package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.LiteLLMModel;
import io.sketch.mochaagents.tools.BaseTool;

public class StructuredOutputToolExample {

    public static class WeatherInfo {
        private final String location;
        private final double temperature;
        private final String conditions;
        private final int humidity;

        public WeatherInfo(String location, double temperature, String conditions, int humidity) {
            this.location = location;
            this.temperature = temperature;
            this.conditions = conditions;
            this.humidity = humidity;
        }

        public String getLocation() { return location; }
        public double getTemperature() { return temperature; }
        public String getConditions() { return conditions; }
        public int getHumidity() { return humidity; }

        @Override
        public String toString() {
            return String.format(
                "{location: '%s', temperature: %.1f, conditions: '%s', humidity: %d}",
                location, temperature, conditions, humidity
            );
        }
    }

    public static class WeatherInfoTool extends BaseTool {
        public WeatherInfoTool() {
            super("get_weather_info", "Get weather information for a location.");
        }

        public WeatherInfo call(String city) {
            return new WeatherInfo(city, 22.5, "partly cloudy", 65);
        }
    }

    public static void main(String[] args) {
        LiteLLMModel model = new LiteLLMModel("mistral/mistral-small-latest");

        try (CodeAgent agent = CodeAgent.builder()
            .tool(new WeatherInfoTool())
            .model(model)
            .build()) {

            agent.run("What is the temperature in Tokyo in Fahrenheit?");
        }
    }
}
