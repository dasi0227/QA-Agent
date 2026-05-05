package com.dasi.qa.agent.infrastructure.util;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJobEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.enums.JobStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
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
        MessageJobEntity existing = findExistingJob(jobId);
        if (existing != null) {
            // retry: update existing
            existing.setJobRetry(existing.getJobRetry() + 1);
            existing.setJobStatus(JobStatus.UNSOLVED.name());
            existing.setMessageLatestSentAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
        } else {
            // first send: insert
            MessageJobEntity job = MessageJobEntity.builder()
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
        log.info("MQ message sent: topic={}, jobId={}", topic, jobId);
    }

    @Override
    public void markSuccess(String jobId) {
        MessageJobEntity existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.SUCCESS.name());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
            log.info("MQ job marked SUCCESS: jobId={}", jobId);
        }
    }

    @Override
    public void markFail(String jobId) {
        MessageJobEntity existing = findExistingJob(jobId);
        if (existing != null) {
            existing.setJobStatus(JobStatus.FAIL.name());
            existing.setUpdatedAt(LocalDateTime.now());
            messageJobMapper.updateById(existing);
            log.info("MQ job marked FAIL: jobId={}", jobId);
        }
    }

    private MessageJobEntity findExistingJob(String jobId) {
        QueryWrapper<MessageJobEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("job_id", jobId);
        return messageJobMapper.selectOne(wrapper);
    }
}
