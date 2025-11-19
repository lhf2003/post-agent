package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 翻译节点
 */
@Slf4j
@Component
public class TranslateNode implements NodeAction {
    @Resource(name = "translateChatClient")
    private ChatClient translateChatClient;

    private static final String TRANSLATE_PROMPT = """
            # 角色
            你是一名翻译专家，精通各种主流语言
            # 任务
            请将{text}}翻译成中文
            # 要求
            1. 保持原文的结构和逻辑，不改变其含义。
            2. 翻译后的文本是用作文件名，所以不能包含特殊字符，但可以使用下划线。
            3. 翻译结果必须符合中文的语法规则和习惯用法。
            4. 只需要返回翻译后的文本，不需要其他任何解释。
            # 示例
            原文: "Hello, how are you?"
            翻译: "你好_你好吗"
            """;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("======TranslateNode apply start======");
        String collectedTitle = state.value("collectedTitle").orElse("unknown titlt name").toString();
        log.info("开始翻译标题: {}", collectedTitle);
        String content = translateChatClient.prompt()
                .user(TRANSLATE_PROMPT.replace("{text}", collectedTitle))
                .call()
                .content();
        log.info("翻译结果：{}", content);
        return Map.of("collectedTitle", content);
    }
}