package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.LiteLLMModel;
import io.sketch.mochaagents.tools.BaseTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RagUsingChromadbExample {

    public static class Document {
        private final String pageContent;
        private final Map<String, String> metadata;

        public Document(String pageContent, Map<String, String> metadata) {
            this.pageContent = pageContent;
            this.metadata = metadata;
        }

        public String getPageContent() { return pageContent; }
        public Map<String, String> getMetadata() { return metadata; }
    }

    public static class ChromaRetrieverTool extends BaseTool {
        private final List<Document> documents;
        private final Random random = new Random();

        public ChromaRetrieverTool(List<Document> documents) {
            super("retriever", "Uses semantic search to retrieve docs.");
            this.documents = documents;
        }

        public String call(String query) {
            StringBuilder result = new StringBuilder("Retrieved documents:\n");
            
            int count = Math.min(3, documents.size());
            for (int i = 0; i < count; i++) {
                int idx = random.nextInt(documents.size());
                Document doc = documents.get(idx);
                result.append("\n===== Document ").append(i + 1).append(" =====\n");
                result.append(doc.getPageContent());
            }
            return result.toString();
        }
    }

    public static void main(String[] args) {
        List<Document> knowledgeBase = new ArrayList<>();
        knowledgeBase.add(new Document(
            "To push a model to Hugging Face Hub, use push_to_hub method.",
            Map.of("source", "transformers/docs/push_to_hub.md")
        ));

        ChromaRetrieverTool retrieverTool = new ChromaRetrieverTool(knowledgeBase);

        LiteLLMModel model = new LiteLLMModel("groq/openai/gpt-oss-120b", System.getenv("GROQ_API_KEY"));

        try (CodeAgent agent = CodeAgent.builder()
            .tool(retrieverTool)
            .model(model)
            .maxSteps(4)
            .build()) {

            Object result = agent.run("How can I push a model to the Hub?");
            System.out.println("\nFinal output:\n" + result);
        }
    }
}
