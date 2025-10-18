package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.CommentMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CommentService;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    private final Sender sender;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @CacheEvict(value = "commentCache", key = "#comment.carId")
    public ResultCodeEnum add(Comment comment, Long userId) {
        comment.setUserId(userId);
        comment.setCreateTime(Date.from(Instant.now()));
        comment.setUpdateTime(Date.from(Instant.now()));
        comment.setDeleted(0);
        commentMapper.insert(comment);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum userAdd(Comment comment, Long userId) {
        // userId后端检查
        comment.setUserId(userId);
        comment.setCreateTime(Date.from(Instant.now()));
        comment.setUpdateTime(Date.from(Instant.now()));
        comment.setDeleted(0);
        sender.sendCarReview(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                RabbitMQAiMessageConfig.ROUTING_KEY_COMMENT_CENSOR,
                comment);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum delete(Long id) {
        Comment comment = commentMapper.selectByPrimaryKey(id);
        if (comment != null) {
            // 删除缓存
            redisTemplate.delete("commentCache::" + comment.getCarId());
        }
        commentMapper.deleteByPrimaryKey(id);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public List<TopCommentVo> query(String keyWord) {
        List<TopCommentVo> topComments = commentMapper.query(keyWord);
        if (CollUtil.isNotEmpty(topComments)) {
            topComments.forEach(comment -> {
                // 非管理员类型时，根据评分判是否为租户
                if (!Objects.equals(comment.getUserType(), 2) && comment.getScore() != null) {
                    comment.setUserType(1);
                }
            });
        }
        return topComments;
    }

    @Override
    @Cacheable(value = "commentCache", key = "#carId")
    public List<TopCommentVo> queryByCarId(Long carId) {
        // 查询顶级评论
        List<TopCommentVo> topComments = commentMapper.queryVoByCarId(carId);
        if (topComments.isEmpty()) {
            return Collections.emptyList();
        }

        // 封装用户类型 + 收集评论ID
        List<Long> parentIds = topComments.stream()
                .peek(comment -> {
                    // 非管理员类型时，根据评分判是否为租户
                    if (!Objects.equals(comment.getUserType(), 2) && comment.getScore() != null) {
                        comment.setUserType(1);
                    }
                })
                .map(TopCommentVo::getId)
                .collect(Collectors.toList());

        // 查询子评论
        List<CommentVo> children = commentMapper.queryVoByIds(parentIds);

        // 根据 parentId 分组
        Map<Long, List<CommentVo>> childrenGroup =
                children.stream().collect(Collectors.groupingBy(CommentVo::getParentCommentId));

        // 把 child list 塞回顶级评论
        topComments.forEach(top ->
                top.setChildren(childrenGroup.getOrDefault(top.getId(), Collections.emptyList()))
        );

        return topComments;
    }

    @Override
    public List<CommentVo> loadReplyByCommentId(Long id) {
        return commentMapper.loadReplyByCommentId(id);
    }
}
