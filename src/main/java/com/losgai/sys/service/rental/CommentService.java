package com.losgai.sys.service.rental;

import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.dto.CommentDto;
import com.losgai.sys.entity.carRental.CommentIndex;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;

import java.util.List;
import java.util.Map;

public interface CommentService {
    ResultCodeEnum add(CommentDto commentDto, Long userId);

    ResultCodeEnum delete(Long id);

    List<TopCommentVo> query(String keyWord);

    List<TopCommentVo> queryByCarId(Long carId);

    List<CommentVo> loadReplyByCommentId(Long id);

    List<TopCommentVo> getMore(Long carId);

    ResultCodeEnum like(Long carId, Long userId);

    Map<Long, Long> queryCommentLikeCounts(List<Long> commentIds);

    AiConfig getDefaultConfig();

    ResultCodeEnum userAdd(CommentDto comment, Long userId);
}
