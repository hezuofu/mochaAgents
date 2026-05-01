package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

public class InspectMultiAgentRunExample {

    public static class CalculatorTool extends BaseTool {
        public CalculatorTool() {
            super("calculator", "Perform math calculations");
        }

        public double call(String expression) {
            try {
                return (double) new javax.script.ScriptEngineManager()
                    .getEngineByName("JavaScript")
                    .eval(expression.replace("^", "**"));
            } catch (Exception e) {
                throw new RuntimeException("Invalid expression: " + expression);
            }
        }
    }

    public static void main(String[] args) {
        CalculatorTool calculator = new CalculatorTool();

        try (CodeAgent agent = CodeAgent.builder()
            .tool(calculator)
            .model(new InferenceClientModel())
            .build()) {

            Object result = agent.run("Calculate (10 + 20) * 3");
            System.out.println("Result: " + result);
            
            System.out.println("\n=== Run History ===");
            System.out.println("Agent completed successfully");
        }
    }
}
