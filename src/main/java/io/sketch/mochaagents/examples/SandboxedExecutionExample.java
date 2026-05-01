package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.executor.LocalPythonExecutor;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

import java.util.List;

public class SandboxedExecutionExample {

    public static class DataAnalysisTool extends BaseTool {
        public DataAnalysisTool() {
            super("data_analysis", "Perform data analysis calculations");
        }

        public String call(String data) {
            return "Analyzed data: " + data.length() + " characters";
        }
    }

    public static void main(String[] args) {
        LocalPythonExecutor executor = new LocalPythonExecutor(List.of());
        
        try (CodeAgent agent = CodeAgent.builder()
            .executor(executor)
            .tool(new DataAnalysisTool())
            .model(new InferenceClientModel())
            .build()) {

            Object result = agent.run("Write a Python script to calculate the sum of first 100 integers");
            System.out.println("Result: " + result);
        }
    }
}
