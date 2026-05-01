package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.tools.Tool;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalPythonExecutor implements PythonExecutor {
    
    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, Tool> tools = new HashMap<>();
    private final Set<String> authorizedImports;
    private final int maxPrintOutputsLength;
    private final int timeoutSeconds;
    private final StringBuilder printOutputs = new StringBuilder();
    
    private static final Set<String> BASE_BUILTIN_MODULES = Set.of(
        "math", "random", "json", "re", "datetime", "collections", "itertools"
    );
    
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
        "final_answer\\s*\\(\\s*(.+?)\\s*\\)", Pattern.DOTALL
    );
    
    public LocalPythonExecutor(List<String> additionalAuthorizedImports) {
        this(additionalAuthorizedImports, 50000, null, 30);
    }
    
    public LocalPythonExecutor(List<String> additionalAuthorizedImports, 
                               Integer maxPrintOutputsLength,
                               Map<String, Object> additionalFunctions,
                               Integer timeoutSeconds) {
        this.maxPrintOutputsLength = maxPrintOutputsLength != null ? maxPrintOutputsLength : 50000;
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 30;
        
        Set<String> imports = new HashSet<>(BASE_BUILTIN_MODULES);
        if (additionalAuthorizedImports != null) {
            imports.addAll(additionalAuthorizedImports);
        }
        this.authorizedImports = Collections.unmodifiableSet(imports);
        
        state.put("__name__", "__main__");
        if (additionalFunctions != null) {
            state.putAll(additionalFunctions);
        }
    }
    
    @Override
    public void sendTools(Map<String, Tool> tools) {
        this.tools.putAll(tools);
    }
    
    @Override
    public void sendVariables(Map<String, Object> variables) {
        state.putAll(variables);
    }
    
    @Override
    public CodeOutput execute(String codeAction) {
        printOutputs.setLength(0);
        
        Matcher finalAnswerMatcher = FINAL_ANSWER_PATTERN.matcher(codeAction);
        if (finalAnswerMatcher.find()) {
            String answer = finalAnswerMatcher.group(1).trim();
            answer = removeQuotes(answer);
            return CodeOutput.finalAnswer(answer);
        }
        
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Object> future = executor.submit(() -> executeCode(codeAction));
            
            Object result;
            try {
                result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Code execution timed out after " + timeoutSeconds + " seconds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Execution interrupted");
            } catch (ExecutionException e) {
                throw new RuntimeException("Execution error: " + e.getCause().getMessage(), e.getCause());
            } finally {
                executor.shutdown();
            }
            
            String logs = printOutputs.toString();
            if (logs.length() > maxPrintOutputsLength) {
                logs = logs.substring(0, maxPrintOutputsLength) + "... [truncated]";
            }
            
            return CodeOutput.intermediate(result != null ? result.toString() : null, logs);
            
        } catch (Exception e) {
            String logs = printOutputs.toString();
            return CodeOutput.intermediate(null, "Error: " + e.getMessage() + "\nLogs: " + logs);
        }
    }
    
    private Object executeCode(String code) {
        List<String> lines = Arrays.asList(code.split("\n"));
        Object result = null;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            if (line.startsWith("import ") || line.startsWith("from ")) {
                validateImport(line);
            } else if (line.contains("=")) {
                executeAssignment(line);
            } else if (line.startsWith("print(")) {
                executePrint(line);
            } else if (line.startsWith("tool_")) {
                result = executeToolCall(line);
            } else {
                result = evaluateExpression(line);
            }
        }
        
        return result;
    }
    
    private void validateImport(String importLine) {
        String moduleName;
        if (importLine.startsWith("import ")) {
            moduleName = importLine.substring(7).split("\\s")[0];
        } else if (importLine.startsWith("from ")) {
            moduleName = importLine.substring(5).split("\\s")[0];
        } else {
            return;
        }
        
        if (!authorizedImports.contains(moduleName) && !authorizedImports.contains("*")) {
            throw new SecurityException("Import of '" + moduleName + "' is not allowed");
        }
    }
    
    private void executeAssignment(String line) {
        int eqIndex = line.indexOf('=');
        String varName = line.substring(0, eqIndex).trim();
        String expr = line.substring(eqIndex + 1).trim();
        
        Object value = evaluateExpression(expr);
        state.put(varName, value);
    }
    
    private void executePrint(String line) {
        String content = line.substring(6, line.length() - 1);
        Object value = evaluateExpression(content);
        printOutputs.append(value != null ? value.toString() : "null").append("\n");
    }
    
    private Object executeToolCall(String line) {
        Pattern toolCallPattern = Pattern.compile("tool_([a-zA-Z_]+)\\s*\\((.*?)\\)");
        Matcher matcher = toolCallPattern.matcher(line);
        
        if (matcher.find()) {
            String toolName = matcher.group(1);
            String args = matcher.group(2);
            
            Tool tool = tools.get(toolName);
            if (tool == null) {
                throw new RuntimeException("Tool not found: " + toolName);
            }
            
            Map<String, Object> arguments = parseArguments(args);
            return tool.call(arguments);
        }
        return null;
    }
    
    private Object evaluateExpression(String expr) {
        if (expr == null || expr.isEmpty()) return null;
        
        expr = expr.trim();
        
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr.substring(1, expr.length() - 1);
        }
        
        if (expr.startsWith("'") && expr.endsWith("'")) {
            return expr.substring(1, expr.length() - 1);
        }
        
        if (state.containsKey(expr)) {
            return state.get(expr);
        }
        
        try {
            return Integer.parseInt(expr);
        } catch (NumberFormatException e) {}
        
        try {
            return Double.parseDouble(expr);
        } catch (NumberFormatException e) {}
        
        if ("True".equals(expr)) return true;
        if ("False".equals(expr)) return false;
        if ("None".equals(expr)) return null;
        
        return expr;
    }
    
    private Map<String, Object> parseArguments(String args) {
        Map<String, Object> arguments = new HashMap<>();
        String[] parts = args.split(",");
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            int colonIndex = part.indexOf(':');
            if (colonIndex > 0) {
                String key = part.substring(0, colonIndex).trim();
                String value = part.substring(colonIndex + 1).trim();
                arguments.put(removeQuotes(key), evaluateExpression(value));
            }
        }
        
        return arguments;
    }
    
    private String removeQuotes(String str) {
        if (str == null) return null;
        if ((str.startsWith("\"") && str.endsWith("\"")) || 
            (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
    
    @Override
    public void cleanup() {
        state.clear();
        tools.clear();
    }
}