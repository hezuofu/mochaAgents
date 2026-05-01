package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.memory.AgentError;
import io.sketch.mochaagents.memory.Timing;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.ResponseFormat;
import io.sketch.mochaagents.models.ToolCall;
import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolArgumentValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSON/native tool invocation aligned with Hugging Face smolagents {@code ToolCallingAgent}.
 */
public class ToolCallingAgent extends MultiStepAgent implements AutoCloseable {

    private static final List<String> SMOLTOOLS_STOPS = List.of("Observation:", "Calling tools:");

    private final Map<String, Tool> resolver;

    private ToolCallingAgent(Builder builder) {
        super(builder);
        LinkedHashMap<String, Tool> map = new LinkedHashMap<>();
        for (Tool t : inferenceToolsUnified()) {
            map.put(t.getName(), t);
        }
        resolver = Map.copyOf(map);
    }

    @Override
    public String initializeSystemPrompt() {
        if (promptTemplates == null) {
            return """
                You are a helpful assistant that calls tools whenever they add value.
                Remember to obey the Observation / Calling-tools delimiters.""";
        }
        LinkedHashMap<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("tools", summarizeToolsCatalog());
        ctx.put("managed_agents", summarizeManagedAgentsCatalog());
        ctx.put("custom_instructions", instructions == null ? "" : instructions);
        return promptRendering.render(promptTemplates.systemPrompt(), ctx);
    }

    @Override
    protected ActionStep step(int agentStepNumber) {
        Timing timing = Timing.start();
        List<ChatMessage> history = writeMemoryToMessages();

        ChatMessage raw = model.generate(
            history,
            inferenceToolsUnified(),
            SMOLTOOLS_STOPS,
            ResponseFormat.text(),
            Map.of());
        ChatMessage modelOutput = model.parseToolCalls(raw);

        List<ToolCall> calls =
            modelOutput.toolCalls() == null ? List.of() : modelOutput.toolCalls();

        if (calls.isEmpty()) {
            Timing done = timing.end();
            return ActionStep.builder()
                .stepNumber(agentStepNumber)
                .timing(done)
                .modelInputMessages(history)
                .modelOutputMessage(modelOutput)
                .modelOutput(modelOutput.getTextContent())
                .toolCalls(List.of())
                .observations("")
                .tokenUsage(modelOutput.tokenUsage())
                .error(AgentError.parsingError(
                    "Model returned zero tool_calls. Implement Model.parse_tool_calls(...) or constrain prompting."))
                .build();
        }

        dedupeFinalAnswerSemantics(calls);
        calls = calls.stream()
            .sorted(Comparator.comparing(tc -> tc.id() == null ? "" : tc.id()))
            .collect(Collectors.toList());

        StringBuilder observation = new StringBuilder();
        boolean emittedFinalAnswer = calls.stream().anyMatch(tc -> "final_answer".equals(tc.function().name()));
        Object finalPayload = null;

        if (calls.size() <= 1) {
            for (ToolCall call : calls) {
                ToolCallOutcome one = executeToolCall(call);
                if (!observation.isEmpty()) {
                    observation.append('\n');
                }
                observation.append(one.observationText());
                if (one.finalSubstitution() != null) {
                    finalPayload = one.finalSubstitution();
                }
            }
        } else {
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                List<CompletableFuture<ToolCallOutcome>> futures = new ArrayList<>(calls.size());
                for (ToolCall call : calls) {
                    futures.add(CompletableFuture.supplyAsync(() -> executeToolCall(call), executor));
                }
                for (int i = 0; i < calls.size(); i++) {
                    ToolCallOutcome o = futures.get(i).join();
                    if (!observation.isEmpty()) {
                        observation.append('\n');
                    }
                    observation.append(o.observationText());
                    if (o.finalSubstitution() != null) {
                        finalPayload = o.finalSubstitution();
                    }
                }
            } finally {
                executor.shutdown();
            }
        }

        Timing finished = timing.end();
        return ActionStep.builder()
            .stepNumber(agentStepNumber)
            .timing(finished)
            .modelInputMessages(history)
            .modelOutputMessage(modelOutput)
            .modelOutput(modelOutput.getTextContent())
            .toolCalls(calls)
            .observations(observation.toString())
            .tokenUsage(modelOutput.tokenUsage())
            .isFinalAnswer(emittedFinalAnswer)
            .actionOutput(finalPayload)
            .build();
    }

    private record ToolCallOutcome(String observationText, Object finalSubstitution) {}

    private ToolCallOutcome executeToolCall(ToolCall call) {
        String name = call.function().name();
        Tool executable = resolver.get(name);
        if (executable == null) {
            throw new IllegalStateException(
                "Unknown tool '%s'. Allowed names: %s".formatted(name, resolver.keySet()));
        }
        Map<String, Object> args = hydrateArguments(call.function().arguments());
        ToolArgumentValidator.validateOrThrow(executable, args);
        Object output = executable.call(args);
        String textual = stringify(output);
        Object finalPart = "final_answer".equals(name) ? substituteState(output) : null;
        return new ToolCallOutcome(textual, finalPart);
    }

    private void dedupeFinalAnswerSemantics(List<ToolCall> calls) {
        long finals = calls.stream().filter(tc -> "final_answer".equals(tc.function().name())).count();
        if (finals > 1) {
            throw new IllegalStateException("Only one final_answer tool invocation is permitted.");
        }
        if (finals == 1 && calls.size() > 1) {
            throw new IllegalStateException("Cannot mix final_answer with other parallel tool_calls.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> hydrateArguments(Map<String, Object> args) {
        if (args == null) {
            return Map.of();
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            parsed.put(entry.getKey(), hydrateValue(entry.getValue()));
        }
        return parsed;
    }

    private Object hydrateValue(Object raw) {
        if (raw instanceof String token && runState.containsKey(token)) {
            return runState.get(token);
        }
        if (raw instanceof Map<?, ?> nested) {
            Map<String, Object> child = new LinkedHashMap<>();
            for (Map.Entry<?, ?> slice : nested.entrySet()) {
                child.put(String.valueOf(slice.getKey()), hydrateValue(slice.getValue()));
            }
            return child;
        }
        return raw;
    }

    private Object substituteState(Object payload) {
        if (payload instanceof String token && runState.containsKey(token)) {
            return runState.get(token);
        }
        return payload;
    }

    private String stringify(Object value) {
        return value == null ? "" : Objects.toString(value).trim();
    }

    @Override
    protected Stream<StreamEvent> runStream(String task, int maxSteps) {
        return Stream.empty();
    }

    /** Streaming mode focuses on deterministic logging rather than websocket-style deltas for now. */
    @Override
    public Object run(String task, boolean stream) {
        if (!stream) {
            return super.run(task);
        }

        String augmentedTask = primeConversation(task, AgentRunControls.DEFAULT);
        AgentExecutionResult result = executeAgentLoop(augmentedTask, AgentRunControls.DEFAULT, record -> {
            if (record.error() != null) {
                System.out.printf("[tool-agent] parsing issue: %s%n", record.error().message());
            }
            System.out.printf("[tool-agent][step-%d] observations=%s%n", record.stepNumber(), record.observations());
        });
        System.out.printf("[tool-agent][final]= %s%n", result.output());
        return result.output();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {}

    public static class Builder extends MultiStepAgent.Builder<Builder> {

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ToolCallingAgent build() {
            if (promptTemplates == null) {
                promptTemplates = PromptTemplates.defaultToolCallingAgent();
            }
            ToolCallingAgent agent = new ToolCallingAgent(this);
            agent.initializeMemory();
            return agent;
        }
    }
}
