package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.monitoring.AgentLogger;
import io.sketch.mochaagents.serialization.SafeSerializer;
import io.sketch.mochaagents.serialization.SerializationError;
import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolDefinition;

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
    public abstract CodeOutput execute(String codeAction);

    @Override
    public void sendTools(Map<String, Tool> tools) {
        if (tools.containsKey("final_answer")) {
            logger.log("Final answer tool registered");
        }

        List<String> packagesToInstall = new ArrayList<>();
        for (Tool tool : tools.values()) {
            ToolDefinition td = tool.toDefinition();
            if (td == null || td.requirements() == null) {
                continue;
            }
            for (String pkg : td.requirements()) {
                if (pkg != null && !installedPackages.contains(pkg) && !"smolagents".equals(pkg)) {
                    if ("PIL".equals(pkg)) {
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
        try {
            String block = SafeSerializer.pythonAssignVarsFromSafeJson(variables, "") + "locals().update(vars_dict)\n";
            execute(block);
        } catch (SerializationError e) {
            throw new IllegalArgumentException(
                "Cannot serialize agent state for remote kernel (safe JSON only): " + e.getMessage(),
                e);
        }
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

    protected Object deserializeFinalAnswer(String encodedValue) {
        if (encodedValue.startsWith(SafeSerializer.SAFE_PREFIX)) {
            try {
                return SafeSerializer.loads(encodedValue);
            } catch (SerializationError e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        } else if (encodedValue.startsWith("pickle:")) {
            if (!allowPickle) {
                throw new SecurityException("Pickle data rejected: allow_pickle=False");
            }
            throw new UnsupportedOperationException(
                "Pickle deserialization is not supported on the JVM; use safe: JSON payloads only.");
        } else {
            throw new IllegalArgumentException("Unknown final answer format: expected 'safe:' or 'pickle:' prefix");
        }
    }

    protected Object parseJson(String json) {
        return json;
    }
}
