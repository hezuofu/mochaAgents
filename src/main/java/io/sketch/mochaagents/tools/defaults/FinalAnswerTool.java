package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;

import java.util.Map;

/**
 * 最终答案工具
 */
public class FinalAnswerTool extends AbstractTool {
    
    public FinalAnswerTool() {
        super(
            "final_answer",
            "Provides a final answer to the given problem.",
            Map.of("answer", ToolInput.any("The final answer to the problem")),
            "any"
        );
    }
    
    @Override
    protected Object forward(Map<String, Object> arguments) {
        return arguments.get("answer");
    }
    
    @Override
    public String toCodePrompt() {
        return "public Object final_answer(Object answer) {\n    /** Provides a final answer to the given problem. */\n}";
    }
}