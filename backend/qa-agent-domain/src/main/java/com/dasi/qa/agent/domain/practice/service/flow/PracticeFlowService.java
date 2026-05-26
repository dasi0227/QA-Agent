package com.dasi.qa.agent.domain.practice.service.flow;

import com.dasi.qa.agent.domain.agent.service.assess.IAssessAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.IFeedbackAgent;
import com.dasi.qa.agent.domain.practice.model.enumeration.PracticeFeedbackMode;
import com.dasi.qa.agent.domain.practice.model.vo.PracticeStateVO;
import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.types.dto.request.practice.PracticeAbandonRequest;
import com.dasi.qa.agent.types.dto.request.practice.AssessRequest;
import com.dasi.qa.agent.types.dto.request.practice.FeedbackRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeRestartRequest;
import com.dasi.qa.agent.types.dto.request.practice.ItemSaveRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeInitRequest;
import com.dasi.qa.agent.types.dto.request.practice.ItemSubmitRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSubmitRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeStateResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeDetailResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PracticeFlowService implements IPracticeFlowService {

    private static final Logger log = LoggerFactory.getLogger(PracticeFlowService.class);

    private final IPracticeRepository practiceRepository;
    private final IFeedbackAgent feedbackAgent;
    private final IAssessAgent assessAgent;
    private final IContextUtil contextUtil;
    private final IIdUtil idUtil;

    public PracticeFlowService(
            IPracticeRepository practiceRepository,
            IFeedbackAgent feedbackAgent,
            IAssessAgent assessAgent,
            IContextUtil contextUtil,
            IIdUtil idUtil
    ) {
        this.practiceRepository = practiceRepository;
        this.feedbackAgent = feedbackAgent;
        this.assessAgent = assessAgent;
        this.contextUtil = contextUtil;
        this.idUtil = idUtil;
    }

    // 新建一轮练习
    @Override
    public PracticeDetailResponse init(PracticeInitRequest request) {
        request.setFeedbackMode(PracticeFeedbackMode.fromValue(request.getFeedbackMode()).name());
        String sessionId = idUtil.nextId();
        List<String> sessionItemIds = new ArrayList<>();
        int itemCount = practiceRepository.countPracticeItems(request, contextUtil.getUserId());
        for (int i = 0; i < itemCount; i++) {
            sessionItemIds.add(idUtil.nextId());
        }
        return practiceRepository.initPractice(request, sessionId, sessionItemIds, contextUtil.getUserId());
    }

    @Override
    public PracticeStateResponse exist(String qaSetId) {
        return practiceRepository.existPractice(qaSetId, contextUtil.getUserId());
    }

    @Override
    public List<PracticeSessionResponse> history(String qaSetId) {
        return practiceRepository.queryPracticeHistory(qaSetId, contextUtil.getUserId());
    }

    @Override
    public PracticeDetailResponse restart(PracticeRestartRequest request) {
        practiceRepository.abandonActivePractice(request.getQaSetId(), contextUtil.getUserId());
        PracticeInitRequest initRequest = PracticeInitRequest.builder()
                .qaSetId(request.getQaSetId())
                .mode(request.getMode())
                .feedbackMode(request.getFeedbackMode())
                .selectedModule(request.getSelectedModule())
                .itemIds(request.getItemIds())
                .build();
        return init(initRequest);
    }

    @Override
    public void abandon(PracticeAbandonRequest request) {
        practiceRepository.abandonPractice(request.getSessionId(), request.getDurationSeconds(), contextUtil.getUserId());
    }

    @Override
    public PracticeDetailResponse detail(String sessionId) {
        return practiceRepository.detailPractice(sessionId, contextUtil.getUserId());
    }

    @Override
    public PracticeItemResponse save(ItemSaveRequest request) {
        return practiceRepository.savePracticeAnswer(request, contextUtil.getUserId());
    }

    @Override
    public PracticeItemResponse unknown(ItemSaveRequest request) {
        PracticeStateVO state = practiceRepository.getPracticeState(request.getSessionId(), contextUtil.getUserId());
        if (state.getFeedbackMode().isItemByItem()) {
            ItemSubmitRequest submitRequest = ItemSubmitRequest.builder()
                    .sessionId(request.getSessionId())
                    .sessionItemId(request.getSessionItemId())
                    .userAnswer(request.getUserAnswer())
                    .currentIndex(request.getCurrentIndex())
                    .durationSeconds(request.getDurationSeconds())
                    .build();
            return confirmItem(submitRequest, true);
        }
        return practiceRepository.markUnknownOnly(request, contextUtil.getUserId());
    }

    @Override
    public PracticeItemResponse answer(ItemSubmitRequest request) {
        return confirmItem(request, false);
    }

    @Override
    public PracticeDetailResponse submit(PracticeSubmitRequest request) {
        String userId = contextUtil.getUserId();
        PracticeStateVO state = practiceRepository.getPracticeState(request.getSessionId(), userId);
        log.info("【练习流程】提交本轮: sessionId={}, feedbackMode={}", request.getSessionId(), state.getFeedbackMode().name());

        // 逐题反馈
        if (state.getFeedbackMode().isItemByItem()) {
            // 判断是否全部提交
            if (!practiceRepository.isPracticeSessionReadyForItemByItemAssess(request.getSessionId(), userId)) {
                throw new ApiException(ResultCode.PRACTICE_NOT_READY, "还有题目未提交，请完成逐题反馈后再结束本轮练习");
            }
        }
        // 整轮反馈
        else {
            // 判断是否全部做了
            if (!practiceRepository.isPracticeSessionReadyForAfterAllAssess(request.getSessionId(), userId)) {
                throw new ApiException(ResultCode.PRACTICE_NOT_READY, "还有题目未作答，请完成后再提交本轮练习");
            }
            List<PracticeItemResponse> items = practiceRepository.queryPracticeItemsForFeedback(request.getSessionId(), userId);
            log.info("【练习流程】生成整轮反馈: sessionId={}, total={}", request.getSessionId(), items.size());
            for (PracticeItemResponse item : items) {
                if (item.getResult() != null) {
                    continue;
                }
                FeedbackRequest feedbackRequest = FeedbackRequest.builder()
                        .sessionItemId(item.getSessionItemId())
                        .userAnswer(item.getUserAnswer())
                        .unknown(Boolean.TRUE.equals(item.getUnknown()))
                        .build();
                feedbackAgent.execute(feedbackRequest);
                practiceRepository.refreshPracticeItemProgress(
                        request.getSessionId(),
                        item.getSessionItemId(),
                        null,
                        request.getDurationSeconds(),
                        userId
                );
            }
        }

        AssessRequest assessRequest = AssessRequest.builder().sessionId(request.getSessionId()).build();
        assessAgent.execute(assessRequest);
        return practiceRepository.detailPractice(request.getSessionId(), userId);
    }

    private PracticeItemResponse confirmItem(ItemSubmitRequest request, boolean unknown) {
        String userId = contextUtil.getUserId();
        FeedbackRequest feedbackRequest = FeedbackRequest.builder()
                .sessionItemId(request.getSessionItemId())
                .userAnswer(request.getUserAnswer())
                .unknown(unknown)
                .build();
        feedbackAgent.execute(feedbackRequest);
        return practiceRepository.refreshPracticeItemProgress(
                request.getSessionId(),
                request.getSessionItemId(),
                request.getCurrentIndex(),
                request.getDurationSeconds(),
                userId
        );
    }
}
