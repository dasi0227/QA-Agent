package com.dasi.qa.agent.domain.agent.service.assess.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AdviseAgent 负责输出整轮整体点评和下一轮复习指导。
 */
public interface AdviseAgent {

    @SystemMessage(fromResource = "prompt/assess/assess-advise.txt")
    @UserMessage("""
            题集标题：{{qaSetTitle}}
            本轮统计：{{stats}}
            诊断结果：{{diagnosis}}
            单题简要摘要：{{itemBriefs}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，字符串字段无内容时输出 ""。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent
    String advise(@V("qaSetTitle") String qaSetTitle,
                  @V("stats") String stats,
                  @V("diagnosis") String diagnosis,
                  @V("itemBriefs") String itemBriefs,
                  @V("retryHint") String retryHint);
}
