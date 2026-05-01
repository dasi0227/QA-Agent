package com.dasi.qa.agent.types.model.request.qa;

import com.dasi.qa.agent.types.model.request.BaseRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QaItemRequest extends BaseRequest {

    private String qaSetId;
    private String question;
    private String knowledgeNote;
    private String answer;
    private String moduleTag;
    private String difficulty;
    private String conflictTip;
    private String sourceChunkIdsJson;
    private Integer sortOrder;
}
