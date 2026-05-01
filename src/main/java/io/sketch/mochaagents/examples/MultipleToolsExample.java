package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

public class MultipleToolsExample {

    public static class WeatherTool extends BaseTool {
        private static final String API_KEY = "your_api_key";
        
        public WeatherTool() {
            super("get_weather", "Get the current weather at the given location.");
        }

        public String call(String location, Boolean celsius) {
            boolean useCelsius = celsius != null && celsius;
            String units = useCelsius ? "m" : "f";
            String url = String.format("http://api.weatherstack.com/current?access_key=%s&query=%s&units=%s", 
                API_KEY, location, units);

            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return "Weather data retrieved for " + location;
            } catch (Exception e) {
                return "Error fetching weather data: " + e.getMessage();
            }
        }
    }

    public static class CurrencyConverterTool extends BaseTool {
        private static final String API_KEY = "your_api_key";
        
        public CurrencyConverterTool() {
            super("convert_currency", "Converts currency using ExchangeRate-API.");
        }

        public String call(Double amount, String fromCurrency, String toCurrency) {
            String url = String.format("https://v6.exchangerate-api.com/v6/%s/latest/%s", API_KEY, fromCurrency);
            
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return String.format("%.2f %s conversion initiated", amount, fromCurrency);
            } catch (Exception e) {
                return "Error fetching conversion data: " + e.getMessage();
            }
        }
    }

    public static class JokeTool extends BaseTool {
        public JokeTool() {
            super("get_joke", "Fetches a random joke.");
        }

        public String call() {
            String url = "https://v2.jokeapi.dev/joke/Any?type=single";
            
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                
                if (body.contains("\"joke\":\"")) {
                    int start = body.indexOf("\"joke\":\"") + 8;
                    int end = body.indexOf("\"", start);
                    return body.substring(start, end);
                }
                return "Joke retrieved";
            } catch (Exception e) {
                return "Error fetching joke: " + e.getMessage();
            }
        }
    }

    public static class RandomFactTool extends BaseTool {
        public RandomFactTool() {
            super("get_random_fact", "Fetches a random fact.");
        }

        public String call() {
            String url = "https://uselessfacts.jsph.pl/random.json?language=en";
            
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                
                if (body.contains("\"text\":\"")) {
                    int start = body.indexOf("\"text\":\"") + 8;
                    int end = body.indexOf("\"", start);
                    return "Random Fact: " + body.substring(start, end);
                }
                return "Fact retrieved";
            } catch (Exception e) {
                return "Error fetching fact: " + e.getMessage();
            }
        }
    }

    public static class WikipediaTool extends BaseTool {
        public WikipediaTool() {
            super("search_wikipedia", "Fetches Wikipedia summary.");
        }

        public String call(String query) {
            String url = String.format("https://en.wikipedia.org/api/rest_v1/page/summary/%s", query);
            
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return "Wikipedia summary retrieved for: " + query;
            } catch (Exception e) {
                return "Error fetching Wikipedia: " + e.getMessage();
            }
        }
    }

    public static void main(String[] args) {
        var model = new InferenceClientModel();
        
        try (CodeAgent agent = CodeAgent.builder()
            .tool(new WeatherTool())
            .tool(new CurrencyConverterTool())
            .tool(new JokeTool())
            .tool(new RandomFactTool())
            .tool(new WikipediaTool())
            .model(model)
            .streamOutputs(true)
            .build()) {

            Object result = agent.run("Convert 5000 dollars to Euros");
            System.out.println("Result: " + result);
        }
    }
}
