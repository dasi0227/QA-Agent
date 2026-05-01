package com.dasi.qa.agent.domain.practice.repository;

import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;

import java.util.List;

public interface IPracticeRepository {

    PracticeSessionResponse detailPracticeSession(String id, String userId);

    List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request, String userId);

    PracticeSessionResponse createPracticeSession(PracticeSessionRequest request, String userId);

    PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request, String userId);

    void deletePracticeSession(String id, String userId);

    PracticeSessionItemResponse detailPracticeSessionItem(String id, String userId);

    List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request, String userId);

    PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request, String userId);

    PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request, String userId);

    void deletePracticeSessionItem(String id, String userId);
}
