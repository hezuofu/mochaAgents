package io.sketch.mochaagents.memory;

/**
 * 时间统计
 */
public record Timing(
    long startTime,
    long endTime
) {
    public Timing(long startTime) {
        this(startTime, 0);
    }
    
    public long getDurationMs() {
        return endTime - startTime;
    }
    
    public static Timing start() {
        return new Timing(System.currentTimeMillis());
    }
    
    public Timing end() {
        return new Timing(startTime, System.currentTimeMillis());
    }
    
    public String formatDuration() {
        long ms = getDurationMs();
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.2fs", ms / 1000.0);
        return String.format("%.2fmin", ms / 60000.0);
    }
}