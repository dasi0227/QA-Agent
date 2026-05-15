package com.dasi.qa.agent.domain.message.service;

import com.dasi.qa.agent.domain.message.model.UnsolvedJob;

import java.util.List;

public interface IMessageService {

    List<UnsolvedJob> listUnsolvedJobs();
}
