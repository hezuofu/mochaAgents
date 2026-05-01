package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.tools.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 远程Python代码执行器基类
 */
public abstract class RemotePythonExecutor implements RemoteExecutor {
    
    protected final List<String> additionalImports;
    protected final AgentLogger logger;
    protected final boolean allowPickle;
    protected final List<String> installedPackages = new ArrayList<>();
    
    public RemotePythonExecutor(List<String> additionalImports, AgentLogger logger) {
        this(additionalImports, logger, false);
    }
    
    public RemotePythonExecutor(List<String> additionalImports, AgentLogger logger, boolean allowPickle) {
        this.additionalImports = additionalImports;
        this.logger = logger;
        this.allowPickle = allowPickle;
        logger.log("Initializing executor, hold on...");
    }
    
    @Override
    public void sendTools(Map<String, Tool> tools) {
        if (tools.containsKey("final_answer")) {
            logger.log("Final answer tool registered");
        }
        
        List<String> packagesToInstall = new ArrayList<>();
        for (Tool tool : tools.values()) {
            List<String> requirements = new ArrayList<>();
            for (String pkg : requirements) {
                if (!installedPackages.contains(pkg) && !pkg.equals("smolagents")) {
                    if (pkg.equals("PIL")) {
                        packagesToInstall.add("pillow");
                    } else {
                        packagesToInstall.add(pkg);
                    }
                }
            }
        }
        
        if (!packagesToInstall.isEmpty()) {
            installedPackages.addAll(installPackages(packagesToInstall));
        }
    }
    
    @Override
    public void sendVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        
        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("import json\n");
        codeBuilder.append("vars_dict = ").append(serializeVariables(variables)).append("\n");
        codeBuilder.append("locals().update(vars_dict)\n");
        
        execute(codeBuilder.toString());
    }
    
    @Override
    public void cleanup() {
        logger.log("Cleaning up remote executor...");
    }
    
    @Override
    public String getLanguage() {
        return "python";
    }
    
    @Override
    public List<String> installPackages(List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return packages;
        }
        
        String code = String.format("!pip install %s", String.join(" ", packages));
        CodeOutput output = execute(code);
        logger.log(output.logs());
        return packages;
    }
    
    @Override
    public List<String> getInstalledPackages() {
        return new ArrayList<>(installedPackages);
    }
    
    @Override
    public void setLogger(AgentLogger logger) {
        // Logger is immutable in this implementation
    }
    
    @Override
    public AgentLogger getLogger() {
        return logger;
    }
    
    protected String serializeVariables(Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ");
            sb.append(serializeValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(", ");
                sb.append(serializeValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\": ");
                sb.append(serializeValue(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else {
            return "\"" + escapeString(value.toString()) + "\"";
        }
    }
    
    private String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    protected Object deserializeFinalAnswer(String encodedValue) {
        if (encodedValue.startsWith("safe:")) {
            return encodedValue.substring(5);
        } else if (encodedValue.startsWith("pickle:")) {
            if (!allowPickle) {
                throw new SecurityException("Pickle data rejected: allow_pickle=False");
            }
            throw new UnsupportedOperationException("Pickle deserialization not supported");
        } else {
            throw new IllegalArgumentException("Unknown final answer format: expected 'safe:' or 'pickle:' prefix");
        }
    }
    
    protected Object parseJson(String json) {
        return json;
    }
}
