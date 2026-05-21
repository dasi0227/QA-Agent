package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.model.vo.PracticeVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.feedback.model.command.FeedbackSaveCommand;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.HintResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.JudgeResult;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;
import com.dasi.qa.agent.types.dto.response.practice.HintDetail;
import com.dasi.qa.agent.types.dto.response.practice.JudgeDetail;
import com.dasi.qa.agent.types.dto.response.practice.SourceChunk;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 负责从 scope 读取反馈结果并落库，返回 FeedbackResponse。
 */
@Component
@Slf4j
public class FeedbackSaver {

    private static final String UNKNOWN_SUMMARY = "这题已标记为不会。";

    private final IAgentRepository agentRepository;

    public FeedbackSaver(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public FeedbackResponse save(AgenticScope scope, PracticeVO practice, boolean unknown, String userAnswer, String userId) {
        // 1. 根据 unknown 二选一
        FeedbackSaveCommand command = unknown
                ? hintSaveCommand(scope)
                : judgeSaveCommand(scope, userAnswer);

        // 2. 只记录最新的回答
        LocalDateTime answeredAt = agentRepository.saveFeedbackResult(practice.getSessionItemId(), userId, command);
        log.info("【单题反馈】保存完成: sessionItemId={}", practice.getSessionItemId());

        // 3. 构造返回对象
        List<SourceChunk> sourceChunks = practice.getSourceChunks().stream()
                .map(c -> SourceChunk.builder()
                        .chunkId(c.getChunkId())
                        .documentId(c.getDocumentId())
                        .titlePath(c.getTitlePath())
                        .summary(c.getSummary())
                        .content(c.getContent())
                        .build())
                .toList();

        return FeedbackResponse.builder()
                .sessionItemId(practice.getSessionItemId())
                .qaItemId(practice.getQaItemId())
                .result(command.getResult().name())
                .score(command.getScore())
                .feedbackSummary(command.getFeedbackSummary())
                .judgeDetail(command.getJudgeDetail())
                .hintDetail(command.getHintDetail())
                .sourceChunks(sourceChunks)
                .answeredAt(answeredAt)
                .build();
    }

    private FeedbackSaveCommand hintSaveCommand(AgenticScope scope) {
        HintResult result = (HintResult) scope.readState(FeedbackPhase.HINT.getScopeKey());
        return FeedbackSaveCommand.builder()
                .userAnswer("")
                .unknown(true)
                .result(FeedbackResult.UNKNOWN)
                .score(0)
                .feedbackSummary(UNKNOWN_SUMMARY)
                .hintDetail(HintDetail.builder()
                        .memoryTip(result.getMemoryTip())
                        .encouragement(result.getEncouragement())
                        .build())
                .build();
    }

    private FeedbackSaveCommand judgeSaveCommand(AgenticScope scope, String userAnswer) {
        JudgeResult result = (JudgeResult) scope.readState(FeedbackPhase.JUDGE.getScopeKey());
        return FeedbackSaveCommand.builder()
                .userAnswer(userAnswer)
                .unknown(false)
                .result(FeedbackResult.valueOf(result.getResult()))
                .score(result.getScore())
                .feedbackSummary(result.getFeedbackSummary())
                .judgeDetail(JudgeDetail.builder()
                        .missingPoints(result.getMissingPoints() != null ? result.getMissingPoints() : List.of())
                        .wrongPoints(result.getWrongPoints() != null ? result.getWrongPoints() : List.of())
                        .improvementAdvice(result.getImprovementAdvice())
                        .betterAnswer(result.getBetterAnswer())
                        .build())
                .build();
    }
}
