package com.dasi.qa.agent.domain.practice.service.crud;

import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.practice.PracticeQueryRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PracticeCrudService implements IPracticeCrudService {

    private final IPracticeRepository repository;
    private final IContextUtil contextUtil;

    public PracticeCrudService(IPracticeRepository repository, IContextUtil contextUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
    }

    @Override
    public List<PracticeSessionResponse> query(PracticeQueryRequest request) {
        return repository.queryPracticeSession(request, contextUtil.getUserId());
    }
}
