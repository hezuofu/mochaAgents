package io.sketch.mochaagents.examples;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.models.InferenceClientModel;
import io.sketch.mochaagents.tools.BaseTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RagExample {

    public static class Document {
        private final String pageContent;
        private final String source;

        public Document(String pageContent, String source) {
            this.pageContent = pageContent;
            this.source = source;
        }

        public String getPageContent() { return pageContent; }
        public String getSource() { return source; }
    }

    public static class RetrieverTool extends BaseTool {
        private final List<Document> documents;
        private final Random random = new Random();

        public RetrieverTool(List<Document> docs) {
            super("retriever", "Uses lexical search to retrieve relevant docs.");
            this.documents = docs;
        }

        public String call(String query) {
            StringBuilder result = new StringBuilder("Retrieved documents:\n");
            
            int count = Math.min(3, documents.size());
            for (int i = 0; i < count; i++) {
                int idx = random.nextInt(documents.size());
                Document doc = documents.get(idx);
                result.append("\n===== Document ").append(i + 1).append(" =====\n");
                result.append(doc.getPageContent().substring(0, Math.min(200, doc.getPageContent().length())));
            }
            return result.toString();
        }
    }

    public static void main(String[] args) {
        List<Document> knowledgeBase = new ArrayList<>();
        knowledgeBase.add(new Document(
            "The forward pass computes output from input data. It involves multiplying input by weights.",
            "transformers/docs/forward_pass.md"
        ));
        knowledgeBase.add(new Document(
            "The backward pass computes gradients using chain rule. It's typically slower than forward pass.",
            "transformers/docs/backward_pass.md"
        ));

        RetrieverTool retrieverTool = new RetrieverTool(knowledgeBase);
        
        try (CodeAgent agent = CodeAgent.builder()
            .tool(retrieverTool)
            .model(new InferenceClientModel("Qwen/Qwen3-Next-80B-A3B-Thinking"))
            .maxSteps(4)
            .build()) {

            agent.run("Which is slower, forward or backward pass?");
        }
    }
}
