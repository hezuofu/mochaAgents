package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.tools.Tool;
import org.codehaus.janino.SimpleCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 本地 Java 代码执行器
 */
public class LocalJavaExecutor implements JavaExecutor {
    
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Set<String> authorizedImports = new HashSet<>();
    private final long timeoutSeconds;
    
    public LocalJavaExecutor() {
        this(30);
    }
    
    public LocalJavaExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        authorizedImports.addAll(Set.of(
            "java.lang",
            "java.util",
            "java.math"
        ));
    }
    
    @Override
    public CodeOutput execute(String code) {
        List<String> logs = new ArrayList<>();
        
        try {
            String fullCode = buildFullCode(code, variables);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            
            Future<Object> future = executor.submit(() -> {
                try {
                    SimpleCompiler compiler = new SimpleCompiler();
                    compiler.cook(fullCode);
                    Class<?> clazz = compiler.getClassLoader().loadClass("DynamicCode");
                    return clazz.getMethod("execute").invoke(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            Object result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            executor.shutdown();
            
            boolean isFinalAnswer = checkFinalAnswer(result);
            
            return new CodeOutput(result, String.join("\n", logs), isFinalAnswer);
            
        } catch (TimeoutException e) {
            return new CodeOutput(null, "Execution timed out", false);
        } catch (Exception e) {
            logs.add("Error: " + e.getMessage());
            return new CodeOutput(null, String.join("\n", logs), false);
        }
    }
    
    @Override
    public void sendVariables(Map<String, Object> variables) {
        this.variables.putAll(variables);
    }
    
    @Override
    public void sendTools(Map<String, Tool> tools) {
        this.tools.putAll(tools);
    }
    
    public void reset() {
        variables.clear();
        tools.clear();
    }
    
    private String buildFullCode(String code, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("import java.util.*;\n");
        sb.append("import java.math.*;\n");
        
        sb.append("public class DynamicCode {\n");
        sb.append("    public static Object execute() {\n");
        
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String valueStr = serializeValue(entry.getValue());
            sb.append("        Object ").append(entry.getKey()).append(" = ").append(valueStr).append(";\n");
        }
        
        for (String toolName : tools.keySet()) {
            sb.append("        Tool ").append(toolName).append(" = tools.get(\"").append(toolName).append("\");\n");
        }
        
        sb.append("        return (() -> {\n");
        sb.append("            ").append(code).append("\n");
        sb.append("        }).apply(null);\n");
        
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }
    
    private String serializeValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeString((String) value) + "\"";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof List) {
            return "List.of(" + ((List<?>) value).stream()
                .map(this::serializeValue)
                .collect(Collectors.joining(", ")) + ")";
        }
        return "null";
    }
    
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
    
    private boolean checkFinalAnswer(Object result) {
        return result instanceof String str && str.contains("FINAL_ANSWER");
    }
}
