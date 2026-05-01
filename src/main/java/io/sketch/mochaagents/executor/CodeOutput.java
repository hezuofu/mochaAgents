package io.sketch.mochaagents.executor;

public record CodeOutput(
    Object output,
    String logs,
    boolean isFinalAnswer
) {
    public static CodeOutput of(Object output, String logs, boolean isFinalAnswer) {
        return new CodeOutput(output, logs, isFinalAnswer);
    }
    
    public static CodeOutput finalAnswer(Object output) {
        return new CodeOutput(output, "", true);
    }
    
    public static CodeOutput intermediate(Object output, String logs) {
        return new CodeOutput(output, logs, false);
    }
}