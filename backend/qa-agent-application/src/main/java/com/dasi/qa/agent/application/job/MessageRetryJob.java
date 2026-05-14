package com.dasi.qa.agent.application.job;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJob;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.enumeration.JobStatus;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MessageRetryJob {

    private static final int MAX_RETRY = 3;

    private final MessageJobMapper messageJobMapper;
    private final IMqUtil mqUtil;

    public MessageRetryJob(MessageJobMapper messageJobMapper, IMqUtil mqUtil) {
        this.messageJobMapper = messageJobMapper;
        this.mqUtil = mqUtil;
    }

    @XxlJob("messageJobRetryHandler")
    public void execute() {
        log.info("【定时任务】消息重试任务启动");
        LambdaQueryWrapper<MessageJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageJob::getJobStatus, JobStatus.UNSOLVED.name());
        List<MessageJob> unsolvedJobs = messageJobMapper.selectList(wrapper);

        if (unsolvedJobs.isEmpty()) {
            log.info("【定时任务】无待重试消息");
            return;
        }

        log.info("【定时任务】发现待重试消息: count={}", unsolvedJobs.size());
        for (MessageJob job : unsolvedJobs) {
            if (job.getJobRetry() < MAX_RETRY) {
                log.info("【定时任务】重试消息发送: jobId={}, retry={}/{}", job.getJobId(), job.getJobRetry() + 1, MAX_RETRY);
                mqUtil.send(job.getMessageTopic(), job.getJobId(), job.getMessageContent());
            } else {
                log.warn("【定时任务】消息超出最大重试次数，移入死信: jobId={}, retry={}", job.getJobId(), job.getJobRetry());
                String dlqTopic = job.getMessageTopic() + ".dlq";
                mqUtil.send(dlqTopic, job.getJobId() + "_dlq", job.getMessageContent());
                mqUtil.markFail(job.getJobId());
            }
        }
    }
}
