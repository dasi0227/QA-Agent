package com.dasi.qa.agent.infrastructure.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJob;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.enumeration.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class MqUtil implements IMqUtil {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessageJobMapper messageJobMapper;

    public MqUtil(KafkaTemplate<String, String> kafkaTemplate,
                  MessageJobMapper messageJobMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageJobMapper = messageJobMapper;
    }

    @Override
    public void send(String topic, String jobId, String content) {
        // check existing job
        MessageJob existing = findExistingJob(jobId);
        if (existing != null) {
            // retry: update existing
            existing.setJobRetry(existing.getJobRetry() + 1);
            existing.setJobStatus(JobStatus.UNSOLVED.name());
            existing.setMessageLatestSentAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
        } else {
            // first send: insert
            MessageJob job = MessageJob.builder()
                    .id(UUID.randomUUID().toString())
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
    public void markSuccess(String jobId) {
        MessageJob existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.SUCCESS.name());
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

    private MessageJob findExistingJob(String jobId) {
        LambdaQueryWrapper<MessageJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageJob::getJobId, jobId);
        return messageJobMapper.selectOne(wrapper);
    }
}
