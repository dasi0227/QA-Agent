package com.dasi.qa.agent.interfaces.consumer;

import lombok.extern.slf4j.Slf4j;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.document.service.rag.index.IIndexService;
import com.dasi.qa.agent.domain.util.IMqUtil;
import static com.dasi.qa.agent.types.constant.StringConstant.INDEX_JOB_ID_PREFIX;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IndexConsumer {


    private final IIndexService indexService;
    private final IMqUtil mqUtil;

    public IndexConsumer(IIndexService indexService, IMqUtil mqUtil) {
        this.indexService = indexService;
        this.mqUtil = mqUtil;
    }

    @KafkaListener(topics = "${qa-agent.kafka.topic-document-index}", groupId = "${spring.kafka.consumer.group-id}")
    public void onDocumentIndexing(String message) {
        String documentId = null;
        String jobId = null;
        try {
            JSONObject json = JSON.parseObject(message);
            documentId = json.getString("documentId");
            jobId = INDEX_JOB_ID_PREFIX + documentId;

            log.info("【消息队列消费者】收到资料索引任务: documentId={}, jobId={}", documentId, jobId);
            indexService.index(documentId);
            mqUtil.markSuccess(jobId);
        } catch (Exception e) {
            log.error("【消息队列消费者】资料索引任务执行失败: documentId={}, jobId={}", documentId, jobId, e);
            if (jobId != null) {
                mqUtil.markFail(jobId);
            }
        }
    }
}
