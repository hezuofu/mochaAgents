package io.sketch.mochaagents.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON-RPC {@code error} object from an MCP peer. */
public final class McpRpcException extends RuntimeException {

    private final transient JsonNode errorNode;

    public McpRpcException(String message, JsonNode errorNode) {
        super(message);
        this.errorNode = errorNode;
    }

    public JsonNode errorNode() {
        return errorNode;
    }
}
