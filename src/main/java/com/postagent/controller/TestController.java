package com.postagent.controller;

import com.postagent.service.PromptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {
    @Resource(name = "writingChatClient")
    private ChatClient chatClient;

    @Resource
    private PromptService promptService;

    @RequestMapping("/chat")
    public String test() {
        String result = chatClient.prompt()
                .user("你好")
                .call()
                .content();
        return result;
    }

}