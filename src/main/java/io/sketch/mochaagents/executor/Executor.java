package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.tools.Tool;

import java.util.Map;

/**
 * 执行器通用接口
 */
public interface Executor {
    
    /**
     * 发送工具到执行器
     */
    void sendTools(Map<String, Tool> tools);
    
    /**
     * 发送变量到执行器
     */
    void sendVariables(Map<String, Object> variables);
    
    /**
     * 执行代码
     */
    CodeOutput execute(String codeAction);
    
    /**
     * 清理资源
     */
    default void cleanup() {}
    
    /**
     * 获取支持的语言
     */
    String getLanguage();
}
