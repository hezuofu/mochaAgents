package io.sketch.mochaagents.tools;

import java.util.List;
import java.util.Map;

/**
 * 工具集合
 */
public class ToolCollection {
    
    private final List<Tool> tools;
    
    public ToolCollection(List<Tool> tools) {
        this.tools = tools;
    }
    
    /**
     * 从 HuggingFace Hub 加载工具集合
     */
    public static ToolCollection fromHub(String collectionSlug, String token) {
        return new ToolCollection(List.of());
    }
    
    /**
     * 从 MCP 服务器加载工具集合
     */
    public static ToolCollection fromMcp(McpServerParameters params) {
        return new ToolCollection(List.of());
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