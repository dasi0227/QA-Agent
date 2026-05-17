package com.dasi.qa.agent.domain.agent.service.feedback;

import com.dasi.qa.agent.types.dto.request.practice.FeedbackRequest;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;

/**
 * 单题反馈 Agent 的领域入口。
 */
public interface IFeedbackAgent {

    FeedbackResponse execute(FeedbackRequest request);
}
