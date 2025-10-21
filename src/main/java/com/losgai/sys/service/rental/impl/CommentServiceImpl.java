package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
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
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    private final RedissonClient redissonClient;

    private final String LIKE_KEY_PREFIX = "comment:like:";
    private final String LIKE_SYNC_SET = "comment:like:sync";
    private final String COMMENT_CACHE_KEY_PREFIX = "commentCache::";

    private static final String LOCK_KEY_PREFIX_COMMENT_LIKE = "lock:comment:like:";

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
//    @Cacheable(value = "commentCache", key = "#carId")
    public List<TopCommentVo> queryByCarId(Long carId) {
        // 查询顶级评论
        List<TopCommentVo> topComments = commentMapper.queryVoByCarIdWithLimit(carId);

        if (topComments.isEmpty()) {
            return Collections.emptyList();
        }

        Long curUserId = StpUtil.getLoginIdAsLong();
        // 给顶级评论列表赋值是否点赞过
        assignLikedTop(topComments, curUserId);

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
        List<CommentVo> children = commentMapper.queryVoByIds(parentIds, 3);
        // 给子评论列表赋值是否点赞过
        if (CollUtil.isNotEmpty(children)) {
            assignLiked(children, curUserId);
        }

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
        List<CommentVo> commentVos = commentMapper.loadReplyByCommentId(id);
        if (CollUtil.isEmpty(commentVos)) {
            return Collections.emptyList();
        }
        Long curUserId = StpUtil.getLoginIdAsLong();
        // 给顶级评论列表赋值是否点赞过
        assignLiked(commentVos, curUserId);
        return commentVos;
    }

    @Override
    @Description("加载更多评论，不走缓存")
    public List<TopCommentVo> getMore(Long carId) {
        // 查询顶级评论
        List<TopCommentVo> topComments = commentMapper.queryVoByCarId(carId);

        if (topComments.isEmpty()) {
            return Collections.emptyList();
        }

        Long curUserId = StpUtil.getLoginIdAsLong();
        // 给顶级评论列表赋值是否点赞过
        assignLikedTop(topComments, curUserId);

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
        List<CommentVo> children = commentMapper.queryVoByIds(parentIds, 3);
        if (CollUtil.isNotEmpty(children)) {
            assignLiked(children, curUserId);
        }

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

        // 尝试重建缓存，同时获取key
        String key = rebuildLikedCache(commentId);

        // 1. 获取对应 commentId 的锁
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX_COMMENT_LIKE + commentId);

        boolean isLocked = false;

        try {
            // 2. 尝试获取锁
            // 参数: 等待时间, 锁自动释放时间, 时间单位
            // 用户操作，等待时间不宜过长，比如最多等3秒
            isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);

            if (isLocked) {
                // 判断是否已点赞
                Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);

                if (Boolean.TRUE.equals(isMember)) {
                    // 已点赞 -> 取消点赞
                    // 如果是最后一个点赞被取消，需要防止删除key
                    // 使用-1L占位，表示该用户已取消点赞
                    redisTemplate.opsForSet().add(key, -1L);
                    redisTemplate.opsForSet().remove(key, userId);
                } else {
                    // 未点赞 -> 点赞前校验数量
                    Long currentLike = redisTemplate.opsForSet().size(key);
                    if (currentLike != null && currentLike >= 50000) {
                        return ResultCodeEnum.DATA_ERROR; // 点赞已达上限
                    }
                    // 新增点赞，需要延长过期时间至永久
                    redisTemplate.opsForSet().add(key, userId);
                }

                // 加入待同步列表
                redisTemplate.opsForSet().add(LIKE_SYNC_SET, commentId);
                return ResultCodeEnum.SUCCESS;
            } else {
                // 获取锁失败
                return ResultCodeEnum.SYSTEM_ERROR;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("用户 {} 操作评论 {} 时获取锁被中断", userId, commentId, e);
            throw new RuntimeException("系统异常，请稍后再试");
        } finally {
            // 3. 释放锁
            if (isLocked && lock.isLocked()) {
                lock.unlock();
            }
        }
    }

    public Map<Long, Long> queryCommentLikeCounts(List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return Collections.emptyMap();
        }

        // 批量执行 SCARD 拿点赞数
        List<Object> results = redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (Long commentId : commentIds) {
                String key = LIKE_KEY_PREFIX + commentId;
                stringConn.sCard(key);
            }
            return null;
        });

        // 组装结果
        Map<Long, Long> likeCountMap = new HashMap<>(commentIds.size());
        for (int i = 0; i < commentIds.size(); i++) {
            Object countObj = results.get(i);
            long count = countObj == null ? 0 : Long.parseLong(countObj.toString());
            likeCountMap.put(commentIds.get(i), count);
        }
        return likeCountMap;
    }

    @Scheduled(fixedRate = 45234) // 每45秒执行一次点赞数同步
    public void syncLikeToDB() {
        // 获取待同步的评论Ids
        Set<Long> commentIds = Objects.requireNonNull(redisTemplate.opsForSet()
                        .members(LIKE_SYNC_SET))
                .stream().map(i -> Long.parseLong(i.toString()))
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(commentIds)) {
            return;
        }

        // 根据评论Ids查询对应carIds，并清除缓存
        List<String> cacheKeys = commentMapper.queryCarIdsByCommentIds(commentIds).stream()
                .map(carId -> COMMENT_CACHE_KEY_PREFIX + carId)
                .collect(Collectors.toList());

        // 清除对应的carId缓存
        redisTemplate.delete(cacheKeys);

        if (CollUtil.isEmpty(commentIds)) {
            return;
        }

        // 1. 准备批量锁
        List<RLock> locks = commentIds.stream()
                .map(id -> redissonClient.getLock(LOCK_KEY_PREFIX_COMMENT_LIKE + id))
                .toList();

        // 将所有 RLock 聚合为一个 MultiLock
        RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[0]));

        boolean isLocked = false;

        try {
            // 2. 尝试批量获取锁
            // 参数: 等待时间, 锁自动释放时间, 时间单位
            // 最多等待5秒，如果获取成功，锁会在60秒后自动释放（防止宕机死锁）
            isLocked = multiLock.tryLock(5, 60, TimeUnit.SECONDS);

            if (isLocked) {
                // 阶段一：批量获取 Redis 和 DB 数据
                // 1. 从 Redis 中批量获取所有点赞数据
                // Map<commentId, Set<userId>>
                Map<Long, Set<Long>> redisLikesMap = new HashMap<>();
                // pipeline 批量执行 SMEMBERS
                List<Object> pipelineResult = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    // 使用 connection 的原生方法而不是强制转换为 StringRedisConnection
                    for (Long commentId : commentIds) {
                        String key = LIKE_KEY_PREFIX + commentId;
                        connection.setCommands().sMembers(key.getBytes()); // ✅ 使用字节操作
                    }
                    return null;
                });

                // 组装结果 Map<commentId, Set<userId>>
                int n = 0;
                for (Long commentId : commentIds) {
                    Object data = pipelineResult.get(n); // 返回的是 Set<byte[]>
                    n++;
                    if (data instanceof Set<?> rawSet && CollUtil.isNotEmpty(rawSet)) {
                        Set<Long> userIds = rawSet.stream()
                                .map(Object::toString)
                                .map(Long::parseLong)
                                .filter(id -> id > 0)
                                .collect(Collectors.toSet());
                        redisLikesMap.put(commentId, userIds);
                    }
                }

                // 2. 从数据库中一次性查询出所有相关的、未被软删除的点赞记录
                // Map<commentId, Set<userId>>
                Map<Long, Set<Long>> dbLikesMap = new HashMap<>();
                List<Like> dbLikes = likeMapper.listActiveLikesByCommentIds(commentIds);
                if (CollUtil.isNotEmpty(dbLikes)) {
                    dbLikesMap = dbLikes.stream()
                            .collect(Collectors.groupingBy(Like::getCommentId,
                                    Collectors.mapping(Like::getUserId, Collectors.toSet())));
                }

                // 阶段二：在内存中计算差异

                List<Like> likesToInsert = new ArrayList<>(); // 所有需要新增的点赞
                List<Like> likesToSoftDelete = new ArrayList<>(); // 所有需要软删除的点赞
                Map<Long, Long> commentLikeCounts = new HashMap<>(); // 最终的评论点赞数

                for (Long commentId : commentIds) {
                    Set<Long> redisUserIds = redisLikesMap.getOrDefault(commentId, Collections.emptySet());
                    Set<Long> dbUserIds = dbLikesMap.getOrDefault(commentId, Collections.emptySet());

                    // 计算点赞数并放入待更新Map
                    commentLikeCounts.put(commentId, Math.min((long) redisUserIds.size(), 50000));

                    // 1. 找出需要软删除的：在数据库中存在，但在 Redis 中不存在
                    // 复制一份 dbUserIds 用于计算，避免修改原始 map 中的 set
                    Set<Long> toDeleteSet = new HashSet<>(dbUserIds);
                    toDeleteSet.removeAll(redisUserIds); // 差集：db - redis
                    for (Long userId : toDeleteSet) {
                        likesToSoftDelete.add(new Like(null, userId, commentId));
                    }

                    // 2. 找出需要新增的：所有 Redis 中的记录都尝试插入
                    // 使用 INSERT IGNORE，数据库中已存在且未删除的记录会被忽略，
                    // 而新记录或之前被软删除的记录（如果需要恢复的话）会被处理。
                    // 这里我们简化为只处理新增。
                    for (Long userId : redisUserIds) {
                        likesToInsert.add(new Like(null, userId, commentId));
                    }
                }

                // 阶段三：批量执行数据库操作

                // 1. 批量软删除
                if (CollUtil.isNotEmpty(likesToSoftDelete)) {
                    likeMapper.batchSoftDelete(likesToSoftDelete);
                    log.info("批量软删除点赞记录 {} 条。", likesToSoftDelete.size());
                }

                // 2. 批量使用 INSERT IGNORE 新增
                if (CollUtil.isNotEmpty(likesToInsert)) {
                    // 为防止单次插入数据量过大，可以分批
                    final int BATCH_SIZE = 1000;
                    for (int i = 0; i < likesToInsert.size(); i += BATCH_SIZE) {
                        List<Like> batchList = likesToInsert.subList(i, Math.min(i + BATCH_SIZE, likesToInsert.size()));
                        likeMapper.batchInsert(batchList);
                    }
                    log.info("批量 INSERT IGNORE 点赞记录 {} 条。", likesToInsert.size());
                }

                // 3. 批量更新 Comment 表的点赞数
                if (CollUtil.isNotEmpty(commentLikeCounts)) {
                    commentMapper.batchUpdateLikeCount(commentLikeCounts);
                    log.info("批量更新 Comment 表点赞数 {} 条。", commentLikeCounts.size());
                }

                // 阶段四：清理 Redis 同步集合
                redisTemplate.opsForSet().remove(LIKE_SYNC_SET, commentIds.toArray());
                log.info("从 Redis 同步集合中移除 {} 个 commentId。", commentIds.size());
            } else {
                log.warn("批量获取同步锁失败，本次任务将跳过。Comment IDs: {}", commentIds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取批量同步锁时被中断", e);
        } finally {
            // 3. 释放锁
            if (isLocked) {
                multiLock.unlock();
                log.info("成功释放 {} 个评论的批量同步锁", commentIds.size());
            }
        }

    }


    @Description("根据commentId重建点赞缓存")
    private String rebuildLikedCache(Long commentId) {
        String key = LIKE_KEY_PREFIX + commentId;
        // 判断缓存中是否有对应key
        if (!redisTemplate.hasKey(key)) {
            // 无key，从数据库重建缓存
            // TODO: 重建时加分布式锁？
            List<Long> userIds = likeMapper.listUserIdsByCommentId(commentId);
            if (CollUtil.isNotEmpty(userIds)) {
                // 有值的key不能过期！
                redisTemplate.opsForSet().add(key, userIds.toArray());
            } else {
                // 放一个无效占位符 -1
                redisTemplate.opsForSet().add(key, -1L);
            }
        }
        return key;
    }

    @Description("给评论列表赋值是否点赞过")
    private void assignLikedTop(List<TopCommentVo> commentVos, Long curUserId) {
        for (TopCommentVo comment : commentVos) {
            // 尝试重建缓存
            String key = rebuildLikedCache(comment.getId());
            // 判断是否已点赞，1为已点赞
            SetOperations<String, Object> stringObjectSetOperations = redisTemplate.opsForSet();
            Boolean isMember = stringObjectSetOperations.isMember(key, curUserId);
            Long liked = stringObjectSetOperations.size(key);
            comment.setLiked(Boolean.TRUE.equals(isMember) ? 1 : 0);
            if (liked != null) {
                comment.setLikeCount(liked.intValue() - 1);
            }
        }
    }

    @Description("给评论列表赋值是否点赞过")
    private void assignLiked(List<CommentVo> commentVos, Long curUserId) {
        for (CommentVo comment : commentVos) {
            // 尝试重建缓存
            String key = rebuildLikedCache(comment.getId());
            // 判断是否已点赞，1为已点赞
            SetOperations<String, Object> stringObjectSetOperations = redisTemplate.opsForSet();
            Boolean isMember = stringObjectSetOperations.isMember(key, curUserId);
            Long liked = stringObjectSetOperations.size(key);
            comment.setLiked(Boolean.TRUE.equals(isMember) ? 1 : 0);
            if (liked != null) {
                comment.setLikeCount(liked.intValue() - 1);
            }
        }
    }

}
