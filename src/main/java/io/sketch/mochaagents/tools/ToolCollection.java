package io.sketch.mochaagents.tools;

import io.sketch.mochaagents.mcp.McpClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 工具集合
 */
public class ToolCollection implements AutoCloseable {

    private final List<Tool> tools;
    private final AutoCloseable lifecycle;

    public ToolCollection(List<Tool> tools) {
        this(tools, null);
    }

    /** @param lifecycle optional resource to close with this collection (e.g. {@link McpClient}). */
    public ToolCollection(List<Tool> tools, AutoCloseable lifecycle) {
        this.tools = List.copyOf(tools);
        this.lifecycle = lifecycle;
    }

    @Override
    public void close() throws Exception {
        if (lifecycle != null) {
            lifecycle.close();
        }
    }
    
    /**
     * 从 HuggingFace Hub 加载工具集合。
     *
     * <p>Not implemented in Java yet; callers should construct tools programmatically or use a HF HTTP client.
     * Previously this returned an empty list and hid misconfiguration.</p>
     */
    public static ToolCollection fromHub(String collectionSlug, String token) {
        throw new UnsupportedOperationException(
            "ToolCollection.fromHub(collectionSlug,token) is not implemented. Provide tools via ToolCollection(List) or MCP integration.");
    }
    
    /**
     * 连接 MCP subprocess（newline-delimited JSON-RPC），枚举工具并保持进程存活直至 {@link #close()}。
     *
     * @throws IOException MCP 握手或枚举失败
     */
    public static ToolCollection fromMcp(McpServerParameters params) throws IOException {
        McpClient client = McpClient.connect(params);
        return client.toolsCollection();
    }
    
    public List<Tool> getTools() {
        return tools;
    }
    
    public Map<String, Tool> toMap() {
        return tools.stream()
            .collect(java.util.stream.Collectors.toMap(Tool::getName, t -> t));
    }
    
    /**
     * MCP 服务器参数
     */
    public record McpServerParameters(
        String command,
        List<String> args,
        Map<String, String> env
    ) {}
}