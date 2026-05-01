package io.sketch.mochaagents.types;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 音频类型
 */
public record AgentAudio(byte[] data, String format) {
    
    public static AgentAudio fromPath(String path) throws IOException {
        return fromPath(Path.of(path));
    }
    
    public static AgentAudio fromPath(Path path) throws IOException {
        String format = getFormatFromPath(path);
        byte[] data = Files.readAllBytes(path);
        return new AgentAudio(data, format);
    }
    
    public static AgentAudio fromBase64(String base64, String format) {
        byte[] data = java.util.Base64.getDecoder().decode(base64);
        return new AgentAudio(data, format);
    }
    
    public String toBase64() {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
    
    public int getSize() {
        return data.length;
    }
    
    private static String getFormatFromPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".wav")) return "wav";
        if (fileName.endsWith(".mp3")) return "mp3";
        if (fileName.endsWith(".ogg")) return "ogg";
        if (fileName.endsWith(".flac")) return "flac";
        return "unknown";
    }
}