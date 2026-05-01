package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

/**
 * 网页访问工具
 */
public class VisitWebpageTool extends AbstractTool {
    
    private final OkHttpClient client;
    
    public VisitWebpageTool() {
        super(
            "visit_webpage",
            "Visits a webpage and returns its content.",
            Map.of("url", ToolInput.string("The URL of the webpage to visit")),
            "string"
        );
        this.client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    }
    
    @Override
    protected Object forward(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        
        try {
            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "smolagents-java/1.0")
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Failed to visit webpage: HTTP " + response.code();
                }
                
                String contentType = response.header("Content-Type", "");
                if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                    return response.body().string();
                } else {
                    return "Content type not supported: " + contentType;
                }
            }
        } catch (Exception e) {
            return "Error visiting webpage: " + e.getMessage();
        }
    }
}