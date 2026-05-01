package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.agents.StreamEvent;
import com.smolagents.models.*;
import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.TokenUsage;
import io.sketch.mochaagents.models.ToolCall;

import java.util.ArrayList;
import java.util.List;

public record ActionStep(
    int stepNumber,
    Timing timing,
    List<ChatMessage> modelInputMessages,
    List<ToolCall> toolCalls,
    AgentError error,
    ChatMessage modelOutputMessage,
    String modelOutput,
    String codeAction,
    String observations,
    Object actionOutput,
    TokenUsage tokenUsage,
    boolean isFinalAnswer
) implements MemoryStep, StreamEvent {
    
    @Override
    public List<ChatMessage> toMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        
        if (modelOutput != null) {
            messages.add(ChatMessage.text(MessageRole.ASSISTANT, modelOutput));
        }
        
        if (toolCalls != null && !toolCalls.isEmpty()) {
            messages.add(ChatMessage.toolCall(toolCalls));
        }
        
        if (observations != null) {
            messages.add(ChatMessage.text(MessageRole.TOOL_RESPONSE, 
                "Observation:\n" + observations));
        }
        
        if (error != null) {
            messages.add(ChatMessage.text(MessageRole.TOOL_RESPONSE, 
                "Error:\n" + error.message()));
        }
        
        return messages;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int stepNumber;
        private Timing timing;
        private List<ChatMessage> modelInputMessages;
        private List<ToolCall> toolCalls;
        private AgentError error;
        private ChatMessage modelOutputMessage;
        private String modelOutput;
        private String codeAction;
        private String observations;
        private Object actionOutput;
        private TokenUsage tokenUsage;
        private boolean isFinalAnswer;
        
        public Builder stepNumber(int stepNumber) {
            this.stepNumber = stepNumber;
            return this;
        }
        
        public Builder timing(Timing timing) {
            this.timing = timing;
            return this;
        }
        
        public Builder modelInputMessages(List<ChatMessage> modelInputMessages) {
            this.modelInputMessages = modelInputMessages;
            return this;
        }
        
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        public Builder error(AgentError error) {
            this.error = error;
            return this;
        }
        
        public Builder modelOutputMessage(ChatMessage modelOutputMessage) {
            this.modelOutputMessage = modelOutputMessage;
            return this;
        }
        
        public Builder modelOutput(String modelOutput) {
            this.modelOutput = modelOutput;
            return this;
        }
        
        public Builder codeAction(String codeAction) {
            this.codeAction = codeAction;
            return this;
        }
        
        public Builder observations(String observations) {
            this.observations = observations;
            return this;
        }
        
        public Builder actionOutput(Object actionOutput) {
            this.actionOutput = actionOutput;
            return this;
        }
        
        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }
        
        public Builder isFinalAnswer(boolean isFinalAnswer) {
            this.isFinalAnswer = isFinalAnswer;
            return this;
        }
        
        public ActionStep build() {
            return new ActionStep(stepNumber, timing, modelInputMessages, toolCalls, 
                error, modelOutputMessage, modelOutput, codeAction, observations, 
                actionOutput, tokenUsage, isFinalAnswer);
        }
    }
}