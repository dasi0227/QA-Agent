package com.dasi.qa.agent.domain.agent.service.assess.subagent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * DiagnoseAgent 负责识别整轮练习中的优势和薄弱点。
 */
public interface DiagnoseAgent {

    @SystemMessage(fromResource = "prompt/assess/assess-diagnose.txt")
    @UserMessage("""
            题集标题：{{qaSetTitle}}
            本轮统计：{{metrics}}
            单题作答摘要：{{items}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，数组字段无内容时输出 []，字符串字段无内容时输出 ""。
            5. 不允许添加未定义字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    @Agent(name = "DIAGNOSE", description = "识别整轮练习的优势和薄弱点")
    String diagnose(@V("qaSetTitle") String qaSetTitle,
                    @V("metrics") String metrics,
                    @V("items") String items,
                    @V("retryHint") String retryHint);
}
