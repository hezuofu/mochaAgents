package io.sketch.mochaagents.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.cli.Cli;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.registry.ModelConnectionConfig;
import io.sketch.mochaagents.registry.ModelRegistry;
import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.defaults.DuckDuckGoSearchTool;
import io.sketch.mochaagents.tools.defaults.VisitWebpageTool;
import io.sketch.mochaagents.tools.defaults.WikipediaSearchTool;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal single-page HTTP UI similar in spirit to smolagents Gradio entrypoints: POST a task,
 * synchronously runs a {@link CodeAgent}, returns plaintext / HTML wrapped result. Uses only the JDK
 * {@link HttpServer}.
 *
 * <p>Start via {@link io.sketch.mochaagents.cli.Cli} {@code serve} mode or {@code main} directly.</p>
 */
public final class SimpleAgentHttpServer {

    private SimpleAgentHttpServer() {}

    public static void main(String[] args) throws Exception {
        int port = 8787;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        ExecutorService exec = Executors.newFixedThreadPool(4);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", ex -> exec.submit(() -> {
            try {
                handleIndex(ex);
            } catch (IOException e) {
                try {
                    ex.sendResponseHeaders(500, -1);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }));
        server.createContext("/run", ex -> exec.submit(() -> handleRun(ex)));
        server.setExecutor(exec);
        server.start();

        System.out.println("Simple agent UI: http://127.0.0.1:" + port + "/");
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        byte[] html = INDEX_HTML.formatted("(optional, defaults env HF token)", "Qwen/Qwen2.5-Coder-32B-Instruct")
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, html.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(html);
        }
    }

    private static void handleRun(HttpExchange exchange) {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);
            String task = form.getOrDefault("task", "").strip();
            String modelId = form.getOrDefault("model_id", "Qwen/Qwen2.5-Coder-32B-Instruct").strip();
            String apiKey = form.getOrDefault("api_key", "").strip();

            if (task.isEmpty()) {
                byte[] msg = "Missing task".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(400, msg.length);
                exchange.getResponseBody().write(msg);
                exchange.close();
                return;
            }

            Cli.loadEnv();
            String resolvedId = modelId.isEmpty() ? "Qwen/Qwen2.5-Coder-32B-Instruct" : modelId;
            String resolvedKey = apiKey.isEmpty() ? getenvOrProp("HF_API_KEY") : apiKey;
            String provRaw = getenvOrProp("HF_PROVIDER");
            String pv = provRaw != null && !provRaw.isBlank() ? provRaw : null;
            Model model =
                ModelRegistry.create(
                    "InferenceClientModel",
                    new ModelConnectionConfig(resolvedId, null, resolvedKey, pv));

            List<Tool> tools =
                List.of(
                    new DuckDuckGoSearchTool(),
                    new VisitWebpageTool(),
                    new WikipediaSearchTool());

            String resultText;
            try (CodeAgent agent =
                    CodeAgent.builder()
                        .model(model)
                        .tools(tools)
                        .build()) {
                Object out = agent.run(task);
                resultText = String.valueOf(out);
            }

            byte[] payload =
                    RESULT_HTML.formatted(escapeHtml(task), escapeHtml(resultText)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        } catch (Exception e) {
            try {
                String err = escapeHtml(String.valueOf(e.getMessage())) + "<pre>" + escapeHtml(stackToString(e)) + "</pre>";
                byte[] b = RESULT_HTML.formatted("error", err).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(500, b.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(b);
                }
            } catch (IOException ignored) {
                // fallback
            }
        }
    }

    private static String getenvOrProp(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        return System.getProperty(key, "");
    }

    private static Map<String, String> parseForm(String body) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String stackToString(Throwable t) {
        List<String> lines = new ArrayList<>();
        for (StackTraceElement el : t.getStackTrace()) {
            lines.add(el.toString());
        }
        return String.join("\n", lines.subList(0, Math.min(30, lines.size())));
    }

    private static final String INDEX_HTML =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="utf-8"><title>mochaAgents — simple UI</title>
        <style>body{font-family:system-ui;max-width:720px;margin:2rem auto;}label{display:block;margin-top:.75rem}
        textarea,input{width:100%%;box-sizing:border-box}button{margin-top:1rem;padding:.5rem 1rem}</style></head>
        <body><h1>Code agent</h1><p>Lightweight JDK UI (non-Gradio). %s</p>
        <form method="post" action="/run"><label>Task<textarea name="task" rows="6" required></textarea></label>
        <label>Model id<input name="model_id" type="text" value="%s"></label>
        <label>Hugging Face API token (optional if env HF_API_KEY)<input name="api_key" type="password"></label>
        <button type="submit">Run</button></form></body></html>
        """;

    private static final String RESULT_HTML =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="utf-8"><title>Result</title>
        <style>body{font-family:system-ui;max-width:900px;margin:2rem auto;}pre{white-space:pre-wrap;background:#f4f4f4;padding:1rem}</style>
        </head><body><h1>Done</h1><p><a href="/">Back</a></p><h2>Task</h2><pre>%s</pre><h2>Output</h2><pre>%s</pre></body></html>
        """;
}
