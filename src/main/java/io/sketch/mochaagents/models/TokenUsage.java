package io.sketch.mochaagents.models;

/**
 * Token 使用统计
 */
public record TokenUsage(
    int inputTokens,
    int outputTokens
) {
    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            inputTokens + other.inputTokens,
            outputTokens + other.outputTokens
        );
    }
    
    public TokenUsage addInput(int tokens) {
        return new TokenUsage(inputTokens + tokens, outputTokens);
    }
    
    public TokenUsage addOutput(int tokens) {
        return new TokenUsage(inputTokens, outputTokens + tokens);
    }
    
    public int total() {
        return inputTokens + outputTokens;
    }
}