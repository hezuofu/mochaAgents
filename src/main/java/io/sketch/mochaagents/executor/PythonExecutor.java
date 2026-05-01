package io.sketch.mochaagents.executor;

public interface PythonExecutor extends Executor {
    
    @Override
    default String getLanguage() {
        return "python";
    }
}
