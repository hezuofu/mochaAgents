package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.ResponseFormat;
import io.sketch.mochaagents.tools.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeAgent 测试类
 */
public class CodeAgentTest {
    
    private Model mockModel;
    
    @BeforeEach
    void setUp() {
        mockModel = new MockModel();
    }
    
    @Test
    void testCodeAgentCreation() {
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .maxSteps(10)
            .name("TestAgent")
            .description("Test description")
            .build();
        
        assertNotNull(agent);
        assertEquals("TestAgent", agent.name);
        assertEquals("Test description", agent.description);
        assertEquals(10, agent.maxSteps);
    }
    
    @Test
    void testCodeAgentWithTools() {
        List<Tool> tools = List.of();
        
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .tools(tools)
            .build();
        
        assertNotNull(agent);
        assertEquals(0, agent.tools.size());
    }
    
    @Test
    void testCodeAgentRun() {
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .maxSteps(5)
            .build();
        
        Object result = agent.run("What is AI?");
        assertNotNull(result);
    }
    
    @Test
    void testContextManager() {
        try (CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .maxSteps(5)
            .build()) {
            assertNotNull(agent);
            agent.run("Test task");
        }
    }
    
    @Test
    void testToDict() {
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .maxSteps(10)
            .name("TestAgent")
            .description("Test description")
            .useStructuredOutputsInternally(true)
            .streamOutputs(true)
            .build();
        
        Map<String, Object> dict = agent.toDict();
        
        assertNotNull(dict);
        assertEquals("TestAgent", dict.get("name"));
        assertEquals("Test description", dict.get("description"));
        assertEquals(10, dict.get("max_steps"));
        assertEquals(true, dict.get("use_structured_outputs_internally"));
        assertEquals(true, dict.get("stream_outputs"));
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .maxSteps(10)
            .name("TestAgent")
            .description("Test description")
            .useStructuredOutputsInternally(true)
            .streamOutputs(true)
            .build();
        
        String json = agent.toJson();
        assertNotNull(json);
        assertTrue(json.contains("TestAgent"));
        assertTrue(json.contains("use_structured_outputs_internally"));
        
        CodeAgent restored = CodeAgent.fromJson(json, mockModel);
        assertNotNull(restored);
        assertEquals("TestAgent", restored.name);
        assertEquals(10, restored.maxSteps);
    }
    
    @Test
    void testExecutorTypeAndKwargs() {
        CodeAgent agent = CodeAgent.builder()
            .model(mockModel)
            .executorType("local")
            .maxPrintOutputsLength(1000)
            .build();
        
        assertNotNull(agent);
        Map<String, Object> dict = agent.toDict();
        assertEquals("local", dict.get("executor_type"));
        assertNotNull(dict.get("executor_kwargs"));
    }
    
    /**
     * Mock 模型实现
     */
    private static class MockModel implements Model {
        @Override
        public ChatMessage generate(List<ChatMessage> messages) {
            return ChatMessage.text(MessageRole.ASSISTANT,
                "```python\nfinal_answer(\"AI is Artificial Intelligence\")\n```");
        }
        
        @Override
        public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools) {
            return generate(messages);
        }
        
        @Override
        public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format) {
            return generate(messages);
        }

        @Override
        public ChatMessage generate(
            List<ChatMessage> messages,
            List<Tool> tools,
            List<String> stopSequences,
            ResponseFormat format,
            Map<String, Object> extraParameters
        ) {
            return generate(messages);
        }
        
        @Override
        public String getModelId() {
            return "mock-model";
        }
        
        @Override
        public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
            return generate(messages);
        }
    }
}