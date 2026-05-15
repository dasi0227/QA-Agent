package com.dasi.qa.agent.domain.message.repository;

import com.dasi.qa.agent.domain.message.model.UnsolvedJob;

import java.util.List;

public interface IMessageRepository {

    List<UnsolvedJob> listUnsolvedJobs();
}
