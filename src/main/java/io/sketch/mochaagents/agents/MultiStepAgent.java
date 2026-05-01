package io.sketch.mochaagents.agents;

import com.smolagents.memory.*;
import com.smolagents.models.*;
import io.sketch.mochaagents.memory.*;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.monitoring.LogLevel;
import io.sketch.mochaagents.monitoring.Monitor;
import io.sketch.mochaagents.tools.Tool;

import java.util.*;
import java.util.stream.Stream;

/**
 * 多步骤代理抽象基类
 */
public abstract class MultiStepAgent {
    
    protected final Model model;
    protected final Map<String, Tool> tools;
    protected final AgentMemory memory;
    protected final PromptTemplates promptTemplates;
    protected final Map<String, MultiStepAgent> managedAgents;
    
    protected int maxSteps = 20;
    protected Integer planningInterval;
    protected String name;
    protected String description;
    protected boolean streamOutputs = false;
    
    protected final AgentLogger logger;
    protected final Monitor monitor;
    
    protected MultiStepAgent(Builder<?> builder) {
        this.model = builder.model;
        this.tools = builder.tools;
        this.promptTemplates = builder.promptTemplates;
        this.maxSteps = builder.maxSteps;
        this.planningInterval = builder.planningInterval;
        this.name = builder.name;
        this.description = builder.description;
        this.memory = new AgentMemory();
        this.logger = builder.logger != null ? builder.logger : new AgentLogger(LogLevel.INFO);
        this.monitor = new Monitor(model, logger);
        this.managedAgents = setupManagedAgents(builder.managedAgents);
        validateToolsAndManagedAgents();
    }
    
    protected Map<String, MultiStepAgent> setupManagedAgents(List<MultiStepAgent> managedAgents) {
        Map<String, MultiStepAgent> result = new LinkedHashMap<>();
        if (managedAgents != null && !managedAgents.isEmpty()) {
            for (MultiStepAgent agent : managedAgents) {
                if (agent.name == null || agent.name.isEmpty() || 
                    agent.description == null || agent.description.isEmpty()) {
                    throw new IllegalArgumentException("All managed agents need both a name and a description!");
                }
                result.put(agent.name, agent);
            }
        }
        return Collections.unmodifiableMap(result);
    }
    
    protected void validateToolsAndManagedAgents() {
        Set<String> names = new HashSet<>();
        
        for (Tool tool : tools.values()) {
            names.add(tool.getName());
        }
        
        for (String agentName : managedAgents.keySet()) {
            if (names.contains(agentName)) {
                throw new IllegalArgumentException(
                    "Duplicate name found: '" + agentName + "' exists in both tools and managed agents."
                );
            }
            names.add(agentName);
        }
        
        if (name != null && names.contains(name)) {
            throw new IllegalArgumentException(
                "Agent name '" + name + "' conflicts with a tool or managed agent name."
            );
        }
    }
    
    public Map<String, MultiStepAgent> getManagedAgents() {
        return managedAgents;
    }
    
    protected void initializeMemory() {
        memory.setSystemPrompt(initializeSystemPrompt());
    }
    
    /**
     * 初始化系统提示词
     */
    public abstract String initializeSystemPrompt();
    
    /**
     * 运行 Agent 完成任务
     */
    public Object run(String task) {
        return run(task, false);
    }
    
    /**
     * 运行 Agent，支持流式输出
     */
    public Object run(String task, boolean stream) {
        memory.reset();
        memory.addStep(new TaskStep(task));
        
        for (int step = 1; step <= maxSteps; step++) {
            ActionStep actionStep = step(step);
            memory.addStep(actionStep);
            
            if (actionStep.isFinalAnswer()) {
                memory.addStep(new FinalAnswerStep(actionStep.actionOutput()));
                return actionStep.actionOutput();
            }
            
            if (actionStep.error() != null && !actionStep.error().isRecoverable()) {
                return actionStep.error().message();
            }
            
            if (planningInterval != null && step % planningInterval == 0) {
                PlanningStep planningStep = generatePlanningStep(task, false, step);
                memory.addStep(planningStep);
            }
        }
        
        memory.addStep(new FinalAnswerStep("Reached max steps without finding a solution."));
        return "Reached max steps without finding a solution.";
    }
    
    /**
     * 流式执行
     */
    public Stream<StreamEvent> runStream(String task) {
        return runStream(task, maxSteps);
    }
    
    protected abstract Stream<StreamEvent> runStream(String task, int maxSteps);
    
    /**
     * 执行单个步骤
     */
    protected abstract ActionStep step(int stepNumber);
    
    /**
     * 将记忆转换为消息格式
     */
    protected List<ChatMessage> writeMemoryToMessages() {
        return memory.toMessages();
    }
    
    /**
     * 生成规划步骤
     */
    protected PlanningStep generatePlanningStep(String task, boolean isFirstStep, int step) {
        List<ChatMessage> messages = writeMemoryToMessages();
        String prompt = isFirstStep 
            ? promptTemplates.planning().initialPlan()
            : promptTemplates.planning().updatePlanPreMessages();
        
        messages.add(ChatMessage.text(MessageRole.USER, prompt.replace("{task}", task)));
        
        ChatMessage output = model.generate(messages);
        String plan = output.getTextContent();
        
        return new PlanningStep(messages, output, plan, Timing.start().end(), output.tokenUsage());
    }
    
    /**
     * 获取当前记忆
     */
    public AgentMemory getMemory() {
        return memory;
    }
    
    /**
     * Builder 模式
     */
    public abstract static class Builder<T extends Builder<T>> {
        protected Model model;
        protected Map<String, Tool> tools = Map.of();
        protected PromptTemplates promptTemplates;
        protected int maxSteps = 20;
        protected Integer planningInterval;
        protected String name;
        protected String description;
        protected AgentLogger logger;
        protected List<MultiStepAgent> managedAgents = new ArrayList<>();
        
        public T model(Model model) {
            this.model = model;
            return self();
        }
        
        public T tools(List<Tool> tools) {
            this.tools = tools.stream()
                .collect(java.util.stream.Collectors.toMap(Tool::getName, t -> t));
            return self();
        }
        
        public T tool(Tool tool) {
            if (this.tools.isEmpty()) {
                this.tools = new HashMap<>();
            } else if (!(this.tools instanceof HashMap)) {
                this.tools = new HashMap<>(this.tools);
            }
            this.tools.put(tool.getName(), tool);
            return self();
        }
        
        public T promptTemplates(PromptTemplates promptTemplates) {
            this.promptTemplates = promptTemplates;
            return self();
        }
        
        public T maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return self();
        }
        
        public T planningInterval(Integer planningInterval) {
            this.planningInterval = planningInterval;
            return self();
        }
        
        public T name(String name) {
            this.name = name;
            return self();
        }
        
        public T description(String description) {
            this.description = description;
            return self();
        }
        
        public T logger(AgentLogger logger) {
            this.logger = logger;
            return self();
        }
        
        public T managedAgents(List<MultiStepAgent> managedAgents) {
            this.managedAgents = new ArrayList<>(managedAgents);
            return self();
        }
        
        public T addManagedAgent(MultiStepAgent agent) {
            this.managedAgents.add(agent);
            return self();
        }
        
        protected abstract T self();
        
        public abstract MultiStepAgent build();
    }
}