package com.losgai.sys.service.rental;

import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;

import java.util.List;

public interface CommentService {
    ResultCodeEnum add(Comment comment, Long userId);

    ResultCodeEnum userAdd(Comment comment, Long userId);

    ResultCodeEnum delete(Long id);

    List<TopCommentVo> query(String keyWord);

    List<TopCommentVo> queryByCarId(Long carId);

    List<CommentVo> loadReplyByCommentId(Long id);

    List<TopCommentVo> getMore(Long carId);

    ResultCodeEnum like(Long carId, Long userId);
}
