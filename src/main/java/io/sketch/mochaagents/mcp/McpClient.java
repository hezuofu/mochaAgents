package io.sketch.mochaagents.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolCollection;
import io.sketch.mochaagents.tools.ToolInput;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal MCP peer over newline-delimited JSON-RPC 2.0 on a subprocess stdin/stdout, roughly matching Python
 * smolagents MCP stdio deployment.
 *
 * <p>Call {@link #listTools()}, then {@link #close()} when the agent finishes. For a one-liner use
 * {@link ToolCollection#fromMcp(ToolCollection.McpServerParameters)} which wraps this client.</p>
 */
public final class McpClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Process process;
    private final BufferedWriter out;
    private final BufferedReader in;
    private final ExecutorService readerPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcp-jsonrpc-reader");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private volatile long seq = 1;
    private volatile boolean closed;

    private McpClient(Process process, BufferedWriter out, BufferedReader in) {
        this.process = process;
        this.out = out;
        this.in = in;
    }

    /**
     * Spawns {@code command} with {@code args} and optional {@code env} overrides, then completes the MCP
     * handshake.
     */
    public static McpClient connect(ToolCollection.McpServerParameters params) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(params.command());
        if (params.args() != null) {
            cmd.addAll(params.args());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (params.env() != null && !params.env().isEmpty()) {
            pb.environment().putAll(params.env());
        }
        Process proc = pb.start();
        BufferedWriter o = new BufferedWriter(
            new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader i = new BufferedReader(
            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        McpClient client = new McpClient(proc, o, i);
        client.startReader();
        client.handshake();
        return client;
    }

    private void startReader() {
        readerPool.submit(() -> {
            try {
                String line;
                while (!closed && (line = in.readLine()) != null) {
                    JsonNode msg;
                    try {
                        msg = MAPPER.readTree(line);
                    } catch (Exception e) {
                        System.err.println("[mcp] skip non-json line: " + line);
                        continue;
                    }
                    if (msg.has("id")) {
                        String id = msg.get("id").asText();
                        CompletableFuture<JsonNode> h = pending.remove(id);
                        if (h != null) {
                            h.complete(msg);
                        }
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    System.err.println("[mcp] reader stopped: " + e.getMessage());
                }
            } finally {
                for (CompletableFuture<JsonNode> h : pending.values()) {
                    h.completeExceptionally(new IOException("MCP stream closed"));
                }
                pending.clear();
            }
        });
    }

    private void handshake() throws IOException {
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "mochaagents");
        clientInfo.put("version", "1.0.0");
        ObjectNode initParams = MAPPER.createObjectNode();
        initParams.put("protocolVersion", "2024-11-05");
        initParams.set("capabilities", MAPPER.createObjectNode());
        initParams.set("clientInfo", clientInfo);
        request("initialize", initParams);
        ObjectNode notified = MAPPER.createObjectNode();
        notified.put("jsonrpc", "2.0");
        notified.put("method", "notifications/initialized");
        notified.set("params", MAPPER.createObjectNode());
        writeLine(notified);
    }

    private JsonNode request(String method, ObjectNode params) throws IOException {
        String id = Long.toString(seq++);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", id);
        root.put("method", method);
        root.set("params", params == null ? MAPPER.createObjectNode() : params);
        CompletableFuture<JsonNode> cf = new CompletableFuture<>();
        pending.put(id, cf);
        writeLine(root);
        try {
            JsonNode envelope = cf.orTimeout(120, TimeUnit.SECONDS).get();
            JsonNode err = envelope.get("error");
            if (err != null && !err.isNull()) {
                throw new McpRpcException("MCP error: " + err, err);
            }
            return envelope;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (Exception e) {
            throw new IOException("MCP request failed: " + method, e);
        } finally {
            pending.remove(id, cf);
        }
    }

    private synchronized void writeLine(ObjectNode node) throws IOException {
        out.write(MAPPER.writeValueAsString(node));
        out.write('\n');
        out.flush();
    }

    public List<Tool> listTools() throws IOException {
        List<Tool> tools = new ArrayList<>();
        String cursor = null;
        while (true) {
            ObjectNode p = MAPPER.createObjectNode();
            if (cursor != null) {
                p.put("cursor", cursor);
            }
            JsonNode env = request("tools/list", p);
            JsonNode result = env.get("result");
            if (result == null || !result.isObject()) {
                break;
            }
            for (JsonNode t : result.path("tools")) {
                tools.add(adaptTool(t));
            }
            JsonNode nc = result.get("nextCursor");
            if (nc == null || nc.isNull() || nc.asText().isBlank()) {
                break;
            }
            cursor = nc.asText();
        }
        return List.copyOf(tools);
    }

    public ToolCollection toolsCollection() throws IOException {
        return new ToolCollection(listTools(), this);
    }

    Object invokeTool(String toolName, Map<String, Object> arguments) throws IOException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", MAPPER.valueToTree(arguments));
        JsonNode env = request("tools/call", params);
        JsonNode result = env.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new IOException("MCP tool error: " + result);
        }
        StringBuilder textOut = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            String kind = block.path("type").asText("");
            if ("text".equals(kind)) {
                textOut.append(block.path("text").asText());
            }
        }
        JsonNode structured = result.get("structuredContent");
        if (structured != null && !structured.isMissingNode()) {
            if (!structured.isNull()) {
                return MAPPER.convertValue(structured, Object.class);
            }
        }
        return textOut.length() > 0 ? textOut.toString() : result.toString();
    }

    private Tool adaptTool(JsonNode t) {
        String name = t.path("name").asText();
        String desc = t.path("description").asText("");
        JsonNode schema = t.path("inputSchema");
        Map<String, ToolInput> inputs = schemaToInputs(schema);
        return new McpDelegateTool(this, name, desc, inputs);
    }

    private static Map<String, ToolInput> schemaToInputs(JsonNode schema) {
        JsonNode props = schema.path("properties");
        if (!props.isObject()) {
            return Map.of();
        }
        Map<String, ToolInput> m = new LinkedHashMap<>();
        Iterator<String> names = props.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode p = props.get(key);
            String type = p.has("type") ? p.get("type").asText("any") : "any";
            String description = p.path("description").asText("");
            boolean nullable = p.path("nullable").asBoolean(false);
            m.put(key, new ToolInput(type, description, nullable));
        }
        return m;
    }

    @Override
    public void close() {
        closed = true;
        readerPool.shutdownNow();
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static final class McpDelegateTool extends AbstractTool {
        private final McpClient owner;

        McpDelegateTool(McpClient owner, String name, String description, Map<String, ToolInput> inputs) {
            super(name, description, inputs, "string");
            this.owner = owner;
        }

        @Override
        protected Object forward(Map<String, Object> arguments) {
            try {
                return owner.invokeTool(getName(), arguments);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
