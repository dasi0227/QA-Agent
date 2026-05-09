package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmendItem {

    @Description("原题对象")
    private DraftItem draftItem;

    @Description("审校不通过的原因")
    private String reason;

    @Description("修改建议")
    private String suggestion;
}
