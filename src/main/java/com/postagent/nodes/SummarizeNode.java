package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.postagent.service.PromptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 总结节点 对收集到的文本内容进行总结
 * 1. 生成小红书封面文案
 * 2. 生成小红书内容文案
 */
@Slf4j
@Component
public class SummarizeNode implements NodeAction {
    @Resource(name = "writingChatClient")
    private ChatClient chatClient;

    @Resource
    private PromptService promptService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("======SummarizeNode apply start======");
        String targetDir = state.value("targetDir").get().toString();
        // 目前设计的上一个节点只提供一个url，所以直接取第一个文件
        List<String> textList = getTextFromFile(targetDir);
        if (textList.isEmpty()) {
            throw new IllegalArgumentException("textList is empty");
        }
        // 生成小红书文案
        String systemPrompt = promptService.getXhsSummaryPrompt("");
        String result = chatClient.prompt()
                .system(systemPrompt)
                .user(textList.get(0))
                .call()
                .content();

        log.info("✅AI输出的小红书文案：\n {}", result);
        log.info("======SummarizeNode apply end======");
        return Map.of("summary_content", result);
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