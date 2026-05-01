package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.ToolCallingAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

public class ToolCallingAgentExample {

    public static class CalculatorTool extends BaseTool {
        public CalculatorTool() {
            super("calculate", "Perform arithmetic calculations");
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

    public static class GreetingTool extends BaseTool {
        public GreetingTool() {
            super("greet", "Generate a friendly greeting");
        }

        public String call(String name) {
            return "Hello " + name + "! Nice to meet you!";
        }
    }

    public static void main(String[] args) {
        try (ToolCallingAgent agent = ToolCallingAgent.builder()
            .tool(new CalculatorTool())
            .tool(new GreetingTool())
            .model(new InferenceClientModel())
            .build()) {

            Object result1 = agent.run("What is 25 * 4 + 18 / 3?");
            System.out.println("Calculation result: " + result1);

            Object result2 = agent.run("Greet John");
            System.out.println("Greeting result: " + result2);
        }
    }
}
