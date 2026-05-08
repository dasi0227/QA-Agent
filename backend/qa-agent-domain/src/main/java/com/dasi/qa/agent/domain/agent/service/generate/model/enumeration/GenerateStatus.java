package com.dasi.qa.agent.domain.agent.service.generate.model.enumeration;

/**
 * 整个生成任务的状态：
 * - PENDING：待处理
 * - PROCESSING：正在处理，进入 DAG
 * - SOLVED：处理成功
 * - UNSOLVED：处理失败
 */
public enum GenerateStatus {
    PENDING,
    PROCESSING,
    SOLVED,
    UNSOLVED;

    // 是否是终止状态
    public boolean isTerminated() {
        return this == SOLVED || this == UNSOLVED;
    }
}
