package com.losgai.sys.service.ai.impl;

import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.global.ChatSession;
import com.losgai.sys.service.ai.AiChatMessageService;
import com.losgai.sys.util.OpenAiModelBuilder;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

@Service
public class AiChatMessageServiceImpl implements AiChatMessageService {

    public ChatSession chatMessageStream(AiConfig config, String userMessage) {
        OpenAiStreamingChatModel model = OpenAiModelBuilder.fromAiConfigByLangChain4j(config);
        return new ChatSession(model, userMessage);
    }
}
