package com.dasi.qa.agent.types.dto.response.qa;

import com.dasi.qa.agent.types.dto.response.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QaItemResponse extends BaseResponse {

    private String qaSetId;
    private String question;
    private String knowledgeNote;
    private String answer;
    private String moduleTag;
    private String difficulty;
    private String keywords;
    private String hint;
    private Boolean sourceReliable;
    private String sourceChunkIdsJson;
    private String completeStatus;
    private Integer sortOrder;
    private Integer practiceTotalCount;
    private BigDecimal practiceAverageScore;
    private BigDecimal practiceCorrectRate;
}
