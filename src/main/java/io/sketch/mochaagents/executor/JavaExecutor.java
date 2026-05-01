package io.sketch.mochaagents.executor;

/**
 * Java执行器接口
 */
public interface JavaExecutor extends Executor {
    
    @Override
    default String getLanguage() {
        return "java";
    }
}
