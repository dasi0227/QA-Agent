package com.dasi.qa.agent.domain.practice.repository;

import com.dasi.qa.agent.domain.practice.model.vo.PracticeStateVO;
import com.dasi.qa.agent.types.dto.request.practice.ItemSaveRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeInitRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeQueryRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeStateResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeDetailResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;

import java.util.List;

public interface IPracticeRepository {

    PracticeDetailResponse initPractice(PracticeInitRequest request, String sessionId, List<String> sessionItemIds, String userId);

    int countPracticeItems(PracticeInitRequest request, String userId);

    PracticeStateResponse existPractice(String qaSetId, String userId);

    PracticeDetailResponse detailPractice(String sessionId, String userId);

    PracticeStateVO getPracticeState(String sessionId, String userId);

    PracticeItemResponse savePracticeAnswer(ItemSaveRequest request, String userId);

    PracticeItemResponse markUnknownOnly(ItemSaveRequest request, String userId);

    PracticeItemResponse refreshPracticeItemProgress(String sessionId, String sessionItemId, Integer currentIndex, Integer durationSeconds, String userId);

    void abandonActivePractice(String qaSetId, String userId);

    PracticeDetailResponse abandonPractice(String sessionId, Integer durationSeconds, String userId);

    boolean isPracticeSessionReadyForItemByItemAssess(String sessionId, String userId);

    boolean isPracticeSessionReadyForAfterAllAssess(String sessionId, String userId);

    List<PracticeItemResponse> queryPracticeItemsForFeedback(String sessionId, String userId);

    List<PracticeSessionResponse> queryPracticeHistory(String qaSetId, String userId);

    List<PracticeSessionResponse> queryPracticeSession(PracticeQueryRequest request, String userId);
}
