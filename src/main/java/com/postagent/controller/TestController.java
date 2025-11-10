package com.postagent.controller;

import com.postagent.service.PromptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        System.out.println(result);
        return result;
    }

    @RequestMapping("/prompt")
    public String testPrompt() throws IOException {
        List<String> fileToTextList = getTextFromFile("D:\\workspace\\post-agent\\out\\20251110144518");
        String xhsSummaryPrompt = promptService.getXhsSummaryPrompt("");
        ChatClient.CallResponseSpec responseSpec = chatClient.prompt()
                .system(xhsSummaryPrompt)
                .user(fileToTextList.get(0))
                .call();
        ChatClientResponse chatResponse = responseSpec.chatClientResponse();
        System.out.println(chatResponse);
        return chatResponse.chatResponse().getResult().getOutput().getText();
    }

    /**
     * 从指定目录读取文件
     * @param targetDir 目标目录
     * @return 文件内容列表
     */
    private List<String> getTextFromFile(String targetDir) throws IOException, NullPointerException {
        File dictionary = new File(targetDir);
        if (!dictionary.exists()) {
            throw new IOException("directory not exists: " + targetDir);
        }
        List<String> fileToTextList = new ArrayList<>();
        // 遍历目录下的所有文件，读取文件内容并添加到列表中
        for (File file : Objects.requireNonNull(dictionary.listFiles())) {
            // 只读取md文件
            if (!file.getName().endsWith(".md")) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                fileToTextList.add(sb.toString());
            }
        }

        return fileToTextList;
    }
}