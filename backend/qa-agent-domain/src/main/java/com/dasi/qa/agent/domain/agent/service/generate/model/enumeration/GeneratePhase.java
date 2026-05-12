package com.dasi.qa.agent.domain.agent.service.generate.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@AllArgsConstructor
@Slf4j
public enum GeneratePhase {
    UNKNOWN("❓ 未知情况", null, "未识别的执行阶段。", null),

    GENERATE("⚙️ 生成执行", "GenerateAgent", "先执行请求判定，再根据路由结果进入终止分支或完整生成链路。", null),
    ROUTE("🧭 路由分发", "RouteAgent", "读取判定结果并路由到终止分支或继续执行生成分支。", null),
    DECIDE("🤔 请求判定", "DecideAgent", "识别用户请求是否满足问答集生成场景并给出判定结果。", "decideResult"),
    PLAN("☑️ 规划模块", "PlanAgent", "分析资料摘要并输出模块化题量与难度分配计划。", "planResult"),
    WRITE("📝 题目编写", "WriteAgent", "顺序执行规划、起草、校验修订与总结，生成最终可落库问答集。", "draftResult"),
    DRAFT("✍️ 检索起草", "DraftAgent", "基于检索证据按模块起草结构化问答题目。", null),
    VALIDATE("🧐 审校修订", "ValidateAgent", "评估并修订候选问答，确保质量达标。", "validateResult"),
    EVALUATE("🔍 内容审校", "EvaluateAgent", "审校题目准确性、完整性与证据边界并输出判定。", null),
    AMEND("🔧 修订完善", "AmendAgent", "按审校建议进行最小必要修订并保持题目结构稳定。", null),
    SUMMARIZE("📈 结果汇总", "SummarizeAgent", "汇总生成结果与统计信息并输出最终完成说明。", null),

    INIT("🚀 任务启动", null, "生成任务已创建，等待进入执行链路。", null),
    ABORT("🗑️ 任务终止", "AbortAgent", "根据拒绝原因生成终止消息并结束当前生成任务。", null),
    COMPLETE("🎉 任务完成", null, "生成链路执行完成并输出结果。", null),
    FAIL("💣 任务失败", null, "生成链路执行失败并返回失败原因。", null);

    private final String generateStage;
    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;

}