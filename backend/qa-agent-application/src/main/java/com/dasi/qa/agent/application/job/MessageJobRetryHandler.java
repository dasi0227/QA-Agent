package com.dasi.qa.agent.application.job;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJobEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.enums.JobStatus;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MessageJobRetryHandler {

    private static final int MAX_RETRY = 3;

    private final MessageJobMapper messageJobMapper;
    private final IMqUtil mqUtil;

    public MessageJobRetryHandler(MessageJobMapper messageJobMapper, IMqUtil mqUtil) {
        this.messageJobMapper = messageJobMapper;
        this.mqUtil = mqUtil;
    }

    @XxlJob("messageJobRetryHandler")
    public void execute() {
        log.info("MessageJobRetryHandler started");
        QueryWrapper<MessageJobEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("job_status", JobStatus.UNSOLVED.name());
        List<MessageJobEntity> unsolvedJobs = messageJobMapper.selectList(wrapper);

        if (unsolvedJobs.isEmpty()) {
            log.info("No UNSOLVED message jobs found");
            return;
        }

        log.info("Found {} UNSOLVED message jobs", unsolvedJobs.size());
        for (MessageJobEntity job : unsolvedJobs) {
            if (job.getJobRetry() < MAX_RETRY) {
                log.info("Retrying job: jobId={}, retry={}/{}", job.getJobId(), job.getJobRetry() + 1, MAX_RETRY);
                mqUtil.send(job.getMessageTopic(), job.getJobId(), job.getMessageContent());
            } else {
                log.warn("Job exceeded max retries, sending to DLQ: jobId={}, retry={}",
                        job.getJobId(), job.getJobRetry());
                String dlqTopic = job.getMessageTopic() + ".dlq";
                mqUtil.send(dlqTopic, job.getJobId() + "_dlq", job.getMessageContent());
                mqUtil.markFail(job.getJobId());
            }
        }
    }
}
