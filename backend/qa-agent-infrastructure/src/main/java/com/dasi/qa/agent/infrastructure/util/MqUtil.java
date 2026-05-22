package com.dasi.qa.agent.infrastructure.util;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJob;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.constant.StringConstant;
import com.dasi.qa.agent.types.enumeration.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class MqUtil implements IMqUtil {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessageJobMapper messageJobMapper;
    private final IIdUtil idUtil;
    private final String indexTopic;
    private final String assistTopic;

    public MqUtil(KafkaTemplate<String, String> kafkaTemplate,
                  MessageJobMapper messageJobMapper,
                  IIdUtil idUtil,
                  @Value("${qa-agent.kafka.topic-document-index}") String indexTopic,
                  @Value("${qa-agent.kafka.topic-qa-item-assist}") String assistTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageJobMapper = messageJobMapper;
        this.idUtil = idUtil;
        this.indexTopic = indexTopic;
        this.assistTopic = assistTopic;
    }


    @Override
    public void sendIndexMessage(String id, Object content) {
        send(indexTopic, StringConstant.INDEX_JOB_ID_PREFIX + id, JSON.toJSONString(content));
    }

    @Override
    public void sendAssistMessage(String id, Object content) {
        send(assistTopic, StringConstant.ASSIST_JOB_ID_PREFIX + id, JSON.toJSONString(content));
    }

    @Override
    public void send(String topic, String jobId, String content) {
        MessageJob existing = findExistingJob(jobId);

        // 重发
        if (existing != null) {
            existing.setJobRetry(existing.getJobRetry() + 1);
            existing.setJobStatus(JobStatus.UNSOLVED.name());
            existing.setErrorMessage(null);
            existing.setMessageLatestSentAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
        }
        // 第一次发
        else {
            MessageJob job = MessageJob.builder()
                    .id(idUtil.nextId())
                    .jobId(jobId)
                    .jobStatus(JobStatus.UNSOLVED.name())
                    .jobRetry(0)
                    .messageTopic(topic)
                    .messageContent(content)
                    .messageFirstSentAt(LocalDateTime.now())
                    .messageLatestSentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            messageJobMapper.insert(job);
        }

        kafkaTemplate.send(topic, content);
        log.info("【消息队列生产者】消息发送: topic={}, jobId={}", topic, jobId);
    }

    @Override
    public void sendDeadLetter(String topic, String content) {
        kafkaTemplate.send(topic, content);
        log.info("【消息队列生产者】死信消息发送: topic={}", topic);
    }

    @Override
    public void markSuccess(String jobId) {
        MessageJob existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.SUCCESS.name());
            existing.setErrorMessage(null);
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
            log.info("【消息队列生产者】任务标记成功: jobId={}", jobId);
        }
    }

    @Override
    public void markFail(String jobId) {
        MessageJob existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.FAIL.name());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
            log.info("【消息队列生产者】任务标记失败: jobId={}", jobId);
        }
    }

    @Override
    public void recordError(String jobId, String errorMessage) {
        MessageJob existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.UNSOLVED.name());
            existing.setErrorMessage(errorMessage);
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
            log.info("【消息队列生产者】任务记录错误: jobId={}", jobId);
        }
    }

    private MessageJob findExistingJob(String jobId) {
        LambdaQueryWrapper<MessageJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageJob::getJobId, jobId);
        return messageJobMapper.selectOne(wrapper);
    }
}
