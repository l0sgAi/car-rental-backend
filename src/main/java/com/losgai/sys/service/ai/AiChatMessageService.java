package com.losgai.sys.service.ai;

import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.global.ChatSession;

public interface AiChatMessageService {

    ChatSession chatMessageStream(AiConfig config, String userMessage);
}
