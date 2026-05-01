package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 记忆系统
 */
public class AgentMemory {
    
    private SystemPromptStep systemPrompt;
    private List<MemoryStep> steps = new ArrayList<>();
    
    public AgentMemory() {
        this.systemPrompt = new SystemPromptStep("");
    }
    
    public AgentMemory(String systemPrompt) {
        this.systemPrompt = new SystemPromptStep(systemPrompt);
    }
    
    public void reset() {
        steps.clear();
    }
    
    public void addStep(MemoryStep step) {
        steps.add(step);
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = new SystemPromptStep(systemPrompt);
    }
    
    public List<ChatMessage> toMessages() {
        return toMessages(false);
    }
    
    public List<ChatMessage> toMessages(boolean summaryMode) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.addAll(systemPrompt.toMessages(summaryMode));
        for (MemoryStep step : steps) {
            messages.addAll(step.toMessages(summaryMode));
        }
        return messages;
    }
    
    public List<MemoryStep> getSteps() {
        return new ArrayList<>(steps);
    }
    
    public List<MemoryStep> getSuccinctSteps() {
        return steps.stream()
            .map(step -> {
                if (step instanceof ActionStep actionStep) {
                    return ActionStep.builder()
                        .stepNumber(actionStep.stepNumber())
                        .codeAction(actionStep.codeAction())
                        .observations(actionStep.observations())
                        .isFinalAnswer(actionStep.isFinalAnswer())
                        .build();
                }
                return step;
            })
            .collect(Collectors.toList());
    }
    
    public int getStepCount() {
        return (int) steps.stream()
            .filter(s -> s instanceof ActionStep)
            .count();
    }
    
    public void truncateTo(int maxSteps) {
        List<MemoryStep> filtered = steps.stream()
            .filter(s -> !(s instanceof ActionStep))
            .collect(Collectors.toList());
        
        List<ActionStep> actionSteps = steps.stream()
            .filter(s -> s instanceof ActionStep)
            .map(s -> (ActionStep) s)
            .collect(Collectors.toList());
        
        if (actionSteps.size() > maxSteps) {
            filtered.addAll(actionSteps.subList(actionSteps.size() - maxSteps, actionSteps.size()));
        } else {
            filtered.addAll(actionSteps);
        }
        
        steps = filtered;
    }
}