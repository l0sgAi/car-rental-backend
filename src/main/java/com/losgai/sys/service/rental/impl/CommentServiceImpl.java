package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.entity.carRental.Like;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.CommentMapper;
import com.losgai.sys.mapper.LikeMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CommentService;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    private final LikeMapper likeMapper;

    private final Sender sender;

    private final RedisTemplate<String, Object> redisTemplate;

    private final String LIKE_KEY_PREFIX = "comment:like:";
    private final String LIKE_SYNC_SET = "comment:like:sync";
    private final String COMMENT_CACHE_KEY_PREFIX = "commentCache::";

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
        // 这里的缓存清除在消息队列消费者中执行
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
            redisTemplate.delete(COMMENT_CACHE_KEY_PREFIX + comment.getCarId());
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
        List<TopCommentVo> topComments = commentMapper.queryVoByCarIdWithLimit(carId,StpUtil.getLoginIdAsLong());
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

        // 查询子评论，对于每个顶级评论，一次最多加载3条
        List<CommentVo> children = commentMapper.queryVoByIds(parentIds,3,StpUtil.getLoginIdAsLong());

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
    @Description("加载更多回复")
    public List<CommentVo> loadReplyByCommentId(Long id) {
        return commentMapper.loadReplyByCommentId(id,StpUtil.getLoginIdAsLong());
    }

    @Override
    @Description("加载更多评论，不走缓存")
    public List<TopCommentVo> getMore(Long carId) {
        long curId = StpUtil.getLoginIdAsLong();
        // 查询顶级评论
        List<TopCommentVo> topComments = commentMapper.queryVoByCarId(carId,curId);
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

        // 查询子评论，对于每个顶级评论，一次最多加载3条
        List<CommentVo> children = commentMapper.queryVoByIds(parentIds,3,curId);

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
    @Description("点赞/取消点赞")
    public ResultCodeEnum like(Long commentId, Long userId) {
        if (commentId == null || userId == null) {
            return ResultCodeEnum.DATA_ERROR;
        }

        String key = LIKE_KEY_PREFIX + commentId;

        // 判断是否已点赞
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);

        if (Boolean.TRUE.equals(isMember)) {
            // ✅ 已点赞 -> 取消点赞
            redisTemplate.opsForSet().remove(key, userId);
        } else {
            // ✅ 未点赞 -> 点赞前校验数量
            Long currentLike = redisTemplate.opsForSet().size(key);
            if (currentLike != null && currentLike >= 50000) {
                return ResultCodeEnum.DATA_ERROR; // 点赞已达上限
            }
            redisTemplate.opsForSet().add(key, userId);
        }

        // ✅ 加入待同步列表
        redisTemplate.opsForSet().add(LIKE_SYNC_SET, commentId);

        return ResultCodeEnum.SUCCESS;
    }

    @Scheduled(fixedRate = 312345) // 每300秒执行一次点赞数同步
    public void syncLikeToDB() {
        // 获取待同步的评论Ids
        Set<Long> commentIds = Objects.requireNonNull(redisTemplate.opsForSet()
                        .members(LIKE_SYNC_SET))
                .stream().map(i-> Long.parseLong(i.toString()))
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(commentIds)) {
            return;
        }

        // 根据评论Ids查询对应carIds，并清除缓存
        Set<Long> carIds = commentMapper.queryCarIdsByCommentIds(commentIds);
        // 随机设置缓存过期时间，防雪崩
        for (Long carId : carIds) {
            String key = COMMENT_CACHE_KEY_PREFIX + carId;
            redisTemplate.expire(key, 5 + RandomUtil.randomInt(10), TimeUnit.SECONDS);
        }

        for (Long commentId : commentIds) {
            String key = LIKE_KEY_PREFIX + commentId;

            // ✅ 当前点赞人数
            Long likeCount = redisTemplate.opsForSet().size(key);

            // ✅ 写入 Comment 表（限制最大5万）
            if (likeCount != null) {
                commentMapper.syncLikeCount(commentId, Math.min(likeCount, 50000));
            }

            // ✅ 同步 Like 表
            Set<Object> userIds = redisTemplate.opsForSet().members(key);
            if (userIds != null && !userIds.isEmpty()) {

                List<Long> dbUserIds = likeMapper.listUserIdsByCommentId(commentId);
                Set<Long> dbUserIdSet = new HashSet<>(dbUserIds);

                List<Like> newLikes = new ArrayList<>();
                for (Object uidObj : userIds) {
                    Long uid = Long.valueOf(uidObj.toString());
                    if (!dbUserIdSet.contains(uid)) {
                        newLikes.add(new Like(null, uid, commentId));
                    }
                }

                if (CollUtil.isNotEmpty(newLikes)) {
                    likeMapper.batchInsert(newLikes); // 同步插入点赞表
                }
            }

            // 清理已经同步的 commentId
            redisTemplate.opsForSet().remove(LIKE_SYNC_SET, commentId);
        }
    }
}
