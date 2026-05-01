package io.sketch.mochaagents.examples.plan_customization;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

public class PlanCustomizationExample {

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

            Object result = agent.run("What is (25 * 4) + (18 / 3) - 5?");
            System.out.println("Result: " + result);
        }
    }
}
