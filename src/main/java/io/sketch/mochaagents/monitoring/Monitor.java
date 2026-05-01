package io.sketch.mochaagents.monitoring;

import io.sketch.mochaagents.memory.ActionStep;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.TokenUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控器
 */
public class Monitor {
    
    private final Model trackedModel;
    private final AgentLogger logger;
    private final List<Long> stepDurations = new ArrayList<>();
    private int totalInputTokenCount = 0;
    private int totalOutputTokenCount = 0;
    
    public Monitor(Model model, AgentLogger logger) {
        this.trackedModel = model;
        this.logger = logger;
    }
    
    public TokenUsage getTotalTokenCounts() {
        return new TokenUsage(totalInputTokenCount, totalOutputTokenCount);
    }
    
    public void reset() {
        stepDurations.clear();
        totalInputTokenCount = 0;
        totalOutputTokenCount = 0;
    }
    
    public void updateMetrics(ActionStep stepLog) {
        if (stepLog.timing() != null) {
            stepDurations.add(stepLog.timing().getDurationMs());
        }
        
        StringBuilder consoleOutput = new StringBuilder();
        consoleOutput.append("[Step ").append(stepDurations.size());
        
        if (stepLog.timing() != null) {
            consoleOutput.append(": Duration ").append(String.format("%.2f", stepLog.timing().getDurationMs() / 1000.0)).append(" seconds");
        }
        
        if (stepLog.tokenUsage() != null) {
            totalInputTokenCount += stepLog.tokenUsage().inputTokens();
            totalOutputTokenCount += stepLog.tokenUsage().outputTokens();
            consoleOutput.append("| Input tokens: ").append(totalInputTokenCount)
                        .append(" | Output tokens: ").append(totalOutputTokenCount);
        }
        
        consoleOutput.append("]");
        logger.log(consoleOutput.toString(), LogLevel.INFO);
    }
    
    public AgentLogger getLogger() {
        return logger;
    }
    
    public int getStepCount() {
        return stepDurations.size();
    }
}
