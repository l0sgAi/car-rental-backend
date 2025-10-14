package com.losgai.sys.service.ai;


import com.losgai.sys.entity.ai.AiMessagePair;

import java.io.IOException;
import java.util.List;

public interface AiMessagePairService {

    List<AiMessagePair> selectBySessionId(Long sessionId);

    void addMessage(AiMessagePair aiMessage);

    void deleteBySessionId(Long id);

    boolean insertAiMessagePairDocBatch(String indexName,List<AiMessagePair> aiMessagePairs) throws IOException;

    boolean insertAiMessagePairDoc(String indexName,AiMessagePair aiMessagePair) throws IOException;

    List<AiMessagePair> getFromGlobalSearch(String indexName,String query) throws IOException;
}
