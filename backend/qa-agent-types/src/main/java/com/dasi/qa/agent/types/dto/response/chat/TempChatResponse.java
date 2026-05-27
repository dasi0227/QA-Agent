package com.dasi.qa.agent.types.dto.response.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TempChatResponse {

    private String role;

    private String content;

}
