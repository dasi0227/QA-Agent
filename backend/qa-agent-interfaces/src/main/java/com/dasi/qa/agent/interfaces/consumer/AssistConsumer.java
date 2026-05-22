package com.dasi.qa.agent.interfaces.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.agent.service.assist.IAssistAgent;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.constant.StringConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AssistConsumer {

    private final IAssistAgent assistAgent;
    private final IMqUtil mqUtil;

    public AssistConsumer(IAssistAgent assistAgent, IMqUtil mqUtil) {
        this.assistAgent = assistAgent;
        this.mqUtil = mqUtil;
    }

    @KafkaListener(topics = "${qa-agent.kafka.topic-qa-item-assist}", groupId = "${spring.kafka.consumer.group-id}")
    public void onQaItemAssist(String message) {
        String qaItemId = null;
        String jobId = null;
        try {
            JSONObject jsonObject = JSON.parseObject(message);
            qaItemId = jsonObject.getString("qaItemId");
            String userId = jsonObject.getString("userId");
            jobId = StringConstant.ASSIST_JOB_ID_PREFIX + qaItemId;

            log.info("【消息队列消费者】收到题目辅助补全补全任务: qaItemId={}, jobId={}", qaItemId, jobId);
            assistAgent.execute(qaItemId, userId);
            mqUtil.markSuccess(jobId);
        } catch (Exception exception) {
            log.error("【消息队列消费者】题目辅助补全补全失败: qaItemId={}, jobId={}", qaItemId, jobId, exception);
            if (jobId != null) {
                mqUtil.recordError(jobId, exception.getMessage());
            }
        }
    }
}
