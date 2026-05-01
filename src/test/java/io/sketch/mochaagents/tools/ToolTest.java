package io.sketch.mochaagents.tools;

import io.sketch.mochaagents.tools.defaults.FinalAnswerTool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tool 测试类
 */
public class ToolTest {
    
    @Test
    void testFinalAnswerTool() {
        FinalAnswerTool tool = new FinalAnswerTool();
        
        assertEquals("final_answer", tool.getName());
        assertEquals("Provides a final answer to the given problem.", tool.getDescription());
        assertEquals("any", tool.getOutputType());
        
        Map<String, Object> args = Map.of("answer", "Test answer");
        Object result = tool.call(args);
        
        assertEquals("Test answer", result);
    }
    
    @Test
    void testAbstractToolToCodePrompt() {
        AbstractTool tool = new AbstractTool("test_tool", "Test description", 
            Map.of("param1", ToolInput.string("Param 1"), "param2", ToolInput.integer("Param 2")), "string") {
            @Override
            protected Object forward(Map<String, Object> arguments) {
                return null;
            }
        };
        
        String prompt = tool.toCodePrompt();
        assertTrue(prompt.contains("public Object test_tool"));
        assertTrue(prompt.contains("String param1"));
        assertTrue(prompt.contains("int param2"));
    }
    
    @Test
    void testToolInputCreation() {
        ToolInput input = ToolInput.string("Test input");
        
        assertEquals("string", input.type());
        assertEquals("Test input", input.description());
        assertFalse(input.nullable());
        
        ToolInput nullableInput = ToolInput.stringNullable("Nullable input");
        assertTrue(nullableInput.nullable());
    }
}