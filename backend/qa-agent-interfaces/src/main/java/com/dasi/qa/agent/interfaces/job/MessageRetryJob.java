package com.dasi.qa.agent.interfaces.job;

import com.dasi.qa.agent.domain.message.model.UnsolvedJob;
import com.dasi.qa.agent.domain.message.service.IMessageService;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageRetryJob {

    private static final int MAX_RETRY = 3;

    private final IMessageService messageService;
    private final IMqUtil mqUtil;

    public MessageRetryJob(IMessageService messageService, IMqUtil mqUtil) {
        this.messageService = messageService;
        this.mqUtil = mqUtil;
    }

    @XxlJob("messageJobRetryHandler")
    public void execute() {
        List<UnsolvedJob> unsolvedJobs = messageService.listUnsolvedJobs();
        if (unsolvedJobs.isEmpty()) {
            return;
        }
        for (UnsolvedJob job : unsolvedJobs) {
            if (job.getRetry() < MAX_RETRY) {
                mqUtil.send(job.getTopic(), job.getJobId(), job.getContent());
            } else {
                String dlqTopic = job.getTopic() + ".dlq";
                mqUtil.sendDeadLetter(dlqTopic, job.getContent());
                mqUtil.markFail(job.getJobId());
            }
        }
    }
}
