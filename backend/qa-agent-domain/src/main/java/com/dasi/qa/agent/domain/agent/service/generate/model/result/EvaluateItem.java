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
public class EvaluateItem {

    @Description("校验结论，必须是 PASS / AMEND / REJECT 之一")
    private String verdict;

    @Description("判定原因")
    private String reason;

    @Description("修改建议，AMEND 时提供，其余留空")
    private String suggestion;
}
