package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMemory 测试类
 */
public class AgentMemoryTest {
    
    private AgentMemory memory;
    
    @BeforeEach
    void setUp() {
        memory = new AgentMemory("System prompt");
    }
    
    @Test
    void testMemoryInitialization() {
        assertEquals(0, memory.getStepCount());
    }
    
    @Test
    void testAddStep() {
        TaskStep task = new TaskStep("Test task");
        memory.addStep(task);
        
        assertEquals(0, memory.getStepCount());
    }
    
    @Test
    void testAddActionStep() {
        ActionStep step = ActionStep.builder()
            .stepNumber(1)
            .codeAction("test code")
            .observations("test observations")
            .isFinalAnswer(false)
            .build();
        
        memory.addStep(step);
        
        assertEquals(1, memory.getStepCount());
    }
    
    @Test
    void testReset() {
        ActionStep step = ActionStep.builder()
            .stepNumber(1)
            .codeAction("test")
            .build();
        memory.addStep(step);
        
        assertEquals(1, memory.getStepCount());
        
        memory.reset();
        
        assertEquals(0, memory.getStepCount());
    }
    
    @Test
    void testToMessages() {
        memory.addStep(new TaskStep("Test task"));
        
        List<ChatMessage> messages = memory.toMessages();
        
        assertEquals(2, messages.size()); // system prompt + task
        assertEquals(MessageRole.SYSTEM, messages.get(0).role());
        assertEquals(MessageRole.USER, messages.get(1).role());
    }
}