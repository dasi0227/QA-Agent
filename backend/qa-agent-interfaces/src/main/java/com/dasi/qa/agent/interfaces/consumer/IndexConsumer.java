package com.dasi.qa.agent.interfaces.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.document.model.IndexStatus;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.index.IIndexService;
import com.dasi.qa.agent.domain.util.IMqUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.dasi.qa.agent.types.constant.StringConstant.INDEX_JOB_ID_PREFIX;

@Component
@Slf4j
public class IndexConsumer {


    private final IIndexService indexService;
    private final IMqUtil mqUtil;
    private final IDocumentRepository documentRepository;

    public IndexConsumer(IIndexService indexService, IMqUtil mqUtil, IDocumentRepository documentRepository) {
        this.indexService = indexService;
        this.mqUtil = mqUtil;
        this.documentRepository = documentRepository;
    }

    @KafkaListener(topics = "${qa-agent.kafka.topic-document-index}", groupId = "${spring.kafka.consumer.group-id}")
    public void onDocumentIndexing(String message) {
        String documentId = null;
        String userId = null;
        String jobId = null;
        try {
            JSONObject jsonObject = JSON.parseObject(message);
            documentId = jsonObject.getString("documentId");
            userId = jsonObject.getString("userId");
            jobId = INDEX_JOB_ID_PREFIX + documentId;

            log.info("【消息队列消费者】收到资料索引任务: documentId={}, jobId={}", documentId, jobId);
            indexService.index(documentId, userId);
            mqUtil.markSuccess(jobId);
            documentRepository.updateIndexStatus(documentId, userId, IndexStatus.FINISHED.name());
        } catch (Exception e) {
            log.error("【消息队列消费者】资料索引任务执行失败: documentId={}, jobId={}", documentId, jobId, e);
            if (jobId != null) {
                mqUtil.recordError(jobId, e.getMessage());
            }
            if (documentId != null) {
                documentRepository.updateIndexStatus(documentId, userId, IndexStatus.UNSOLVED.name());
            }
        }
    }
}
