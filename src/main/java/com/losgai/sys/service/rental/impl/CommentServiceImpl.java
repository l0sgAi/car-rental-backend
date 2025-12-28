package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Description;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.*;
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

    // 点赞列表Zset前缀
    private final String LIKE_KEY_PREFIX = "comment:like:list";
    // 点赞计数缓存key前缀
    private final String LIKE_COUNT_KEY_PREFIX = "comment:like:count:";
    // 需要向数据库同步点赞的评论数据id集合
    private final String LIKE_SYNC_SET = "comment:like:sync";
    // 评论缓存key前缀
    private final String COMMENT_CACHE_KEY_PREFIX = "comment:content::";
    // 点赞数定时同步的分布式锁前缀
    private static final String LOCK_KEY_PREFIX_COMMENT_LIKE = "lock:comment:like:";
    // Unlike Log
    private static final String UNLIKE_LOG = "UNLIKE_LOG:";
    // 点赞上限
    private static final Integer LIKE_LIMIT = 5000;

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
    @Description("点赞/取消点赞")
    public ResultCodeEnum like(Long commentId, Long userId) {
        if (commentId == null || userId == null) {
            return ResultCodeEnum.DATA_ERROR;
        }

        String zsetKey = LIKE_KEY_PREFIX + commentId;
        String countKey = LIKE_COUNT_KEY_PREFIX + commentId;
        // 新增：定义取消日志的 Key
        String unlikeKey = UNLIKE_LOG + commentId;

        // 1. 重建缓存逻辑
        rebuildLikedCache(commentId, zsetKey, countKey);

        // 2. 判断是否点赞
        boolean isLiked = isUserLiked(commentId, userId, zsetKey);

        if (isLiked) {
            // ==================== 执行取消点赞逻辑 ====================

            // 2.1 Redis ZSet 移除
            redisTemplate.opsForZSet().remove(zsetKey, userId);

            // 2.2 Redis Count 减 1
            redisTemplate.opsForValue().decrement(countKey);

            // 2.3 写入取消日志 (Unlike Log)
            redisTemplate.opsForSet().add(unlikeKey, userId);
        } else {
            // ==================== 执行点赞逻辑 ====================

            // 3.1 Redis ZSet 新增
            redisTemplate.opsForZSet().add(zsetKey, userId, System.currentTimeMillis());

            // 3.2 滚动删除点赞列表
            Long size = redisTemplate.opsForZSet().zCard(zsetKey);
            if (size != null && size > LIKE_LIMIT) {
                redisTemplate.opsForZSet().removeRange(zsetKey, 0, size - LIKE_LIMIT - 1);
            }

            // 3.3 Redis Count 加 1
            redisTemplate.opsForValue().increment(countKey);

            // 3.4 从取消日志中移除
            redisTemplate.opsForSet().remove(unlikeKey, userId);
        }

        // ==================== 公共收尾逻辑 ====================

        // 4. 续期
        // 只要有操作，就对相关Key全部续期 24h
        redisTemplate.expire(zsetKey, 24, TimeUnit.HOURS);
        redisTemplate.expire(countKey, 24, TimeUnit.HOURS);
        redisTemplate.expire(unlikeKey, 24, TimeUnit.HOURS);

        // 5. 加入点赞数待同步列表
        redisTemplate.opsForSet().add(LIKE_SYNC_SET, commentId);

        return ResultCodeEnum.SUCCESS;
    }

    /**
     * 判断用户是否已赞
     * 策略：Redis ZSet (热数据) -> DB (兜底)
     */
    private boolean isUserLiked(Long commentId, Long userId, String zsetKey) {
        // 1. 先查 ZSet
        Double score = redisTemplate.opsForZSet().score(zsetKey, userId);
        if (score != null) {
            return true;
        }

        // 2. 如果 ZSet 里没有，不代表没赞 (可能被挤出去了)
        // 只有当 ZSet 没满时，不在 ZSet 里才等于没赞；如果满了，必须查 DB
        Long count = likeMapper.countByCommentIdAndUserId(commentId);
        return count != null && count > 0;
    }


    @Description("根据commentId重建点赞缓存")
    private void rebuildLikedCache(Long commentId, String zsetKey, String countKey) {
        // 如果计数缓存和列表缓存都存在，直接返回
        if (redisTemplate.hasKey(countKey) && redisTemplate.hasKey(zsetKey)) {
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX_COMMENT_LIKE + commentId);
        try {
            // 尝试加锁，等待时间5s，上锁后10s自动解锁
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 双重检查 (Double Check Lock)
                    if (redisTemplate.hasKey(countKey) && redisTemplate.hasKey(zsetKey)) {
                        return;
                    }

                    // === 重建逻辑 ===
                    log.info("为commentId重建缓存: {}", commentId);

                    // 1. 查 DB 总数
                    Long dbCount = likeMapper.countByCommentId(commentId);
                    dbCount = dbCount == null ? 0L : dbCount;

                    // 4. 查 DB 最新点赞用户列表 (Limit 5000)
                    List<Like> recentLikes = likeMapper.selectLatestLikes(commentId, LIKE_LIMIT);

                    if (CollUtil.isNotEmpty(recentLikes)) {
                        Set<ZSetOperations.TypedTuple<Object>> tuples = new HashSet<>();
                        for (Like like : recentLikes) {
                            // score 使用时间戳
                            tuples.add(new DefaultTypedTuple<>(like.getUserId(), (double) like.getCreateTime().getTime()));
                        }
                        redisTemplate.opsForZSet().add(zsetKey, tuples);
                        redisTemplate.expire(zsetKey, 24, TimeUnit.HOURS);
                    } else {
                        // 防止缓存穿透，可以设一个空值的标志，或者不设ZSet(因为isLiked逻辑有DB兜底)
                        // 这里选择存一个特殊的占位符，或者单纯设个过期时间
                        redisTemplate.opsForZSet().add(zsetKey, -1L, 0); // 占位
                        redisTemplate.expire(zsetKey, 15, TimeUnit.MINUTES);
                    }
                    // 3. 设置 Redis Count
                    redisTemplate.opsForValue().set(countKey, dbCount, 24, TimeUnit.HOURS); // 加上过期时间防止死数据

                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock interrupted", e);
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

    @Override
    @Cacheable(value = "aiConfigDefault")
    public AiConfig getDefaultConfig() {
        return aiConfigMapper.selectDefault();
    }

    @Scheduled(fixedDelay = 5000) // 建议改用 fixedDelay，跑完上一轮再等5秒，防止积压
    @Transactional
    public void syncLikeToDB() {
        // 1. 每次只取 50-100 个需要同步的 ID，避免一次处理太多导致 OOM
        // 使用 SPOP 弹出并移除，保证原子性，也不需要再手动删除了
        List<Object> idObjects = redisTemplate.opsForSet().pop(LIKE_SYNC_SET, 50);

        if (CollUtil.isEmpty(idObjects)) {
            return;
        }

        Set<Long> commentIds = idObjects.stream().map(Object::toString).map(Long::valueOf).collect(Collectors.toSet());

        // 2. 不需要加分布式锁（RedissonMultiLock）。
        // 理由：同步任务只负责"追加"和"指定删除"。
        // 即使同步过程中用户又点赞了，只是多一次 Insert Ignore，无副作用。
        // 即使同步过程中用户取消点赞了，会写入 UNLIKE_LOG，下次任务会处理。

        // 3. 准备数据容器
        List<Like> batchInsertList = new ArrayList<>();
        List<Like> batchDeleteList = new ArrayList<>();
        List<CommentIndex> batchUpdateCountList = new ArrayList<>();

        // 4. 使用 Pipeline 批量读取数据 (ZSet列表, Count)
        List<Object> pipelineResult = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long cid : commentIds) {
                String zsetKey = LIKE_KEY_PREFIX + cid;
                String countKey = LIKE_COUNT_KEY_PREFIX + cid;
                String unlikeKey = UNLIKE_LOG + cid;

                connection.zSetCommands().zRange(zsetKey.getBytes(), 0, -1); // 0. ZSet members
                connection.stringCommands().get(countKey.getBytes());        // 1. Count
                connection.setCommands().sMembers(unlikeKey.getBytes());     // 2. Unlike Log
                connection.keyCommands().del(unlikeKey.getBytes());          // 3. 读完立刻删除 Unlike Log (防止重复删)
            }
            return null;
        });

        // 5. 解析结果
        int index = 0;
        for (Long cid : commentIds) {
            // --- 处理新增 ---
            Set<String> redisZSetMembers = (Set<String>) pipelineResult.get(index++);
            if (CollUtil.isNotEmpty(redisZSetMembers)) {
                for (String uidStr : redisZSetMembers) {
                    // 构造对象，后续批量 INSERT IGNORE
                    long uid = Long.parseLong(uidStr);
                    if (uid > 0){
                        batchInsertList.add(new Like(null, uid, cid));
                    }
                }
            }

            // --- 处理计数 ---
            Object countObj = pipelineResult.get(index++);
            if (countObj != null) {
                // Redis Count 存在则信 Redis，否则不更新 DB Count
                int count = Integer.parseInt(countObj.toString());
                CommentIndex commentIndex = new CommentIndex();
                commentIndex.setId(cid);
                commentIndex.setHotScore(count);
                batchUpdateCountList.add(commentIndex);
            }

            // --- 处理删除 ---
            Set<String> unlikeMembers = (Set<String>) pipelineResult.get(index++);
            index++; // 跳过 DEL 命令的返回值

            if (CollUtil.isNotEmpty(unlikeMembers)) {
                for (String uidStr : unlikeMembers) {
                    batchDeleteList.add(new Like(null, Long.valueOf(uidStr), cid));
                }
            }
        }

        // 6. 执行数据库操作
        if (CollUtil.isNotEmpty(batchInsertList)) {
            // 利用数据库唯一索引(comment_id, user_id) 避免重复插入
            likeMapper.batchInsert(batchInsertList);
        }

        if (CollUtil.isNotEmpty(batchDeleteList)) {
            // 批量软删除 WHERE (comment_id = ? AND user_id = ?)
            likeMapper.batchSoftDelete(batchDeleteList);
        }

        if (CollUtil.isNotEmpty(batchUpdateCountList)) {
            commentIndexMapper.batchUpdateLikeCountMap(batchUpdateCountList);
        }

        log.info("同步完成: {} comments, Insert: {}, Delete: {}, UpdateCount: {}",
                commentIds.size(), batchInsertList.size(), batchDeleteList.size(), batchUpdateCountList.size());
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
