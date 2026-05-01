package io.sketch.mochaagents.tools.defaults;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;

import java.util.Map;

/**
 * Python 代码执行工具
 */
public class PythonInterpreterTool extends AbstractTool {
    
    public PythonInterpreterTool() {
        super(
            "python_interpreter",
            "Executes Python code and returns the result.",
            Map.of("code", ToolInput.string("Python code to execute")),
            "string"
        );
    }
    
    @Override
    protected Object forward(Map<String, Object> arguments) {
        String code = (String) arguments.get("code");
        
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-c", code);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                return "Error (exit code " + exitCode + "): " + output;
            }
            return output.trim();
            
        } catch (Exception e) {
            return "Python execution error: " + e.getMessage();
        }
    }
}