package com.dasi.qa.agent.interfaces.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.memory.service.IMemoryService;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.constant.StringConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MemoryConsumer {

    private final IMemoryService memoryService;
    private final IMqUtil mqUtil;

    public MemoryConsumer(IMemoryService memoryService, IMqUtil mqUtil) {
        this.memoryService = memoryService;
        this.mqUtil = mqUtil;
    }

    @KafkaListener(topics = "${qa-agent.kafka.topic-memory-ingest}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMemoryIngest(String message) {
        String sessionId = null;
        String jobId = null;
        try {
            JSONObject jsonObject = JSON.parseObject(message);
            sessionId = jsonObject.getString("sessionId");
            String userId = jsonObject.getString("userId");
            jobId = StringConstant.MEMORY_JOB_ID_PREFIX + sessionId;

            log.info("【消息队列消费者】收到记忆画像沉淀任务: sessionId={}, jobId={}", sessionId, jobId);
            memoryService.ingest(sessionId, userId);
            mqUtil.markSuccess(jobId);
        } catch (Exception exception) {
            log.error("【消息队列消费者】记忆画像沉淀失败: sessionId={}, jobId={}", sessionId, jobId, exception);
            if (jobId != null) {
                mqUtil.recordError(jobId, exception.getMessage());
            }
        }
    }
}
