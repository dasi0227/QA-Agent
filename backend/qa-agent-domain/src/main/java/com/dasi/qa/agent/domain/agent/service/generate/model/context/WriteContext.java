package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSupervisor;
import com.dasi.qa.agent.domain.agent.service.generate.support.WebEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.shared.RagEvidenceProvider;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.Executor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WriteContext {

    private String taskId;
    private String userId;
    private CreateQaSetRequest request;
    private Executor executor;
    private RagEvidenceProvider ragEvidenceProvider;
    private WebEvidenceProvider webEvidenceProvider;
    private String targetCompany;
    private String targetRole;
    private String userProfileJson;
    private String answerStyle;
    private GenerateSupervisor supervisor;
}
