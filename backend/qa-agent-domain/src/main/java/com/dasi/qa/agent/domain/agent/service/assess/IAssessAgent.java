package com.dasi.qa.agent.domain.agent.service.assess;

import com.dasi.qa.agent.types.dto.request.practice.AssessRequest;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;

public interface IAssessAgent {

    AssessResponse execute(AssessRequest request);
}
