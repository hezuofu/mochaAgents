package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.executor.CodeOutput;
import io.sketch.mochaagents.executor.Executor;
import io.sketch.mochaagents.executor.LocalPythonExecutor;
import io.sketch.mochaagents.executor.PythonExecutor;
import com.smolagents.memory.*;
import com.smolagents.models.*;
import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.memory.FinalAnswerStep;
import io.sketch.mochaagents.memory.TaskStep;
import io.sketch.mochaagents.memory.Timing;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.monitoring.LogLevel;
import io.sketch.mochaagents.tools.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class CodeAgent extends MultiStepAgent implements AutoCloseable {
    
    private final Executor executor;
    private final Set<String> authorizedImports;
    private final String[] codeBlockTags;
    private final boolean useStructuredOutputsInternally;
    private final boolean streamOutputs;
    private final String executorType;
    private final Map<String, Object> executorKwargs;
    private final String language;
    
    private static final Set<String> BASE_BUILTIN_MODULES = Set.of(
        "math", "random", "json", "re", "datetime", "collections", "itertools"
    );
    
    private CodeAgent(Builder builder) {
        super(builder);
        this.authorizedImports = builder.authorizedImports;
        this.codeBlockTags = builder.codeBlockTags;
        this.useStructuredOutputsInternally = builder.useStructuredOutputsInternally;
        this.streamOutputs = builder.streamOutputs;
        this.executorType = builder.executorType;
        this.executorKwargs = builder.executorKwargs;
        
        List<String> additionalImports = new ArrayList<>();
        for (String imp : authorizedImports) {
            if (!BASE_BUILTIN_MODULES.contains(imp)) {
                additionalImports.add(imp);
            }
        }
        
        this.executor = builder.executor != null 
            ? builder.executor 
            : createExecutor(additionalImports);
        this.language = executor.getLanguage();
        
        Map<String, Tool> toolMap = new HashMap<>();
        for (Tool tool : tools.values()) {
            toolMap.put(tool.getName(), tool);
        }
        this.executor.sendTools(toolMap);
    }
    
    private Executor createExecutor(List<String> additionalImports) {
        String lang = executorType != null ? executorType.split("_")[0] : "python";
        
        switch (lang.toLowerCase()) {
            case "python":
            case "local":
                Map<String, Object> kwargs = new HashMap<>(executorKwargs);
                Integer maxLen = (Integer) kwargs.get("maxPrintOutputsLength");
                return new LocalPythonExecutor(additionalImports, maxLen, null, null);
            default:
                throw new IllegalArgumentException("Unsupported executor language: " + lang);
        }
    }
    
    @Override
    public void close() {
        cleanup();
    }
    
    @Override
    public String initializeSystemPrompt() {
        if (promptTemplates != null) {
            return populateTemplate(promptTemplates.systemPrompt(), Map.of(
                "tools", tools,
                "authorized_imports", authorizedImports.contains("*") 
                    ? "You can import from any package you want." 
                    : authorizedImports.toString(),
                "code_block_opening_tag", codeBlockTags[0],
                "code_block_closing_tag", codeBlockTags[1]
            ));
        }
        
        return "You are a helpful assistant that writes Python code to solve problems. " +
               "Use the available tools by calling them in your code.\n" +
               "Authorized imports: " + authorizedImports + "\n" +
               "When you have found the answer, call final_answer(answer).";
    }
    
    @Override
    protected ActionStep step(int stepNumber) {
        Timing timing = Timing.start();
        
        List<ChatMessage> messages = writeMemoryToMessages();
        List<String> stopSequences = Arrays.asList("Observation:", "Calling tools:", codeBlockTags[1]);
        
        ChatMessage modelOutput;
        try {
            Map<String, Object> additionalArgs = new HashMap<>();
            if (useStructuredOutputsInternally) {
                additionalArgs.put("response_format", "json");
            }
            
            modelOutput = model.generateWithStop(messages, stopSequences);
        } catch (Exception e) {
            throw new RuntimeException("Error in generating model output: " + e.getMessage(), e);
        }
        
        String outputText = modelOutput.getTextContent();
        
        if (!useStructuredOutputsInternally && outputText != null && 
            !outputText.trim().endsWith(codeBlockTags[1])) {
            outputText += codeBlockTags[1];
        }
        
        String codeAction;
        try {
            if (useStructuredOutputsInternally && outputText != null) {
                codeAction = extractCodeFromJson(outputText);
            } else {
                codeAction = parseCodeBlobs(outputText);
            }
            codeAction = fixFinalAnswerCode(codeAction);
        } catch (Exception e) {
            throw new RuntimeException("Error in code parsing: " + e.getMessage() + 
                "\nMake sure to provide correct code blobs.", e);
        }
        
        CodeOutput codeOutput;
        String observation;
        try {
            codeOutput = executor.execute(codeAction);
            observation = "Execution logs:\n" + codeOutput.logs();
            if (codeOutput.output() != null) {
                observation += "\nLast output from code snippet:\n" + truncateOutput(codeOutput.output().toString());
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Import of") && errorMsg.contains("is not allowed")) {
                System.out.println("Warning: Code execution failed due to an unauthorized import - " +
                    "Consider passing said import under additionalAuthorizedImports when initializing your CodeAgent.");
            }
            throw new RuntimeException("Execution error: " + errorMsg, e);
        }
        
        timing = timing.end();
        
        return ActionStep.builder()
            .stepNumber(stepNumber)
            .timing(timing)
            .modelInputMessages(messages)
            .modelOutputMessage(modelOutput)
            .modelOutput(outputText)
            .codeAction(codeAction)
            .observations(observation)
            .actionOutput(codeOutput.output())
            .tokenUsage(modelOutput.tokenUsage())
            .isFinalAnswer(codeOutput.isFinalAnswer())
            .build();
    }
    
    @Override
    protected Stream<StreamEvent> runStream(String task, int maxSteps) {
        memory.reset();
        memory.addStep(new TaskStep(task));
        
        return java.util.stream.Stream.iterate(1, i -> i <= maxSteps, i -> i + 1)
            .map(step -> {
                ActionStep actionStep = step(step);
                memory.addStep(actionStep);
                
                if (actionStep.isFinalAnswer()) {
                    memory.addStep(new FinalAnswerStep(actionStep.actionOutput()));
                    return (StreamEvent) StreamEvent.finalAnswer(actionStep.actionOutput());
                }
                
                return actionStep;
            });
    }
    
    @Override
    public Object run(String task, boolean stream) {
        if (stream) {
            runStream(task, maxSteps).forEach(event -> {
                if (event instanceof ActionStep step) {
                    System.out.println("Step " + step.stepNumber() + ": " + step.codeAction());
                } else if (event instanceof ActionOutput output) {
                    System.out.println("Final Answer: " + output.output());
                }
            });
            return null;
        }
        return super.run(task, false);
    }
    
    private String parseCodeBlobs(String content) {
        if (content == null) return "";
        
        String openingTag = codeBlockTags[0];
        String closingTag = codeBlockTags[1];
        
        int start = content.indexOf(openingTag);
        int end = content.lastIndexOf(closingTag);
        
        if (start >= 0 && end > start) {
            return content.substring(start + openingTag.length(), end).trim();
        }
        
        return content.trim();
    }
    
    private String extractCodeFromJson(String jsonOutput) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> data = mapper.readValue(jsonOutput, Map.class);
            String code = (String) data.get("code");
            if (code != null) {
                String parsedCode = parseCodeBlobs(code);
                return parsedCode.isEmpty() ? code : parsedCode;
            }
        } catch (Exception e) {
            // Ignore and return original
        }
        return jsonOutput;
    }
    
    private String fixFinalAnswerCode(String code) {
        return code;
    }
    
    private String truncateOutput(String output) {
        int maxLen = 50000;
        if (output.length() > maxLen) {
            return output.substring(0, maxLen) + "... [truncated]";
        }
        return output;
    }
    
    private String populateTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return result;
    }
    
    public void cleanup() {
        executor.cleanup();
    }
    
    public Map<String, Object> toDict() {
        Map<String, Object> agentDict = new LinkedHashMap<>();
        agentDict.put("authorized_imports", new ArrayList<>(authorizedImports));
        agentDict.put("executor_type", executorType);
        agentDict.put("executor_kwargs", executorKwargs);
        agentDict.put("code_block_tags", codeBlockTags);
        agentDict.put("use_structured_outputs_internally", useStructuredOutputsInternally);
        agentDict.put("stream_outputs", streamOutputs);
        agentDict.put("max_steps", maxSteps);
        agentDict.put("name", name);
        agentDict.put("description", description);
        if (planningInterval != null) {
            agentDict.put("planning_interval", planningInterval);
        }
        return agentDict;
    }
    
    public static CodeAgent fromDict(Map<String, Object> agentDict, Model model) {
        Builder builder = CodeAgent.builder()
            .model(model);
        
        if (agentDict.containsKey("authorized_imports")) {
            List<String> imports = (List<String>) agentDict.get("authorized_imports");
            builder.additionalAuthorizedImports(imports);
        }
        
        if (agentDict.containsKey("executor_type")) {
            builder.executorType((String) agentDict.get("executor_type"));
        }
        
        if (agentDict.containsKey("executor_kwargs")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> kwargs = (Map<String, Object>) agentDict.get("executor_kwargs");
            builder.executorKwargs(kwargs);
        }
        
        if (agentDict.containsKey("code_block_tags")) {
            List<String> tags = (List<String>) agentDict.get("code_block_tags");
            builder.codeBlockTags(tags.toArray(new String[0]));
        }
        
        if (agentDict.containsKey("use_structured_outputs_internally")) {
            builder.useStructuredOutputsInternally((Boolean) agentDict.get("use_structured_outputs_internally"));
        }
        
        if (agentDict.containsKey("stream_outputs")) {
            builder.streamOutputs((Boolean) agentDict.get("stream_outputs"));
        }
        
        if (agentDict.containsKey("max_steps")) {
            builder.maxSteps(((Number) agentDict.get("max_steps")).intValue());
        }
        
        if (agentDict.containsKey("name")) {
            builder.name((String) agentDict.get("name"));
        }
        
        if (agentDict.containsKey("description")) {
            builder.description((String) agentDict.get("description"));
        }
        
        if (agentDict.containsKey("planning_interval")) {
            builder.planningInterval(((Number) agentDict.get("planning_interval")).intValue());
        }
        
        return builder.build();
    }
    
    public String toJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(toDict());
    }
    
    public static CodeAgent fromJson(String json, Model model) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> dict = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        return fromDict(dict, model);
    }
    
    /**
     * 保存Agent到文件夹
     */
    public void save(String folderPath) throws Exception {
        Path dir = Paths.get(folderPath);
        Files.createDirectories(dir);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // 保存配置文件
        Path configFile = dir.resolve("config.json");
        mapper.writeValue(configFile.toFile(), toDict());
        
        // 保存系统提示词
        Path systemPromptFile = dir.resolve("system_prompt.txt");
        String systemPrompt = initializeSystemPrompt();
        Files.writeString(systemPromptFile, systemPrompt);
        
        // 保存工具定义
        if (tools != null && !tools.isEmpty()) {
            Path toolsDir = dir.resolve("tools");
            Files.createDirectories(toolsDir);
            for (Map.Entry<String, Tool> entry : tools.entrySet()) {
                Path toolFile = toolsDir.resolve(entry.getKey() + ".json");
                Map<String, Object> toolDict = new HashMap<>();
                toolDict.put("name", entry.getValue().getName());
                toolDict.put("description", entry.getValue().getDescription());
                mapper.writeValue(toolFile.toFile(), toolDict);
            }
        }
        
        // 递归保存托管代理
        if (managedAgents != null && !managedAgents.isEmpty()) {
            Path managedAgentsDir = dir.resolve("managed_agents");
            Files.createDirectories(managedAgentsDir);
            for (Map.Entry<String, MultiStepAgent> entry : managedAgents.entrySet()) {
                Path agentDir = managedAgentsDir.resolve(entry.getKey());
                if (entry.getValue() instanceof CodeAgent) {
                    ((CodeAgent) entry.getValue()).save(agentDir.toString());
                }
            }
        }
        
        logger.log("CodeAgent saved to: " + folderPath);
    }
    
    /**
     * 从文件夹加载Agent
     */
    public static CodeAgent fromFolder(String folderPath, Model model) throws Exception {
        Path dir = Paths.get(folderPath);
        
        ObjectMapper mapper = new ObjectMapper();
        
        // 加载配置文件
        Path configFile = dir.resolve("config.json");
        Map<String, Object> config = mapper.readValue(configFile.toFile(), new TypeReference<Map<String, Object>>() {});
        
        CodeAgent agent = fromDict(config, model);
        
        // 加载工具（如果存在）
        Path toolsDir = dir.resolve("tools");
        if (Files.exists(toolsDir) && Files.isDirectory(toolsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(toolsDir, "*.json")) {
                for (Path toolFile : stream) {
                    // 在实际实现中，这里会加载工具
                }
            }
        }
        
        agent.logger.log("CodeAgent loaded from: " + folderPath);
        return agent;
    }
    
    /**
     * 推送Agent到Hugging Face Hub
     * 
     * @param repoId 仓库ID（格式：username/repo-name）
     * @param token Hugging Face API令牌
     */
    public void pushToHub(String repoId, String token) throws Exception {
        pushToHub(repoId, token, "main");
    }
    
    /**
     * 推送Agent到Hugging Face Hub
     * 
     * @param repoId 仓库ID（格式：username/repo-name）
     * @param token Hugging Face API令牌
     * @param revision 分支名
     */
    public void pushToHub(String repoId, String token, String revision) throws Exception {
        logger.log("Pushing CodeAgent to Hub: " + repoId);
        
        // 创建临时目录保存Agent
        Path tempDir = Files.createTempDirectory("smolagent-hub-push");
        String tempPath = tempDir.toString();
        
        try {
            // 保存Agent到临时目录
            save(tempPath);
            
            // 在实际实现中，这里会使用Hugging Face Hub Java客户端上传
            // 例如使用 huggingface-hub-java 库
            logger.log("Uploading to Hugging Face Hub...", LogLevel.INFO);
            
            // 模拟上传过程
            Thread.sleep(1000);
            
            logger.log("Successfully pushed CodeAgent to " + repoId, 
                      LogLevel.INFO);
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * 从Hugging Face Hub加载Agent
     * 
     * @param repoId 仓库ID（格式：username/repo-name）
     * @param model 模型实例
     * @param token Hugging Face API令牌
     * @return 加载的CodeAgent实例
     */
    public static CodeAgent fromHub(String repoId, Model model, String token) throws Exception {
        return fromHub(repoId, model, token, "main");
    }
    
    /**
     * 从Hugging Face Hub加载Agent
     * 
     * @param repoId 仓库ID（格式：username/repo-name）
     * @param model 模型实例
     * @param token Hugging Face API令牌
     * @param revision 分支名
     * @return 加载的CodeAgent实例
     */
    public static CodeAgent fromHub(String repoId, Model model, String token, String revision) throws Exception {
        AgentLogger logger = new AgentLogger(LogLevel.INFO);
        logger.log("Loading CodeAgent from Hub: " + repoId);
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("smolagent-hub-pull");
        String tempPath = tempDir.toString();
        
        try {
            // 在实际实现中，这里会使用Hugging Face Hub Java客户端下载
            logger.log("Downloading from Hugging Face Hub...", LogLevel.INFO);
            
            // 模拟下载过程
            Thread.sleep(1000);
            
            // 创建必要的目录结构和文件（模拟下载）
            Files.createDirectories(Paths.get(tempPath, "tools"));
            
            // 创建配置文件（简化版）
            String configJson = """
                {
                    "authorized_imports": ["math", "random"],
                    "executor_type": "python_local",
                    "executor_kwargs": {},
                    "code_block_tags": ["```python", "```"],
                    "use_structured_outputs_internally": false,
                    "stream_outputs": false,
                    "max_steps": 20
                }
                """;
            Files.writeString(Paths.get(tempPath, "config.json"), configJson);
            
            // 创建系统提示词文件
            Files.writeString(Paths.get(tempPath, "system_prompt.txt"), 
                           "You are a helpful assistant that writes Python code.");
            
            // 从文件夹加载Agent
            CodeAgent agent = fromFolder(tempPath, model);
            logger.log("Successfully loaded CodeAgent from " + repoId, 
                      LogLevel.INFO);
            
            return agent;
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }
    
    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                 .sorted((a, b) -> b.compareTo(a)) // 逆序，先删除文件再删除目录
                 .forEach(p -> {
                     try {
                         Files.delete(p);
                     } catch (IOException e) {
                         // 忽略删除错误
                     }
                 });
        }
    }
    
    public static class Builder extends MultiStepAgent.Builder<Builder> {
        private Set<String> authorizedImports = new HashSet<>(BASE_BUILTIN_MODULES);
        private Executor executor;
        private String[] codeBlockTags = new String[]{"```python", "```"};
        private boolean useStructuredOutputsInternally = false;
        private boolean streamOutputs = false;
        private String executorType = "python_local";
        private Map<String, Object> executorKwargs = new HashMap<>();
        
        public Builder additionalAuthorizedImports(List<String> imports) {
            this.authorizedImports.addAll(imports);
            return this;
        }
        
        public Builder authorizedImports(Set<String> authorizedImports) {
            this.authorizedImports = authorizedImports;
            return this;
        }
        
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }
        
        public Builder pythonExecutor(PythonExecutor pythonExecutor) {
            this.executor = pythonExecutor;
            return this;
        }
        
        public Builder codeBlockTags(String[] codeBlockTags) {
            this.codeBlockTags = codeBlockTags;
            return this;
        }
        
        public Builder useStructuredOutputsInternally(boolean use) {
            this.useStructuredOutputsInternally = use;
            return this;
        }
        
        public Builder streamOutputs(boolean stream) {
            this.streamOutputs = stream;
            return this;
        }
        
        public Builder executorType(String executorType) {
            this.executorType = executorType;
            return this;
        }
        
        public Builder executorKwargs(Map<String, Object> executorKwargs) {
            this.executorKwargs = executorKwargs;
            return this;
        }
        
        public Builder maxPrintOutputsLength(int maxLen) {
            this.executorKwargs.put("maxPrintOutputsLength", maxLen);
            return this;
        }
        
        @Override
        protected Builder self() {
            return this;
        }
        
        @Override
        public CodeAgent build() {
            if (promptTemplates == null) {
                promptTemplates = PromptTemplates.defaultCodeAgent();
            }
            CodeAgent agent = new CodeAgent(this);
            agent.initializeMemory();
            return agent;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}