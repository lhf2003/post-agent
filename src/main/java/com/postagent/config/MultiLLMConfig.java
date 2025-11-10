package com.postagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Multi-LLMs配置 为不同的Agent配置不同的模型
 */
@Configuration
public class MultiLLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 模型配置常量 - 默认使用百炼模型
    private static final String WRITING_MODEL = "qwen3-max";
    private static final String IMAGE_MODEL = "wan2.5-i2i-preview";
    private static final String ANALYSIS_MODEL = "qwen-plus";

    // 温度参数常量
    private static final double WRITING_TEMPERATURE = 0.6;
    private static final double CODING_TEMPERATURE = 0.5;
    private static final double ANALYSIS_TEMPERATURE = 0.4;

    // Token限制常量
    private static final int WRITING_MAX_TOKENS = 30000;
    private static final int CODING_MAX_TOKENS = 6000;
    private static final int ANALYSIS_MAX_TOKENS = 3000;

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
     * 代码手专用模型 - 擅长代码生成和调试
     */
    @Bean("codingChatModel")
    public DashScopeChatModel codingChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(IMAGE_MODEL)
                        .withTemperature(CODING_TEMPERATURE)
                        .withMaxToken(CODING_MAX_TOKENS)
                        .build())
                .build();
    }

    /**
     * 通用分析模型 - 用于数据分析和结果解释
     */
    @Bean("analysisChatModel")
    public DashScopeChatModel analysisChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(ANALYSIS_MODEL)
                        .withTemperature(ANALYSIS_TEMPERATURE)
                        .withMaxToken(ANALYSIS_MAX_TOKENS)
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
     * 通用分析ChatClient
     */
    @Bean("analysisChatClient")
    public ChatClient analysisChatClient(@Qualifier("analysisChatModel") DashScopeChatModel analysisChatModel) {
        return ChatClient.builder(analysisChatModel).defaultAdvisors(new TokenLoggerAdvisor()).build();
    }
}
