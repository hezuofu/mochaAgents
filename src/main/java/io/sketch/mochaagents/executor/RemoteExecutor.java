package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.monitoring.AgentLogger;

import java.util.List;

/**
 * 远程执行器抽象接口
 */
public interface RemoteExecutor extends Executor {
    
    String FINAL_ANSWER_EXCEPTION = "FinalAnswerException";
    
    /**
     * 安装额外的Python包
     */
    List<String> installPackages(List<String> packages);
    
    /**
     * 获取已安装的包列表
     */
    List<String> getInstalledPackages();
    
    /**
     * 设置日志记录器
     */
    void setLogger(AgentLogger logger);
    
    /**
     * 获取日志记录器
     */
    AgentLogger getLogger();
}
