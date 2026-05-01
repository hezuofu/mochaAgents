package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.memory.AgentMemory;
import io.sketch.mochaagents.memory.FinalAnswerStep;
import io.sketch.mochaagents.memory.MemoryStep;
import io.sketch.mochaagents.memory.PlanningStep;
import io.sketch.mochaagents.memory.TaskStep;
import io.sketch.mochaagents.memory.Timing;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.ResponseFormat;
import io.sketch.mochaagents.models.TokenUsage;
import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.monitoring.LogLevel;
import io.sketch.mochaagents.monitoring.Monitor;
import io.sketch.mochaagents.tool_validation.ToolValidation;
import io.sketch.mochaagents.tools.ManagedAgentTool;
import io.sketch.mochaagents.tools.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract multi-step orchestration aligned with Hugging Face smolagents {@code MultiStepAgent}
 * (planning cadence before actions, summary-mode planning prompts, terminal {@code provideFinalAnswer}.
 */
public abstract class MultiStepAgent {

    protected final Model model;
    protected final Map<String, Tool> tools;
    protected final AgentMemory memory;
    protected final PromptTemplates promptTemplates;
    protected final Map<String, MultiStepAgent> managedAgents;
    protected final PromptRendering promptRendering;
    protected final List<Tool> inferenceToolsUnified;

    /** Live key/value bag updated per run ({@code reset}) and delegated tools (parity with Python {@code self.state}). */
    protected final Map<String, Object> runState = new ConcurrentHashMap<>();

    /** Optional injected instructions interpolated into prompts as {@code custom_instructions}. */
    protected final String instructions;

    protected int maxSteps = 20;
    protected Integer planningInterval;
    protected String name;
    protected String description;
    protected boolean streamOutputs = false;

    protected final AgentLogger logger;
    protected final Monitor monitor;

    /** Cooperative interrupt (cleared each {@link #primeConversation}); honoured between steps. */
    protected volatile boolean interruptRequested;

    protected final List<FinalAnswerCheck> finalAnswerChecks;
    protected final List<Consumer<ActionStep>> stepCallbacks;

    protected MultiStepAgent(Builder<?> builder) {
        this.model = Objects.requireNonNull(builder.model);
        this.promptTemplates = builder.promptTemplates;
        this.maxSteps = builder.maxSteps;
        this.planningInterval = builder.planningInterval;
        this.name = builder.name;
        this.description = builder.description;
        this.promptRendering = builder.promptRendering != null ? builder.promptRendering : PromptRendering.defaultRenderer();
        this.instructions = builder.instructions;
        this.memory = new AgentMemory();
        this.logger = builder.logger != null ? builder.logger : new AgentLogger(LogLevel.INFO);
        this.monitor = new Monitor(model, logger);
        LinkedHashMap<String, Tool> toolCopy = builder.tools != null
            ? new LinkedHashMap<>(builder.tools)
            : new LinkedHashMap<>();
        this.tools = Collections.unmodifiableMap(toolCopy);
        this.managedAgents = setupManagedAgents(builder.managedAgents);
        validateToolsAndManagedAgents();

        List<Tool> combined = new ArrayList<>(this.tools.values());
        for (MultiStepAgent ag : managedAgents.values()) {
            combined.add(new ManagedAgentTool(ag, promptRendering));
        }
        this.inferenceToolsUnified = List.copyOf(combined);
        this.finalAnswerChecks = List.copyOf(builder.finalAnswerChecks);
        this.stepCallbacks = List.copyOf(builder.stepCallbacks);
    }

    /** Request cooperative abort; the current run stops before the next action step. */
    public final void interrupt() {
        interruptRequested = true;
    }

    /** Tools passed to backends that expose first-class JSON tool calls (regular tools + managed agents). */
    protected List<Tool> inferenceToolsUnified() {
        return inferenceToolsUnified;
    }

    protected Map<String, MultiStepAgent> setupManagedAgents(List<MultiStepAgent> managedAgents) {
        LinkedHashMap<String, MultiStepAgent> result = new LinkedHashMap<>();
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
        for (Tool tool : tools.values()) {
            ToolValidation.validateToolOrThrow(tool);
        }
        var names = new java.util.HashSet<>();
        for (Tool tool : tools.values()) {
            names.add(tool.getName());
        }
        for (String agentName : managedAgents.keySet()) {
            if (names.contains(agentName)) {
                throw new IllegalArgumentException(
                    "Duplicate name found: '" + agentName + "' exists in both tools and managed agents.");
            }
            names.add(agentName);
        }
        if (name != null && names.contains(name)) {
            throw new IllegalArgumentException(
                "Agent name '" + name + "' conflicts with a tool or managed agent name.");
        }
    }

    public Map<String, MultiStepAgent> getManagedAgents() {
        return managedAgents;
    }

    protected void initializeMemory() {
        memory.setSystemPrompt(initializeSystemPrompt());
    }

    public abstract String initializeSystemPrompt();

    public Object run(String task) {
        return run(task, AgentRunControls.DEFAULT);
    }

    public Object run(String task, AgentRunControls controls) {
        return runReturningDetails(task, controls).output();
    }

    /**
     * Full run artifact (steps snapshot, interrupt flag), matching smolagents {@code return_full_result}.
     */
    public AgentExecutionResult runReturningDetails(String rawTask, AgentRunControls controls) {
        Objects.requireNonNull(rawTask);
        String augmented = primeConversation(rawTask, controls);
        return executeAgentLoop(augmented, controls, null);
    }

    /** Backwards-compatible toggle; subclasses override streaming behaviour via {@link #consumeStream}. */
    public Object run(String task, boolean stream) {
        if (stream) {
            return consumeStream(task);
        }
        return run(task, AgentRunControls.DEFAULT);
    }

    /** Hook invoked after constructing the augmented task ({@link #augmentTaskWithAdditionalArgs}); override for executor binding. */
    protected void onRunStart(AgentRunControls controls, String augmentedTask) {}

    protected Object consumeStream(String task) {
        throw new UnsupportedOperationException("Streaming is not wired for this agent type.");
    }

    /**
     * Core step loop shared by {@link #runReturningDetails} and streaming-style runners in subclasses.
     *
     * @param stepTracer optional per-action logging hook (called after metrics, with callbacks).
     */
    protected AgentExecutionResult executeAgentLoop(
        String augmentedTask,
        AgentRunControls controls,
        Consumer<ActionStep> stepTracer
    ) {
        int agentStepNumber = 1;
        int cap = controls.effectiveMaxSteps(maxSteps);

        while (agentStepNumber <= cap) {

            if (interruptRequested) {
                interruptRequested = false;
                return AgentExecutionResult.interrupted(snapshotMemorySteps(), snapshotRunState());
            }

            if (shouldRunPlanningBeforeStep(agentStepNumber)) {
                boolean firstPlanning = memory.getSteps().size() == 1;
                PlanningStep planning =
                    generatePlanningStepSmolagents(augmentedTask, firstPlanning, agentStepNumber);
                memory.addStep(planning);
            }

            ActionStep actionStep = step(agentStepNumber);
            memory.addStep(actionStep);

            monitor.updateMetrics(actionStep);
            if (stepTracer != null) {
                stepTracer.accept(actionStep);
            }
            invokeStepCallbacks(actionStep);

            if (actionStep.error() != null && !actionStep.error().isRecoverable()) {
                Object errOut = finalizeNonRecoverableError(actionStep);
                return AgentExecutionResult.of(
                    errOut,
                    false,
                    false,
                    true,
                    snapshotMemorySteps(),
                    snapshotRunState());
            }

            if (actionStep.isFinalAnswer()) {
                Object out = actionStep.actionOutput();
                applyFinalAnswerChecks(out);
                memory.addStep(new FinalAnswerStep(out));
                return AgentExecutionResult.of(
                    out, true, false, false, snapshotMemorySteps(), snapshotRunState());
            }

            agentStepNumber++;
        }

        ChatMessage fa = provideFinalAnswer(augmentedTask);
        Object finalOut = fa.getTextContent();
        applyFinalAnswerChecks(finalOut);
        memory.addStep(new FinalAnswerStep(finalOut));
        return AgentExecutionResult.of(
            finalOut, true, false, false, snapshotMemorySteps(), snapshotRunState());
    }

    private List<MemoryStep> snapshotMemorySteps() {
        return List.copyOf(memory.getSteps());
    }

    private Map<String, Object> snapshotRunState() {
        return Map.copyOf(runState);
    }

    private void invokeStepCallbacks(ActionStep actionStep) {
        for (Consumer<ActionStep> cb : stepCallbacks) {
            cb.accept(actionStep);
        }
    }

    protected void applyFinalAnswerChecks(Object answer) {
        for (FinalAnswerCheck check : finalAnswerChecks) {
            check.validate(answer, memory, this);
        }
    }

    /**
     * Mirrors smolagents run preamble: resets (optional), restores system prompt, hydrates {@link #runState},
     * appends {@link TaskStep}, then {@link #onRunStart}.
     */
    protected String primeConversation(String rawTask, AgentRunControls controls) {
        interruptRequested = false;
        if (controls.reset()) {
            memory.reset();
            runState.clear();
            monitor.reset();
        }
        runState.putAll(controls.safeAdditionalArgs());
        memory.setSystemPrompt(initializeSystemPrompt());
        String augmented = augmentTaskWithAdditionalArgs(rawTask.strip(), controls.safeAdditionalArgs());
        memory.addStep(new TaskStep(augmented, controls.taskImages()));
        onRunStart(controls, augmented);
        return augmented;
    }

    protected Object finalizeNonRecoverableError(ActionStep actionStep) {
        return actionStep.error().message();
    }

    /** smolagents: planning runs before {@code Step n} when interval set. */
    protected boolean shouldRunPlanningBeforeStep(int agentStepNumber) {
        return planningInterval != null
            && (agentStepNumber == 1 || (agentStepNumber - 1) % planningInterval == 0);
    }

    protected String augmentTaskWithAdditionalArgs(String task, Map<String, Object> additionalArgs) {
        if (additionalArgs == null || additionalArgs.isEmpty()) {
            return task;
        }
        return task + "\n\nYou have been provided with these additional arguments, that you can access directly using the keys as variables:\n"
            + additionalArgs;
    }

    protected Map<String, Object> plannerVariables(String augmentedTask, int agentStepNumber) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("task", augmentedTask);
        m.put("tools", summarizeToolsCatalog());
        m.put("managed_agents", summarizeManagedAgentsCatalog());
        m.put("remaining_steps", Integer.valueOf(Math.max(maxSteps - agentStepNumber, 0)));
        m.put("custom_instructions", instructions == null ? "" : instructions);
        return m;
    }

    protected String summarizeToolsCatalog() {
        return tools.values().stream()
            .map(t -> "- " + t.getName() + ": " + t.getDescription())
            .collect(Collectors.joining("\n"));
    }

    protected String summarizeManagedAgentsCatalog() {
        return managedAgents.values().stream()
            .map(a -> "- " + a.name + ": " + a.description)
            .collect(Collectors.joining("\n"));
    }

    protected PlanningStep generatePlanningStepSmolagents(
        String augmentedTask,
        boolean isFirstPlanningCycle,
        int agentStepNumber
    ) {
        Timing timingStart = Timing.start();
        Objects.requireNonNull(promptTemplates);

        Map<String, Object> vars = plannerVariables(augmentedTask, agentStepNumber);
        ChatMessage modelOut;
        List<ChatMessage> modelInputs;

        List<String> planStops = List.of(" "); // parity with Python single-space stop_sequences

        if (isFirstPlanningCycle) {
            String userRendered = promptRendering.render(promptTemplates.planning().initialPlan(), vars);
            modelInputs = List.of(ChatMessage.text(MessageRole.USER, userRendered));
            modelOut = model.generate(modelInputs, List.of(), planStops, ResponseFormat.text(), Map.of());
            String planBody = Objects.requireNonNullElse(modelOut.getTextContent(), "").strip();
            String plan = "Here are the facts I know and the plan of action that I will follow to solve the task:\n```\n"
                + planBody + "\n```";
            Timing done = timingStart.end();
            return new PlanningStep(modelInputs, modelOut, plan, done, modelOut.tokenUsage());
        }

        ChatMessage systemPre = ChatMessage.text(MessageRole.SYSTEM,
            promptRendering.render(promptTemplates.planning().updatePlanPreMessages(), vars));
        ChatMessage userPost = ChatMessage.text(MessageRole.USER,
            promptRendering.render(promptTemplates.planning().updatePlanPostMessages(), vars));

        modelInputs = Stream.concat(Stream.of(systemPre), memory.toMessages(true).stream())
            .collect(Collectors.toCollection(ArrayList::new));
        modelInputs.add(userPost);

        modelOut = model.generate(modelInputs, List.of(), planStops, ResponseFormat.text(), Map.of());
        String updatedBody = Objects.requireNonNullElse(modelOut.getTextContent(), "").strip();
        String planRendered = ("I still need to solve the task I was given:\n```\n" + augmentedTask + "\n```\n\n"
            + "Here are the facts I know and my new/updated plan of action to solve the task:\n```\n"
            + updatedBody + "\n```");
        Timing done = timingStart.end();
        return new PlanningStep(modelInputs, modelOut, planRendered, done, modelOut.tokenUsage());
    }

    /** Final LLM condensation when exhausting {@code max_steps}, mirroring Python {@code provide_final_answer}. */
    protected ChatMessage provideFinalAnswer(String augmentedTask) {
        if (promptTemplates == null || promptTemplates.finalAnswer() == null) {
            return ChatMessage.text(MessageRole.ASSISTANT,
                "Reached max steps without finding an earlier final answer.");
        }
        String preRendered = promptRendering.render(promptTemplates.finalAnswer().preMessages(), Map.of());
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.text(MessageRole.SYSTEM, preRendered));
        List<ChatMessage> snapshot = memory.toMessages();
        if (snapshot.size() > 1) {
            messages.addAll(snapshot.subList(1, snapshot.size()));
        } else if (!snapshot.isEmpty()) {
            messages.addAll(snapshot);
        }

        messages.add(ChatMessage.text(MessageRole.USER,
            promptRendering.render(promptTemplates.finalAnswer().postMessages(),
                Map.of("task", augmentedTask))));
        try {
            return model.generate(messages);
        } catch (Exception e) {
            return ChatMessage.text(MessageRole.ASSISTANT, "Error in generating final LLM output: " + e.getMessage());
        }
    }

    public Stream<StreamEvent> runStream(String task) {
        return runStream(task, maxSteps);
    }

    protected abstract Stream<StreamEvent> runStream(String task, int maxSteps);

    protected abstract ActionStep step(int agentStepNumber);

    protected List<ChatMessage> writeMemoryToMessages() {
        return memory.toMessages(false);
    }

    protected List<ChatMessage> writeMemoryToMessages(boolean summaryMode) {
        return memory.toMessages(summaryMode);
    }

    public AgentMemory getMemory() {
        return memory;
    }

    /** Invoked via {@link ManagedAgentTool}, matching smolagents managed-agent prompting. */
    public Object runAsManagedMember(String taskFragment, Map<String, Object> additionalArgs,
                                     PromptRendering renderingOverride) {
        PromptRendering rr = renderingOverride != null ? renderingOverride : promptRendering;
        Objects.requireNonNull(promptTemplates);

        LinkedHashMap<String, Object> taskVars = new LinkedHashMap<>();
        taskVars.put("name", name != null ? name : "agent");
        taskVars.put("task", taskFragment);

        String fullTask = rr.render(promptTemplates.managedAgent().task(), taskVars);

        if (additionalArgs != null && !additionalArgs.isEmpty()) {
            fullTask += ("\n\nYou have been provided with these additional arguments, that you can access directly using the keys as variables:\n"
                + additionalArgs);
        }

        Object internal = run(fullTask, new AgentRunControls(true, null, Map.of()));

        LinkedHashMap<String, Object> reportVars = new LinkedHashMap<>();
        reportVars.put("name", name != null ? name : "agent");
        reportVars.put("final_answer", Objects.toString(internal, ""));

        return rr.render(promptTemplates.managedAgent().report(), reportVars);
    }

    public String agentNameRequired() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Managed agent invocation requires agent.name");
        }
        return name;
    }

    public String agentDescriptionRequired() {
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Managed agent invocation requires agent.description");
        }
        return description;
    }

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
        protected PromptRendering promptRendering;
        protected String instructions;
        protected final List<FinalAnswerCheck> finalAnswerChecks = new ArrayList<>();
        protected final List<Consumer<ActionStep>> stepCallbacks = new ArrayList<>();

        public T model(Model model) {
            this.model = model;
            return self();
        }

        public T tools(List<Tool> tools) {
            this.tools = tools.stream()
                .collect(Collectors.toMap(Tool::getName, t -> t, (a, b) -> a, LinkedHashMap::new));
            return self();
        }

        public T tool(Tool tool) {
            if (tools.isEmpty()) {
                this.tools = new LinkedHashMap<>();
            } else if (!(this.tools instanceof LinkedHashMap<?, ?>)) {
                this.tools = new LinkedHashMap<>(this.tools);
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

        public T promptRendering(PromptRendering rendering) {
            this.promptRendering = rendering;
            return self();
        }

        public T instructions(String instructions) {
            this.instructions = instructions;
            return self();
        }

        public T addFinalAnswerCheck(FinalAnswerCheck check) {
            this.finalAnswerChecks.add(Objects.requireNonNull(check));
            return self();
        }

        public T addStepCallback(Consumer<ActionStep> callback) {
            this.stepCallbacks.add(Objects.requireNonNull(callback));
            return self();
        }

        protected abstract T self();

        public abstract MultiStepAgent build();
    }
}
