package com.losgai.sys.service.ai;


import com.losgai.sys.entity.ai.AiSession;

import java.util.List;

public interface AiChatSessionService {

    Long addSession(AiSession aiSession);

    List<AiSession> selectByKeyword(String keyword);

    void deleteById(Long id);
}
