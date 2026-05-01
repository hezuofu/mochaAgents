package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

/**
 * 网页搜索工具
 */
public class WebSearchTool extends AbstractTool {
    
    private final OkHttpClient client;
    private final String apiUrl;
    
    public WebSearchTool() {
        super(
            "web_search",
            "Performs a web search for a query and returns results.",
            Map.of("query", ToolInput.string("The search query")),
            "string"
        );
        this.client = new OkHttpClient();
        this.apiUrl = "https://api.search.example.com/v1/search";
    }
    
    public WebSearchTool(String apiUrl) {
        super(
            "web_search",
            "Performs a web search for a query and returns results.",
            Map.of("query", ToolInput.string("The search query")),
            "string"
        );
        this.client = new OkHttpClient();
        this.apiUrl = apiUrl;
    }
    
    @Override
    protected Object forward(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        
        try {
            Request request = new Request.Builder()
                .url(apiUrl + "?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Search failed: HTTP " + response.code();
                }
                return response.body().string();
            }
        } catch (Exception e) {
            return "Search error: " + e.getMessage();
        }
    }
}