package com.losgai.sys.util;

import com.losgai.sys.entity.ai.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelBuilder {

    /**
     * 非流简单式对话实现
     * */
    public String buildModelWithoutMemo(AiConfig aiConfig,
                                        String systemMsg,
                                        String userMsg){
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(aiConfig.getApiKey())
                .baseUrl(aiConfig.getApiDomain())
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .maxTokens(aiConfig.getMaxContextMsgs())
                .topP(aiConfig.getSimilarityTopP())
                .temperature(aiConfig.getTemperature())
                .model(aiConfig.getModelId())
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .build();

        return chatClient.prompt()
                .system(systemMsg)
                .user(userMsg)
                .call()
                .content();
    }

}
