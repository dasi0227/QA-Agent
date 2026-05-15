package com.dasi.qa.agent.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dasi.qa.agent.domain.message.model.UnsolvedJob;
import com.dasi.qa.agent.domain.message.repository.IMessageRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.MessageJob;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.MessageJobMapper;
import com.dasi.qa.agent.types.enumeration.JobStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository implements IMessageRepository {

    private final MessageJobMapper messageJobMapper;

    public MessageRepository(MessageJobMapper messageJobMapper) {
        this.messageJobMapper = messageJobMapper;
    }

    @Override
    public List<UnsolvedJob> listUnsolvedJobs() {
        LambdaQueryWrapper<MessageJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageJob::getJobStatus, JobStatus.UNSOLVED.name());
        return messageJobMapper.selectList(wrapper).stream()
                .map(job -> UnsolvedJob.builder()
                        .jobId(job.getJobId())
                        .retry(job.getJobRetry())
                        .topic(job.getMessageTopic())
                        .content(job.getMessageContent())
                        .build())
                .toList();
    }
}
