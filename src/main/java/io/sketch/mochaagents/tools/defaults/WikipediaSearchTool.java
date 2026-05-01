package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Map;

/**
 * Wikipedia 搜索工具
 */
public class WikipediaSearchTool extends AbstractTool {
    
    private final OkHttpClient client;
    
    public WikipediaSearchTool() {
        super(
            "wikipedia_search",
            "Searches Wikipedia for a query and returns summaries.",
            Map.of("query", ToolInput.string("The search query")),
            "string"
        );
        this.client = new OkHttpClient();
    }
    
    @Override
    protected Object forward(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        
        try {
            String url = "https://en.wikipedia.org/w/api.php?" +
                "action=query&list=search&srsearch=" + java.net.URLEncoder.encode(query, "UTF-8") +
                "&srlimit=5&format=json";
            
            Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
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