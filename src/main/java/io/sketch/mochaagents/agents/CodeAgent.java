package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.executor.CodeOutput;
import io.sketch.mochaagents.executor.Executor;
import io.sketch.mochaagents.executor.LocalPythonExecutor;
import io.sketch.mochaagents.executor.PythonExecutor;
import io.sketch.mochaagents.executor.PythonFinalAnswerNormalizer;
import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.memory.AgentError;
import io.sketch.mochaagents.memory.AgentMemory;
import io.sketch.mochaagents.memory.FinalAnswerStep;
import io.sketch.mochaagents.memory.MemoryStep;
import io.sketch.mochaagents.memory.Timing;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.ChatMessageContent;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.ResponseFormat;
import io.sketch.mochaagents.models.TokenUsage;
import io.sketch.mochaagents.models.ToolCall;
import io.sketch.mochaagents.models.ToolCallFunction;
import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.monitoring.LogLevel;
import io.sketch.mochaagents.tools.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.sketch.mochaagents.hub.HubClient;

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
        
        refreshExecutorBindings();
    }

    private void refreshExecutorBindings() {
        Map<String, Tool> toolMap = new LinkedHashMap<>();
        for (Tool tool : inferenceToolsUnified()) {
            toolMap.put(tool.getName(), tool);
        }
        executor.sendTools(toolMap);
        executor.sendVariables(new LinkedHashMap<>(runState));
    }

    @Override
    protected void onRunStart(AgentRunControls controls, String augmentedTask) {
        refreshExecutorBindings();
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
            LinkedHashMap<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("tools", summarizeToolsCatalog());
            ctx.put("managed_agents", summarizeManagedAgentsCatalog());
            ctx.put("custom_instructions", instructions == null ? "" : instructions);
            ctx.put(
                "authorized_imports",
                authorizedImports.contains("*")
                    ? "You can import from any package you want."
                    : authorizedImports.toString()
            );
            ctx.put("code_block_opening_tag", codeBlockTags[0]);
            ctx.put("code_block_closing_tag", codeBlockTags[1]);
            return promptRendering.render(promptTemplates.systemPrompt(), ctx);
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
            Map<String, Object> extras = new HashMap<>();
            if (useStructuredOutputsInternally) {
                extras.put("response_format", "json");
            }
            modelOutput = model.generate(messages, List.of(), stopSequences, ResponseFormat.text(), extras);
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
            codeAction = PythonFinalAnswerNormalizer.fix(codeAction);
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

        List<ToolCall> interpreterTrace = List.of(
            new ToolCall(
                "call_" + stepNumber,
                ToolCallFunction.of("python_interpreter", Map.of("code", codeAction))
            ));

        return ActionStep.builder()
            .stepNumber(stepNumber)
            .timing(timing)
            .modelInputMessages(messages)
            .modelOutputMessage(modelOutput)
            .modelOutput(outputText)
            .toolCalls(interpreterTrace)
            .codeAction(codeAction)
            .observations(observation)
            .actionOutput(codeOutput.output())
            .tokenUsage(modelOutput.tokenUsage())
            .isFinalAnswer(codeOutput.isFinalAnswer())
            .build();
    }
    
    @Override
    protected Stream<StreamEvent> runStream(String task, int maxSteps) {
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
        if (!stream) {
            return super.run(task);
        }

        String augmentedTask = primeConversation(task, AgentRunControls.DEFAULT);
        AgentExecutionResult result = executeAgentLoop(augmentedTask, AgentRunControls.DEFAULT, actionStep -> {
            System.out.println("Step " + actionStep.stepNumber() + ": " + actionStep.codeAction());
            if (actionStep.isFinalAnswer()) {
                System.out.println("Final Answer: " + actionStep.actionOutput());
            }
        });
        return result.output();
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
    
    private String truncateOutput(String output) {
        int maxLen = 50000;
        if (output.length() > maxLen) {
            return output.substring(0, maxLen) + "... [truncated]";
        }
        return output;
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
     * 推送Agent到Hugging Face Hub。
     *
     * @param repoId 仓库ID（格式：username/repo-name 或 namespace/repo-name）
     * @param token  Hugging Face API令牌
     */
    public void pushToHub(String repoId, String token) throws Exception {
        pushToHub(repoId, token, "main");
    }

    /**
     * 推送Agent到Hugging Face Hub，指定分支。
     *
     * @param repoId   仓库ID（格式：username/repo-name）
     * @param token    Hugging Face API令牌
     * @param revision 分支/标签名
     */
    public void pushToHub(String repoId, String token, String revision) throws Exception {
        logger.log("Pushing CodeAgent to Hub: " + repoId, LogLevel.INFO);

        HubClient hub = new HubClient(token);
        hub.ensureRepo(repoId);

        Path tempDir = Files.createTempDirectory("smolagent-hub-push");
        try {
            save(tempDir.toString());
            hub.uploadDirectory(repoId, revision, tempDir);
            logger.log("Successfully pushed CodeAgent to " + repoId, LogLevel.INFO);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * 从Hugging Face Hub加载Agent。
     *
     * @param repoId 仓库ID（格式：username/repo-name）
     * @param model  模型实例
     * @param token  Hugging Face API令牌
     * @return 加载的CodeAgent实例
     */
    public static CodeAgent fromHub(String repoId, Model model, String token) throws Exception {
        return fromHub(repoId, model, token, "main");
    }

    /**
     * 从Hugging Face Hub加载Agent，指定分支。
     *
     * @param repoId   仓库ID（格式：username/repo-name）
     * @param model    模型实例
     * @param token    Hugging Face API令牌
     * @param revision 分支/标签名
     * @return 加载的CodeAgent实例
     */
    public static CodeAgent fromHub(String repoId, Model model, String token, String revision) throws Exception {
        AgentLogger logger = new AgentLogger(LogLevel.INFO);
        logger.log("Loading CodeAgent from Hub: " + repoId, LogLevel.INFO);

        HubClient hub = new HubClient(token);

        Path tempDir = Files.createTempDirectory("smolagent-hub-pull");
        try {
            hub.downloadAll(repoId, revision, tempDir);
            CodeAgent agent = fromFolder(tempDir.toString(), model);
            logger.log("Successfully loaded CodeAgent from " + repoId, LogLevel.INFO);
            return agent;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                 .sorted((a, b) -> b.compareTo(a))
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