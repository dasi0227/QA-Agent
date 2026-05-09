package com.dasi.qa.agent.domain.agent.service.generate.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@AllArgsConstructor
@Slf4j
public enum GeneratePhase {
    UNKNOWN("UNKNOWN", "UnknownAgent", "未识别的执行阶段。"),

    GENERATE("GENERATING", "GenerateAgent", "先执行请求判定，再根据路由结果进入终止分支或完整生成链路。"),
    ROUTE("ROUTING", "RouteAgent", "读取判定结果并路由到终止分支或继续执行生成分支。"),
    DECIDE("DECIDING", "DecideAgent", "识别用户请求是否满足问答集生成场景并给出判定结果。"),
    ABORT("ABORTING", "AbortAgent", "根据拒绝原因生成终止消息并结束当前生成任务。"),
    PLAN("PLANNING", "PlanAgent", "分析资料摘要并输出模块化题量与难度分配计划。"),
    WRITE("WRITING", "WriteAgent", "顺序执行规划、起草、校验修订与总结，生成最终可落库问答集。"),
    DRAFT("DRAFTING", "DraftAgent", "基于检索证据按模块起草结构化问答题目。"),
    VALIDATE("VALIDATING", "ValidateAgent", "评估并修订候选问答，确保质量达标。"),
    EVALUATE("EVALUATING", "EvaluateAgent", "审校题目准确性、完整性与证据边界并输出判定。"),
    AMEND("AMENDING", "AmendAgent", "按审校建议进行最小必要修订并保持题目结构稳定。"),
    SUMMARIZE("SUMMARIZING", "SummarizeAgent", "汇总生成结果与统计信息并输出最终完成说明。"),

    INIT("INITIALIZED", "NullAgent", "生成任务已创建，等待进入执行链路。"),
    COMPLETE("COMPLETED", "NullAgent", "生成链路执行完成并输出结果。"),
    FAIL("FAILED", "NullAgent", "生成链路执行失败并返回失败原因。");

    private final String generateStage;
    private final String agentName;
    private final String agentDesc;

    public static GeneratePhase fromAgentName(String agentName) {
        if (DECIDE.getAgentName().equals(agentName)) {
            return DECIDE;
        }
        if (PLAN.getAgentName().equals(agentName)) {
            return PLAN;
        }
        if (DRAFT.getAgentName().equals(agentName) || WRITE.getAgentName().equals(agentName)) {
            return WRITE;
        }
        if (EVALUATE.getAgentName().equals(agentName)
                || AMEND.getAgentName().equals(agentName)
                || VALIDATE.getAgentName().equals(agentName)) {
            return VALIDATE;
        }
        if (SUMMARIZE.getAgentName().equals(agentName)) {
            return SUMMARIZE;
        }
        log.warn("Unknown agent phase mapping: agentName={}", agentName);
        return UNKNOWN;
    }
}
