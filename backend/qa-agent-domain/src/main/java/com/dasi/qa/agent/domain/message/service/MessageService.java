package com.dasi.qa.agent.domain.message.service;

import com.dasi.qa.agent.domain.message.model.UnsolvedJob;
import com.dasi.qa.agent.domain.message.repository.IMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService implements IMessageService {

    private final IMessageRepository repository;

    public MessageService(IMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UnsolvedJob> listUnsolvedJobs() {
        return repository.listUnsolvedJobs();
    }
}
