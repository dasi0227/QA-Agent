package com.dasi.qa.agent.types.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TempChatRequest {

    @NotBlank(message = "临时对话 ID 不能为空")
    @Size(max = 128, message = "临时对话 ID 过长")
    private String tempChatId;

    @NotBlank(message = "请输入要询问的内容")
    @Size(max = 4000, message = "单次提问最多 4000 个字符")
    private String message;

}
