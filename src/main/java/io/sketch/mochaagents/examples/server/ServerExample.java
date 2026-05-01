package io.sketch.mochaagents.examples.server;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

public class ServerExample {

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
            .model(new InferenceClientModel("meta-llama/Llama-3.3-70B-Instruct"))
            .build()) {

            System.out.println("Server example - simulating server startup...");
            Object result = agent.run("Calculate 100 + 200");
            System.out.println("Calculation result: " + result);
        }
    }
}
