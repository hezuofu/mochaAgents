package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.LiteLLMModel;
import io.sketch.mochaagents.tools.BaseTool;

public class MultiLlmAgentExample {

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
        LiteLLMModel gpt4o = new LiteLLMModel("gpt-4o");
        LiteLLMModel mistral = new LiteLLMModel("mistral/mistral-small-latest");

        CalculatorTool calculator = new CalculatorTool();

        try (CodeAgent agent = CodeAgent.builder()
            .tool(calculator)
            .model(gpt4o)
            .build()) {

            Object result = agent.run("Calculate the factorial of 15");
            System.out.println("Result: " + result);
        }
    }
}
