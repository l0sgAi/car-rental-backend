package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.losgai.sys.config.RabbitMQMessageConfig;
import com.losgai.sys.dto.ReviewDto;
import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.dto.CommentDto;
import com.losgai.sys.entity.carRental.CommentDetail;
import com.losgai.sys.entity.carRental.CommentIndex;
import com.losgai.sys.entity.carRental.Like;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.*;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CommentService;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Description;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AiConfigMapper aiConfigMapper;

    private final CommentIndexMapper commentIndexMapper;

    private final CommentDetailMapper commentDetailMapper;

    private final RentalOrderMapper rentalOrderMapper;

    private final Sender sender;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonClient redissonClient;

    // 点赞列表set前缀
    public static final String USER_LIKE_KEY_PREFIX = "user:like:list";
    // 点赞计数缓存key前缀
    public static final String LIKE_COUNT_KEY_PREFIX = "comment:like:count:";
    // 需要向数据库同步点赞的评论数据id集合
    public static final String LIKE_SYNC_SET = "comment:like:sync";
    // 评论缓存key前缀
    public static final String COMMENT_CACHE_KEY_PREFIX = "comment:content::";
    // 点赞数的分布式锁前缀
    public static final String LOCK_KEY_PREFIX_COMMENT_LIKE = "lock:comment:like:";
    // 用户点赞列表的分布式锁前缀
    public static final String LOCK_KEY_PREFIX_USER_LIKE = "lock:comment:like:";
    // Unlike Log
    public static final String UNLIKE_LOG = "UNLIKE_LOG:";
    // 点赞上限
    public static final Integer LIKE_LIMIT = 5000;

    private DefaultRedisScript<Long> likeScript;

    @PostConstruct
    public void init() {
        likeScript = new DefaultRedisScript<>();
        likeScript.setLocation(new ClassPathResource("scripts/like_script.lua"));
        likeScript.setResultType(Long.class);
    }

    @Override
