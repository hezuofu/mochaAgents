package io.sketch.mochaagents.examples.async_agent;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncAgentExample {

    private static CodeAgent getAgent() {
        return CodeAgent.builder()
            .model(new InferenceClientModel("Qwen/Qwen3-Next-80B-A3B-Thinking"))
            .build();
    }

    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Create thread pool for async processing
        ExecutorService executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);

        server.createContext("/run-agent", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                // Read request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes());

                // Parse task from JSON (simplified)
                String task = extractTaskFromJson(requestBody);

                if (task == null || task.isEmpty()) {
                    String response = "{\"error\": \"Missing 'task' in request body.\"}";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                // Run agent in background thread
                executor.submit(() -> {
                    try (CodeAgent agent = getAgent()) {
                        Object resultObj = agent.run(task);
                        String result = resultObj != null ? resultObj.toString() : "";
                        String response = "{\"result\": \"" + escapeJson(result) + "\"}";
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    } catch (Exception e) {
                        try {
                            String response = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                            exchange.sendResponseHeaders(500, response.getBytes().length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(response.getBytes());
                            }
                        } catch (IOException ioEx) {
                            ioEx.printStackTrace();
                        }
                    }
                });
            }
        });

        server.start();
        System.out.println("Server started on port " + port);
        System.out.println("Use: POST http://localhost:" + port + "/run-agent with {\"task\": \"your task\"}");
    }

    private static String extractTaskFromJson(String json) {
        // Simplified JSON parsing
        int taskStart = json.indexOf("\"task\":");
        if (taskStart == -1) return null;

        int valueStart = json.indexOf("\"", taskStart + 7);
        int valueEnd = json.indexOf("\"", valueStart + 1);

        if (valueStart == -1 || valueEnd == -1) return null;

        return json.substring(valueStart + 1, valueEnd);
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
