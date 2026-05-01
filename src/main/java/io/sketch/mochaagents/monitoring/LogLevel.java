package io.sketch.mochaagents.monitoring;

/**
 * 日志级别枚举
 */
public enum LogLevel {
    OFF(-1),    // 无输出
    ERROR(0),   // 仅错误
    INFO(1),    // 正常输出（默认）
    DEBUG(2);   // 详细输出
    
    private final int level;
    
    LogLevel(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    public static LogLevel fromInt(int level) {
        return switch (level) {
            case -1 -> OFF;
            case 0 -> ERROR;
            case 2 -> DEBUG;
            default -> INFO;
        };
    }
}
