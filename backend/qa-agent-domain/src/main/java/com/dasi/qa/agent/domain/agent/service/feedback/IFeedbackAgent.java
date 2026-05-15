package com.dasi.qa.agent.domain.agent.service.feedback;

import com.dasi.qa.agent.types.dto.request.practice.FeedbackRequest;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;

public interface IFeedbackAgent {

    FeedbackResponse execute(FeedbackRequest request);
}
