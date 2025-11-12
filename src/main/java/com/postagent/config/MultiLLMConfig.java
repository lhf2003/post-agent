package com.postagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiLLMConfig {

    // 模型配置常量 - 默认使用百炼模型
    private static final String WRITING_MODEL = "qwen-plus";
    private static final String IMAGE_MODEL = "wan2.5-i2i-preview";

    // 温度参数常量
    private static final double WRITING_TEMPERATURE = 0.8;
    public static final double IMAGE_TEMPERATURE = 0.6;

    // Token限制常量
    private static final int WRITING_MAX_TOKENS = 30000;

    /**
     * 文案助手专用模型 - 擅长文案创作和编辑
     */
    @Bean("writingChatModel")
    public DashScopeChatModel writingChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(WRITING_MODEL)
                        .withTemperature(WRITING_TEMPERATURE)
                        .withMaxToken(WRITING_MAX_TOKENS)
                        .withEnableThinking(false)
                        .withEnableSearch(false)
                        .build())
                .build();
    }

    /**
     * 图片编辑助手专用模型 - 擅长图片编辑
     */
    @Bean("imageChatModel")
    public DashScopeChatModel imageChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(IMAGE_MODEL)
                        .withTemperature(IMAGE_TEMPERATURE)
                        .build())
                .build();
    }


    /**
     * 本地模型
     * @param ollamaChatModel 本地模型
     * @return 本地模型的ChatClient
     */
    @Bean
    public ChatClient ollamachatClient(@Qualifier("ollamaChatModel") OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).defaultAdvisors(new TokenLoggerAdvisor()).build();
    }

    /**
     * 文案助手专用ChatClient
     */
    @Bean("writingChatClient")
    public ChatClient writingChatClient(@Qualifier("writingChatModel") DashScopeChatModel writingChatModel) {
        return ChatClient.builder(writingChatModel).defaultAdvisors(new TokenLoggerAdvisor()).build();
    }

    /**
     * 图片编辑助手专用ChatClient
     */
    @Bean("imageChatClient")
    public ChatClient imageChatClient(@Qualifier("imageChatModel") DashScopeChatModel imageChatModel) {
        return ChatClient.builder(imageChatModel).defaultAdvisors(new TokenLoggerAdvisor()).build();
    }
}
