package io.sketch.mochaagents.monitoring;

import io.sketch.mochaagents.agents.MultiStepAgent;
import io.sketch.mochaagents.memory.ActionStep;

/**
 * Agent 日志记录器
 */
public class AgentLogger {
    
    private static final String YELLOW_HEX = "#d4b702";
    private static final String BLUE_HEX = "#1E90FF";
    
    private final LogLevel level;
    
    public AgentLogger() {
        this(LogLevel.INFO);
    }
    
    public AgentLogger(LogLevel level) {
        this.level = level;
    }
    
    public LogLevel getLevel() {
        return level;
    }
    
    public void log(String message) {
        log(message, LogLevel.INFO);
    }
    
    public void log(String message, LogLevel logLevel) {
        if (logLevel.getLevel() <= level.getLevel()) {
            System.out.println(message);
        }
    }
    
    public void logError(String errorMessage) {
        log("\u001B[1;31m" + sanitizeForOutput(errorMessage) + "\u001B[0m", LogLevel.ERROR);
    }
    
    public void logMarkdown(String content, String title) {
        logMarkdown(content, title, LogLevel.INFO);
    }
    
    public void logMarkdown(String content, String title, LogLevel level) {
        if (level.getLevel() <= this.level.getLevel()) {
            if (title != null && !title.isEmpty()) {
                log("\u001B[4m" + title + "\u001B[0m", level);
            }
            log(content, level);
        }
    }
    
    public void logCode(String title, String content) {
        logCode(title, content, LogLevel.INFO);
    }
    
    public void logCode(String title, String content, LogLevel level) {
        if (level.getLevel() <= this.level.getLevel()) {
            System.out.println();
            System.out.println("┌─────────────────────────────────────────────────────────────");
            System.out.println("│ " + title);
            System.out.println("├─────────────────────────────────────────────────────────────");
            System.out.println(content);
            System.out.println("└─────────────────────────────────────────────────────────────");
            System.out.println();
        }
    }
    
    public void logRule(String title) {
        logRule(title, LogLevel.INFO);
    }
    
    public void logRule(String title, LogLevel level) {
        if (level.getLevel() <= this.level.getLevel()) {
            String line = "━".repeat(60);
            System.out.println(line);
            System.out.println("[" + title + "]");
            System.out.println(line);
        }
    }
    
    public void logTask(String content, String subtitle) {
        logTask(content, subtitle, null, LogLevel.INFO);
    }
    
    public void logTask(String content, String subtitle, String title, LogLevel level) {
        if (level.getLevel() <= this.level.getLevel()) {
            System.out.println();
            System.out.println("╔═════════════════════════════════════════════════════════════");
            System.out.println("║ " + (title != null ? "New run - " + title : "New run"));
            System.out.println("╠═════════════════════════════════════════════════════════════");
            System.out.println("║ " + sanitizeForOutput(content));
            if (subtitle != null && !subtitle.isEmpty()) {
                System.out.println("║");
                System.out.println("╚═════════════════════════════════════════════════════════════");
                System.out.println("  " + sanitizeForOutput(subtitle));
            } else {
                System.out.println("╚═════════════════════════════════════════════════════════════");
            }
            System.out.println();
        }
    }
    
    public void logStep(ActionStep step) {
        if (LogLevel.INFO.getLevel() <= level.getLevel()) {
            System.out.println();
            System.out.println("──────────────────────────────────────────────────────────────");
            System.out.println("Step " + step.stepNumber());
            if (step.codeAction() != null) {
                logCode("Code Execution", step.codeAction(), LogLevel.INFO);
            }
            if (step.observations() != null && !step.observations().isEmpty()) {
                System.out.println("Observations: " + step.observations());
            }
            if (step.error() != null) {
                logError("Error: " + step.error().message());
            }
            if (step.tokenUsage() != null) {
                System.out.println("Tokens: " + step.tokenUsage().inputTokens() + " in / " + 
                    step.tokenUsage().outputTokens() + " out");
            }
        }
    }
    
    public void logPlanning(String plan) {
        log("Planning: " + plan, LogLevel.INFO);
    }
    
    public void logFinalAnswer(Object answer) {
        System.out.println();
        System.out.println("╔═════════════════════════════════════════════════════════════");
        System.out.println("║ FINAL ANSWER");
        System.out.println("╠═════════════════════════════════════════════════════════════");
        System.out.println("║ " + sanitizeForOutput(String.valueOf(answer)));
        System.out.println("╚═════════════════════════════════════════════════════════════");
        System.out.println();
    }
    
    public void visualizeAgentTree(MultiStepAgent agent) {
        if (LogLevel.INFO.getLevel() <= level.getLevel()) {
            System.out.println();
            System.out.println("Agent Structure:");
            System.out.println("────────────────");
            printAgentTree(agent, "", true);
        }
    }
    
    private void printAgentTree(MultiStepAgent agent, String prefix, boolean isRoot) {
        String agentType = agent.getClass().getSimpleName();
        String headline = (isRoot ? "" : prefix) + "├─ " + agentType;
        System.out.println(headline);
    }
    
    private String sanitizeForOutput(String content) {
        if (content == null) return "";
        return content.replace("\n", "\\n")
                      .replace("\r", "\\r")
                      .replace("\t", "\\t");
    }
}
