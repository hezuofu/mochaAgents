package io.sketch.mochaagents.agents;

import com.smolagents.memory.*;
import com.smolagents.models.*;
import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.memory.FinalAnswerStep;
import io.sketch.mochaagents.memory.TaskStep;
import io.sketch.mochaagents.memory.Timing;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.ToolCall;
import io.sketch.mochaagents.tools.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工具调用代理 - 使用 JSON 格式调用工具
 */
public class ToolCallingAgent extends MultiStepAgent implements AutoCloseable {
    
    private ToolCallingAgent(Builder builder) {
        super(builder);
    }
    
    @Override
    public String initializeSystemPrompt() {
        return promptTemplates != null 
            ? promptTemplates.systemPrompt()
            : "You are a helpful assistant that calls tools to solve problems. " +
              "Use JSON format to call tools when needed.";
    }
    
    @Override
    protected ActionStep step(int stepNumber) {
        Timing timing = Timing.start();
        
        List<ChatMessage> messages = writeMemoryToMessages();
        
        List<Tool> toolList = new ArrayList<>(tools.values());
        ChatMessage modelOutput = model.generate(messages, toolList);
        
        List<ToolCall> toolCalls = modelOutput.toolCalls() != null
            ? modelOutput.toolCalls() 
            : List.of();
        
        StringBuilder observations = new StringBuilder();
        List<ToolOutput> outputs = new ArrayList<>();
        
        for (ToolCall call : toolCalls) {
            Tool tool = tools.get(call.function().name());
            if (tool != null) {
                Object result = tool.call(call.function().arguments());
                ToolOutput output = ToolOutput.from(call, result);
                outputs.add(output);
                observations.append(output.observation()).append("\n");
            }
        }
        
        timing = timing.end();
        
        return ActionStep.builder()
            .stepNumber(stepNumber)
            .timing(timing)
            .modelInputMessages(messages)
            .modelOutputMessage(modelOutput)
            .modelOutput(modelOutput.getTextContent())
            .toolCalls(toolCalls)
            .observations(observations.toString().trim())
            .tokenUsage(modelOutput.tokenUsage())
            .isFinalAnswer(isFinalAnswer(toolCalls))
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
                    System.out.println("Step " + step.stepNumber() + ": " + step.toolCalls());
                } else if (event instanceof ActionOutput output) {
                    System.out.println("Final Answer: " + output.output());
                }
            });
            return null;
        }
        return super.run(task, false);
    }
    
    private boolean isFinalAnswer(List<ToolCall> toolCalls) {
        return toolCalls.stream()
            .anyMatch(call -> call.function().name().equals("final_answer"));
    }
    
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
    
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {}
}