package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.agents.ToolCallingAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.models.LiteLLMModel;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.TransformersModel;
import io.sketch.mochaagents.tools.BaseTool;

public class AgentFromAnyLlmExample {

    public static class WeatherTool extends BaseTool {
        public WeatherTool() {
            super("get_weather", "Get weather at given location.");
        }

        public String call(String location, Boolean celsius) {
            return "The weather is UNGODLY with torrential rains!";
        }
    }

    public static void main(String[] args) {
        String chosenInference = "inference_client";
        System.out.println("Chose model: '" + chosenInference + "'");

        Model model = switch (chosenInference) {
            case "inference_client" -> new InferenceClientModel("meta-llama/Llama-3.3-70B-Instruct", "nebius");
            case "transformers" -> new TransformersModel("HuggingFaceTB/SmolLM2-1.7B-Instruct", "auto", 1000);
            case "ollama" -> new LiteLLMModel("ollama_chat/llama3.2", "http://localhost:11434", "your-api-key");
            case "litellm" -> new LiteLLMModel("gpt-4o");
            default -> new InferenceClientModel();
        };

        WeatherTool weatherTool = new WeatherTool();

        try (ToolCallingAgent toolAgent = ToolCallingAgent.builder()
            .tool(weatherTool)
            .model(model)
            .build()) {

            Object result = toolAgent.run("What's the weather like in Paris?");
            System.out.println("ToolCallingAgent: " + result);
        }

        try (CodeAgent codeAgent = CodeAgent.builder()
            .tool(weatherTool)
            .model(model)
            .build()) {

            Object result = codeAgent.run("What's the weather like in Paris?");
            System.out.println("CodeAgent: " + result);
        }
    }
}
