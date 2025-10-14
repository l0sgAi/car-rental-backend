package com.losgai.sys.service.ai;

import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.enums.ResultCodeEnum;

import java.util.List;

public interface AiConfigService {
    ResultCodeEnum add(AiConfig aiConfig);

    List<AiConfig> queryByKeyWord(String keyWord);

    ResultCodeEnum deleteById(Long id);

    ResultCodeEnum update(AiConfig aiConfig);

    List<AiConfig> getModels();
}
