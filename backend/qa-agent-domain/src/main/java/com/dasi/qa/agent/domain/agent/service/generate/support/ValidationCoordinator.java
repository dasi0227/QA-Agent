package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.AmendItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.EvaluateItem;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ValidationCoordinator {

    private static final String V_PASS = "PASS";
    private static final String V_AMEND = "AMEND";

    private final int batchSize;
    private final IJsonUtil jsonUtil;
    private final Executor executor;

    public ValidationCoordinator(int batchSize, IJsonUtil jsonUtil, Executor executor) {
        this.batchSize = batchSize;
        this.jsonUtil = jsonUtil;
        this.executor = executor;
    }

    public List<DraftItem> doValidate(String taskId, EvaluateAgent evaluateAgent, AmendAgent amendAgent, List<DraftItem> drafts) {
        // 1. 获取不同批次集合
        List<List<DraftItem>> batchList = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i += batchSize) {
            batchList.add(drafts.subList(i, Math.min(i + batchSize, drafts.size())));
        }

        // 2. 创建每个批次的异步任务
        List<CompletableFuture<List<DraftItem>>> futureList = batchList.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> doValidateLoop(taskId, evaluateAgent, amendAgent, batch), executor))
                .toList();

        // 3. 等待所有批次的异步任务执行完成并汇总结果
        return futureList.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    //
    private List<DraftItem> doValidateLoop(String taskId, EvaluateAgent evaluateAgent, AmendAgent amendAgent, List<DraftItem> batch) {
        // 通过集
        List<DraftItem> passItems = new ArrayList<>();

        // 当前审批集
        AtomicReference<List<DraftItem>> evaluateItems = new AtomicReference<>(batch);

        // 当前修改集
        AtomicReference<List<AmendItem>> amendItems = new AtomicReference<>(List.of());

        // 当前审批结果
        AtomicBoolean flag = new AtomicBoolean(true);

        // 构造 ValidateDAG
        UntypedAgent validateAgent = AgenticServices.loopBuilder()
                .name(GeneratePhase.VALIDATE.getAgentName())
                .description(GeneratePhase.VALIDATE.getAgentDesc())
                .maxIterations(2)
                .exitCondition((scope, iteration) -> flag.get())
                .subAgents(
                        // 校验
                        AgenticServices.agentAction(scope -> {
                            List<EvaluateItem> evaluates = doEvaluate(taskId, evaluateAgent, evaluateItems.get());

                            List<AmendItem> amends = new ArrayList<>();
                            for (int i = 0; i < Math.min(evaluateItems.get().size(), evaluates.size()); i++) {
                                if (V_PASS.equals(evaluates.get(i).getVerdict())) {
                                    passItems.add(evaluateItems.get().get(i));
                                } else {
                                    AmendItem amendItem = AmendItem.builder()
                                            .draftItem(evaluateItems.get().get(i))
                                            .reason(evaluates.get(i).getReason())
                                            .suggestion(evaluates.get(i).getSuggestion())
                                            .build();
                                    amends.add(amendItem);
                                }
                            }

                            amendItems.set(amends);
                        }),

                        // 修改
                        AgenticServices.agentAction(scope -> {
                            if (amendItems.get().isEmpty()) {
                                flag.set(true);
                                return;
                            }
                            List<EvaluateItem> evaluates = doDraft(taskId, amendItems.get(), "");

                            String response = amendAgent.amend(taskId, jsonUtil.toJsonString(amendItems), "");
                            evaluateItems.set(evaluates);
                        })
                )
                .output(scope -> evaluateItems.get())
                .build();

        // 调用智能体
        try {
            validateAgent.invoke(Map.of());
        } catch (Exception exception) {
            log.warn("【GenerateAgent - DraftAgent】调用智能体出错: taskId={}, error={}", taskId, exception.getMessage());
        }

        return passItems;
    }

    private List<EvaluateItem> doEvaluate(String taskId, EvaluateAgent evaluateAgent, List<DraftItem> drafts) {
        try {
            String response = evaluateAgent.evaluate(taskId, jsonUtil.toJsonString(drafts));
            return jsonUtil.parseJsonArray(response, EvaluateItem.class);
        } catch (Exception exception) {
            log.warn("【GenerateAgent - EvaluateAgent】调用智能体出错: taskId={}, error={}", taskId, exception.getMessage());
            return fallbackDraft(drafts);
        }
    }

    private List<EvaluateItem> doDraft(String taskId, List<AmendItem> drafts, String userPrompt) {
        try {
            String response = amendAgent.amend(taskId, jsonUtil.toJsonString(drafts), userPrompt);
            return jsonUtil.parseJsonArray(response, EvaluateItem.class);
        } catch (Exception exception) {
            log.warn("【GenerateAgent - EvaluateAgent】调用智能体出错: taskId={}, error={}", taskId, exception.getMessage());
            return fallbackDraft();
        }
    }


    private List<EvaluateItem> fallbackDraft(List<DraftItem> drafts) {
        List<EvaluateItem> results = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            results.add(new EvaluateItem(V_PASS, "fallback pass", ""));
        }
        return results;
    }

}
