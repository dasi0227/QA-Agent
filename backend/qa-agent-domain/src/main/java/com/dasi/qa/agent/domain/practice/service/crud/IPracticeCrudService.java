package com.dasi.qa.agent.domain.practice.service.crud;

import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;

import java.util.List;

public interface IPracticeCrudService {

    PracticeSessionResponse detailPracticeSession(String id);

    List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request);

    PracticeSessionResponse createPracticeSession(PracticeSessionRequest request);

    PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request);

    void deletePracticeSession(String id);

    PracticeSessionItemResponse detailPracticeSessionItem(String id);

    List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request);

    PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request);

    PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request);

    void deletePracticeSessionItem(String id);
}