//    @CacheEvict(value = "commentCache", key = "#comment.carId")
    public ResultCodeEnum add(CommentDto commentDto, Long userId) {
        commentDto.setUserId(userId);
        commentDto.setCreateTime(Date.from(Instant.now()));
        commentDto.setUpdateTime(Date.from(Instant.now()));
        commentMapper.insert(commentDto);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Transactional
    public ResultCodeEnum userAdd(CommentDto comment, Long userId) {
        // userId后端检查
        comment.setUserId(userId);
        comment.setCreateTime(Date.from(Instant.now()));
        comment.setUpdateTime(Date.from(Instant.now()));
        // 构建索引
        CommentIndex commentIndex = new CommentIndex();
        commentIndex.setUserId(userId);
        commentIndex.setCarId(comment.getCarId());
        commentIndex.setParentCommentId(comment.getParentCommentId());
        commentIndex.setFollowCommentId(comment.getFollowCommentId());
        commentIndex.setHotScore(0);
        commentIndex.setCreateTime(comment.getCreateTime());
        commentIndex.setUpdateTime(comment.getUpdateTime());
        // 审核结束前都置为1已经删除，不做展示
        commentIndex.setDeleted(1);
        // 构建详情
        CommentDetail commentDetail = new CommentDetail();
        commentDetail.setIndexId(commentIndex.getId());
        commentDetail.setContent(comment.getContent());
        // score从用户订单中查询
        Integer score = rentalOrderMapper.getScoreByUserIdAndCarId(userId, comment.getCarId());
        commentDetail.setScore(score);
        commentDetail.setExtraImages(comment.getExtraImages());
        commentDetail.setCreateTime(comment.getCreateTime());
        commentDetail.setUpdateTime(comment.getUpdateTime());
        // 审核结束前都置为1已经删除，不做展示
        commentDetail.setDeleted(1);
        // 执行插入
        commentIndexMapper.insert(commentIndex);
        commentDetailMapper.insert(commentDetail);
        // 发给消息队列执行审核
        sender.sendCarReview(RabbitMQMessageConfig.EXCHANGE_NAME,
                RabbitMQMessageConfig.ROUTING_KEY_COMMENT_CENSOR,
                new ReviewDto(commentIndex.getId(), commentDetail.getId(),commentDetail.getContent()));
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum delete(Long id) {
        CommentDto commentDto = commentMapper.selectByPrimaryKey(id);
        if (commentDto != null) {
            // 删除缓存
            redisTemplate.delete(COMMENT_CACHE_KEY_PREFIX + commentDto.getCarId());
        }
        commentMapper.deleteByPrimaryKey(id);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public List<TopCommentVo> query(String keyWord) {
        // 1. 查询keyword对应的评论内容
        List<TopCommentVo> topComments = commentDetailMapper.query(keyWord);
        if (CollUtil.isNotEmpty(topComments)) {
            topComments.forEach(comment -> {
                // 因为开始是按角色分配userType 0-用户 1-管理员
                // 我们要转换成业务标识，0-普通客户 1-租户 2-管理员
                if (comment.getUserType() == 1) {
                    comment.setUserType(2);
                }
                // 非管理员类型时，根据评分判是否为租户
                if (!Objects.equals(comment.getUserType(), 2) && comment.getScore() != null) {
                    comment.setUserType(1);
                }
            });
        }
        return topComments;
    }


    /**
     *  这里就牛b了，我们最复杂的一部分：评论列表异步编排组装：
     *  1. 我们在表中查询
     * */
    @Override
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
    @Description("点赞/取消点赞 (Lua原子版)")
    public ResultCodeEnum like(Long commentId, Long userId) {
        if (commentId == null || userId == null) {
            return ResultCodeEnum.DATA_ERROR;
        }

        String userSetKey = USER_LIKE_KEY_PREFIX + userId;
        String countKey = LIKE_COUNT_KEY_PREFIX + commentId;

        // 1. 尝试执行 Lua 脚本
        // 参数说明：Keys列表, ARGV列表
        long result = executeLikeScript(userSetKey, countKey, commentId);

        // 2. 如果返回 -1，说明缓存缺失，需要重建
        if (result == -1) {
            log.info("缓存缺失，执行重建逻辑... uid:{}, cid:{}", userId, commentId);

            // 重建两个缓存
            rebuildUserLikedCache(userId);
            rebuildCommentCountCache(commentId);

            // 再次执行 Lua 脚本
            result = executeLikeScript(userSetKey, countKey, commentId);

            // 如果还是 -1，说明重建失败或者系统异常，做个兜底
            if (result == -1) {
                return ResultCodeEnum.SYSTEM_ERROR;
            }
        }

        // 3. 根据结果返回
        // 或者返回特定枚举 UNLIKED
        if (result == 1) {
            log.info("点赞成功");
        } else {
            log.info("取消点赞成功");
        }
        // 消息队列发送点赞记录入库和点赞数量更新请求
        Like like = new Like();
        like.setUserId(userId);
        like.setCommentId(commentId);
        like.setIsFallback(result == 1?0:1);
        like.setCreateTime(Date.from(Instant.now()));
        sender.sendLikeSync(RabbitMQMessageConfig.EXCHANGE_NAME, RabbitMQMessageConfig.QUEUE_NAME_LIKE,like);
        return ResultCodeEnum.SUCCESS;
    }

    private Long executeLikeScript(String userSetKey, String countKey, Long commentId) {
        return redisTemplate.execute(
                likeScript,
                Arrays.asList(userSetKey, countKey), // KEYS
                commentId,           // ARGV[1]
                7 * 24 * 60 * 60,    // ARGV[2] UserSet过期时间 7天
                24 * 60 * 60         // ARGV[3] Count过期时间 1天
        );
    }

    /**
     * 重建评论点赞数缓存 (只负责 Count)
     * 调用时机：GET article:count:{id} 返回 null 时
     */
    @Description("根据commentId重建点赞数缓存")
    private void rebuildCommentCountCache(Long commentId) {
        String countKey = LIKE_COUNT_KEY_PREFIX + commentId;
        // 1. 第一层检查
        if (redisTemplate.hasKey(countKey)) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX_COMMENT_LIKE + commentId);
        try {
            // 尝试加锁
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 2. 双重检查 (Double Check)
                    if (redisTemplate.hasKey(countKey)) {
                        return;
                    }

                    log.info("重建评论点赞数缓存，id: {}", commentId);

                    // 3. 查 DB
                    Long dbCount = likeMapper.countByCommentId(commentId);
                    dbCount = dbCount == null ? 0L : dbCount;

                    // 4. 写 Redis
                    redisTemplate.opsForValue().set(countKey, dbCount, 24, TimeUnit.HOURS);

                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted during comment count rebuild", e);
        }
    }

    /**
     * 重建用户点赞历史缓存 (只负责 User Set)
     * 调用时机：SISMEMBER user:likes:{uid} 之前的检查发现 Key 不存在时
     */
    @Description("根据userId重建用户点赞历史缓存")
    private void rebuildUserLikedCache(Long userId) {
        String userSetKey = USER_LIKE_KEY_PREFIX + userId;
        // 1. 第一层检查
        if (redisTemplate.hasKey(userSetKey)) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX_USER_LIKE + userId);
        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 2. 双重检查
                    if (redisTemplate.hasKey(userSetKey)) {
                        return;
                    }

                    log.info("重建用户点赞历史缓存，uid: {}", userId);

                    // 3. 查 DB (注意：这里查的是 commentId 列表，不是 userId)
                    List<Like> recentLikes = likeMapper.selectLatestLikes(userId, LIKE_LIMIT);

                    if (CollUtil.isNotEmpty(recentLikes)) {
                        // 提取 CommentId
                        Object[] commentIds = recentLikes.stream()
                                .map(Like::getCommentId)
                                .toArray(); // <--- 关键修正：转为数组

                        // 4. 批量写入 Set
                        redisTemplate.opsForSet().add(userSetKey, commentIds);
                    } else {
                        // 5. 空值占位防穿透
                        redisTemplate.opsForSet().add(userSetKey, -1L);
                    }
                    // 统一设置过期时间，用户历史可以久一点
                    redisTemplate.expire(userSetKey, 7, TimeUnit.DAYS);

                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted during user history rebuild", e);
        }
    }

    @Description("获取一个评论列表id对应的点赞数")
    public Map<Long, Long> queryCommentLikeCounts(List<Long> commentIds) {
        if (CollUtil.isEmpty(commentIds)) {
            return Collections.emptyMap();
        }

        // 批量执行 SCARD 拿点赞数
        List<Object> results = redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            StringRedisConnection stringConn = (StringRedisConnection) connection;
            for (Long commentId : commentIds) {
                String key = USER_LIKE_KEY_PREFIX + commentId;
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

    @Override
    @Cacheable(value = "aiConfigDefault")
    public AiConfig getDefaultConfig() {
        return aiConfigMapper.selectDefault();
    }

    @Description("给评论列表赋值是否点赞过")
    private void assignLikedTop(List<TopCommentVo> commentVos, Long curUserId) {
//        for (TopCommentVo comment : commentVos) {
//            // 尝试重建缓存
//            rebuildLikedCache(comment.getId());
//            // 判断是否已点赞，1为已点赞
//            SetOperations<String, Object> stringObjectSetOperations = redisTemplate.opsForSet();
//            Boolean isMember = stringObjectSetOperations.isMember(LIKE_KEY_PREFIX+comment.getId(), curUserId);
//            Long liked = stringObjectSetOperations.size(key);
//            comment.setLiked(Boolean.TRUE.equals(isMember) ? 1 : 0);
//            if (liked != null) {
//                comment.setLikeCount(liked.intValue() - 1);
//            }
//        }
    }

    @Description("给评论列表赋值是否点赞过")
    private void assignLiked(List<CommentVo> commentVos, Long curUserId) {
//        for (CommentVo comment : commentVos) {
//            // 尝试重建缓存
//            rebuildLikedCache(comment.getId());
//            // 判断是否已点赞，1为已点赞
//            SetOperations<String, Object> stringObjectSetOperations = redisTemplate.opsForSet();
//            Boolean isMember = stringObjectSetOperations.isMember(key, curUserId);
//            Long liked = stringObjectSetOperations.size(key);
//            comment.setLiked(Boolean.TRUE.equals(isMember) ? 1 : 0);
//            if (liked != null) {
//                comment.setLikeCount(liked.intValue() - 1);
//            }
//        }
    }

}
