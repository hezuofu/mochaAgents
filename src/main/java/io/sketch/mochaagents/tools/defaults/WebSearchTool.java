package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 通用网页搜索工具。
 *
 * <p>默认使用 DuckDuckGo Instant Answer API（无需密钥），可切换替代后端。
 * 若需要 Google 搜索，请使用 {@link GoogleSearchTool}。</p>
 */
public class WebSearchTool extends AbstractTool {

    private static final String DUCKDUCKGO_URL = "https://api.duckduckgo.com/";

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String backendUrl;

    public WebSearchTool() {
        this(DUCKDUCKGO_URL);
    }

    public WebSearchTool(String backendUrl) {
        super(
            "web_search",
            "Performs a web search for a query and returns results.",
            Map.of("query", ToolInput.string("The search query")),
            "string"
        );
        this.backendUrl = backendUrl;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    protected Object forward(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");

        try {
            String url = backendUrl + "?q="
                + java.net.URLEncoder.encode(query, "UTF-8")
                + "&format=json&no_html=1&skip_disambig=1";

            Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Search failed: HTTP " + response.code();
                }
                String json = response.body().string();
                return formatDuckDuckGoResults(json);
            }
        } catch (Exception e) {
            return "Search error: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String formatDuckDuckGoResults(String json) throws Exception {
        Map<String, Object> data = mapper.readValue(json, Map.class);
        StringBuilder sb = new StringBuilder();

        String abstractText = (String) data.get("AbstractText");
        String abstractUrl = (String) data.get("AbstractURL");
        if (abstractText != null && !abstractText.isBlank()) {
            sb.append("=== Abstract ===\n");
            sb.append(abstractText).append("\n");
            if (abstractUrl != null && !abstractUrl.isBlank()) {
                sb.append("Source: ").append(abstractUrl).append("\n");
            }
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("RelatedTopics");
        if (results != null && !results.isEmpty()) {
            sb.append("\n=== Related Topics ===\n");
            int count = 0;
            for (Map<String, Object> r : results) {
                if (count >= 10) break;
                String text = (String) r.get("Text");
                String firstUrl = (String) r.get("FirstURL");
                if (text != null) {
                    sb.append(count + 1).append(". ").append(text).append("\n");
                    if (firstUrl != null) {
                        sb.append("   URL: ").append(firstUrl).append("\n");
                    }
                    count++;
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("No results found.");
        }
        return sb.toString();
    }
}