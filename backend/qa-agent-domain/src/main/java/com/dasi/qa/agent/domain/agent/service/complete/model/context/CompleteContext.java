package com.dasi.qa.agent.domain.agent.service.complete.model.context;

import com.dasi.qa.agent.domain.agent.model.vo.UserProfileInfoVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteContext {

    private String qaItemId;
    private String question;
    private List<String> documentIds;
    private UserProfileInfoVO userProfile;
    private String answerStyle;
}
