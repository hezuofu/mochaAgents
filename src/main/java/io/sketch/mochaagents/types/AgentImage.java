package io.sketch.mochaagents.types;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 图像类型
 */
public record AgentImage(byte[] data, String format) {
    
    public static AgentImage fromPath(String path) throws IOException {
        return fromPath(Path.of(path));
    }
    
    public static AgentImage fromPath(Path path) throws IOException {
        String format = getFormatFromPath(path);
        byte[] data = Files.readAllBytes(path);
        return new AgentImage(data, format);
    }
    
    public static AgentImage fromBase64(String base64, String format) {
        byte[] data = java.util.Base64.getDecoder().decode(base64);
        return new AgentImage(data, format);
    }
    
    public String toBase64() {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
    
    public int getSize() {
        return data.length;
    }
    
    private static String getFormatFromPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) return "png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "jpeg";
        if (fileName.endsWith(".gif")) return "gif";
        if (fileName.endsWith(".webp")) return "webp";
        return "unknown";
    }
}