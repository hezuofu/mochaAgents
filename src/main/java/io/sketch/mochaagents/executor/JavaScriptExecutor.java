package io.sketch.mochaagents.executor;

/**
 * JavaScript执行器接口
 */
public interface JavaScriptExecutor extends Executor {
    
    @Override
    default String getLanguage() {
        return "javascript";
    }
}
