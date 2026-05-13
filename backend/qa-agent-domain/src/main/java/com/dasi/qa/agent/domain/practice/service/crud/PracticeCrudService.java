package com.dasi.qa.agent.domain.practice.service.crud;

import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PracticeCrudService implements IPracticeCrudService {

    private final IPracticeRepository repository;
    private final IContextUtil contextUtil;

    public PracticeCrudService(IPracticeRepository repository, IContextUtil contextUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
    }

    @Override
    public PracticeSessionResponse detailPracticeSession(String id) {
        return repository.detailPracticeSession(id, currentUserId());
    }

    @Override
    public List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request) {
        return repository.queryPracticeSession(request, currentUserId());
    }

    @Override
    public PracticeSessionResponse createPracticeSession(PracticeSessionRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        return repository.createPracticeSession(request, currentUserId());
    }

    @Override
    public PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request) {
        return repository.updatePracticeSession(request, currentUserId());
    }

    @Override
    public void deletePracticeSession(String id) {
        repository.deletePracticeSession(id, currentUserId());
    }

    @Override
    public PracticeSessionItemResponse detailPracticeSessionItem(String id) {
        return repository.detailPracticeSessionItem(id, currentUserId());
    }

    @Override
    public List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request) {
        return repository.queryPracticeSessionItem(request, currentUserId());
    }

    @Override
    public PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        return repository.createPracticeSessionItem(request, currentUserId());
    }

    @Override
    public PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request) {
        return repository.updatePracticeSessionItem(request, currentUserId());
    }

    @Override
    public void deletePracticeSessionItem(String id) {
        repository.deletePracticeSessionItem(id, currentUserId());
    }

    private String currentUserId() {
        String userId = contextUtil.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
