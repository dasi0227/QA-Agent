package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.chat.service.IChatService;
import com.dasi.qa.agent.types.dto.request.chat.TempChatRequest;
import com.dasi.qa.agent.types.dto.response.chat.TempChatResponse;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final IChatService chatService;

    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/temp")
    public Result<TempChatResponse> tempChat(@RequestBody @Valid TempChatRequest request) {
        return Result.success(chatService.tempChat(request));
    }

}
