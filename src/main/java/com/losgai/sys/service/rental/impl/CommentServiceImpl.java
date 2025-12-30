package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.losgai.sys.config.RabbitMQMessageConfig;
import com.losgai.sys.dto.CommentIndexDto;
import com.losgai.sys.dto.CommentLikeCountDto;
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
import com.losgai.sys.vo.LikeInfoVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
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
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Resource(name = "vtExecutor")
    private ExecutorService vtExecutor; // 注入虚拟线程执行器

    private final CommentMapper commentMapper;

    private final LikeMapper likeMapper;

    private final AiConfigMapper aiConfigMapper;

    private final CommentIndexMapper commentIndexMapper;

    private final CommentDetailMapper commentDetailMapper;

    private final RentalOrderMapper rentalOrderMapper;

    private final Sender sender;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisTemplate<String, String> stringRedisTemplate;

    private final RedissonClient redissonClient;

    // 点赞列表set前缀
    public static final String USER_LIKE_KEY_PREFIX = "user:like:list";
    // 点赞计数缓存key前缀
    public static final String LIKE_COUNT_KEY_PREFIX = "comment:like:count:";
    // 评论缓存key前缀
    public static final String COMMENT_CACHE_KEY_PREFIX = "comment:content:";
    // 点赞数的分布式锁前缀
    public static final String LOCK_KEY_PREFIX_COMMENT_LIKE = "lock:comment:like:";
    // 用户点赞列表的分布式锁前缀
    public static final String LOCK_KEY_PREFIX_USER_LIKE = "lock:comment:like:";
    // 点赞上限
    public static final Integer LIKE_LIMIT = 5000;

    // 定义基础缓存时间（例如 24 小时）
    private static final long CACHE_TTL_SECONDS = 60 * 60 * 24;

    private DefaultRedisScript<Long> likeScript;

    @PostConstruct
    public void init() {
        likeScript = new DefaultRedisScript<>();
        likeScript.setLocation(new ClassPathResource("scripts/like_script.lua"));
        likeScript.setResultType(Long.class);
    }

    @Override
    public ResultCodeEnum add(CommentDto comment, Long userId) {
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
        // 执行插入
        commentIndexMapper.insert(commentIndex);
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
            redisTemplate.delete(COMMENT_CACHE_KEY_PREFIX + id);
            redisTemplate.delete(LIKE_COUNT_KEY_PREFIX + id);
        }
        commentMapper.deleteByPrimaryKey(id);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public List<CommentVo> query(String keyWord) {
        // 1. 查询keyword对应的评论内容
        return commentDetailMapper.query(keyWord);
    }

    /**
     *  评论列表异步编排组装：
     *  1. 查询comment_index表，根据carId查询，初始加载10条
     *  2. 根据parentCommentId分组，封装一级和二级评论
     *  3. 注意内容缓存
     * */
    @Override
    public List<CommentVo> queryByCarId(Long carId) {
        // 查询1级评论索引 limit10，带回复数量
        List<CommentIndexDto> indexes = commentIndexMapper.queryByCarIdWithLimit(carId,10);

        if (indexes.isEmpty()) {
            return Collections.emptyList();
        }
        Long curUserId = StpUtil.getLoginIdAsLong();

        // 提取所有涉及的 commentId，用于批量查询
        List<Long> commentIds = indexes.stream().map(CommentIndexDto::getId).collect(Collectors.toList());

        // ================== 异步任务编排开始 ==================

        // 2. 异步任务 A：查询评论内容（带多级缓存策略）
        CompletableFuture<Map<Long, CommentDetail>> contentFuture = CompletableFuture.supplyAsync(() ->
                getCommentContentMap(commentIds), vtExecutor);

        // 3. 异步任务 B：查询点赞数据（点赞数 + 当前用户是否点赞）
        CompletableFuture<Map<Long, LikeInfoVo>> likeFuture = CompletableFuture.supplyAsync(() ->
                getCommentLikeMap(commentIds, curUserId), vtExecutor);

        CompletableFuture.allOf(contentFuture, likeFuture).join();

        // ================== 数据组装 ==================

        Map<Long, CommentDetail> contentMap = contentFuture.getNow(Collections.emptyMap());
        Map<Long, LikeInfoVo> likeMap = likeFuture.getNow(Collections.emptyMap());

        // 5. 分组与封装 (父子评论归位)
        // 找出所有一级评论 (假设 parentId 为 0 或 null 代表一级)
        // 寻找该一级评论下的二级评论 (Reply)
        // 假设有rootParentId指向顶级

        return indexes.stream()
                .map(index -> convertToVo(index, contentMap, likeMap))
                .collect(Collectors.toList());
    }

    @Override
    @Description("分页加载更多1级评论")
    public List<CommentVo> getMore(Long carId) {
        // 查询1级评论索引 limit20，带回复数量，覆盖前10条
        List<CommentIndexDto> indexes = commentIndexMapper.queryByCarId(carId);

        if (indexes.isEmpty()) {
            return Collections.emptyList();
        }
        Long curUserId = StpUtil.getLoginIdAsLong();

        // 提取所有涉及的 commentId，用于批量查询
        List<Long> commentIds = indexes.stream().map(CommentIndexDto::getId).collect(Collectors.toList());

        // ================== 异步任务编排开始 ==================

        // 2. 异步任务 A：查询评论内容（带多级缓存策略）
        CompletableFuture<Map<Long, CommentDetail>> contentFuture = CompletableFuture.supplyAsync(() ->
                getCommentContentMap(commentIds), vtExecutor);

        // 3. 异步任务 B：查询点赞数据（点赞数 + 当前用户是否点赞）
        CompletableFuture<Map<Long, LikeInfoVo>> likeFuture = CompletableFuture.supplyAsync(() ->
                getCommentLikeMap(commentIds, curUserId), vtExecutor);

        CompletableFuture.allOf(contentFuture, likeFuture).join();

        // ================== 数据组装 ==================

        Map<Long, CommentDetail> contentMap = contentFuture.getNow(Collections.emptyMap());
        Map<Long, LikeInfoVo> likeMap = likeFuture.getNow(Collections.emptyMap());

        // 5. 分组与封装 (父子评论归位)
        // 找出所有一级评论 (假设 parentId 为 0 或 null 代表一级)
        // 寻找该一级评论下的二级评论 (Reply)
        // 假设有rootParentId指向顶级

        return indexes.stream()
                .map(index -> convertToVo(index, contentMap, likeMap))
                .collect(Collectors.toList());
    }

    @Override
    @Description("分页加载回复")
    public List<CommentVo> loadReplyByCommentId(Long parentCommentId) {
        // 查询1级评论索引 limit20，带回复数量，覆盖前10条
        List<CommentIndexDto> indexes = commentIndexMapper.queryReplyWithLimit(parentCommentId);

        if (indexes.isEmpty()) {
            return Collections.emptyList();
        }
        Long curUserId = StpUtil.getLoginIdAsLong();

        // 提取所有涉及的 commentId，用于批量查询
        List<Long> commentIds = indexes.stream().map(CommentIndexDto::getId).collect(Collectors.toList());

        // ================== 异步任务编排开始 ==================

        // 2. 异步任务 A：查询评论内容（带多级缓存策略）
        CompletableFuture<Map<Long, CommentDetail>> contentFuture = CompletableFuture.supplyAsync(() ->
                getCommentContentMap(commentIds), vtExecutor);

        // 3. 异步任务 B：查询点赞数据（点赞数 + 当前用户是否点赞）
        CompletableFuture<Map<Long, LikeInfoVo>> likeFuture = CompletableFuture.supplyAsync(() ->
                getCommentLikeMap(commentIds, curUserId), vtExecutor);

        CompletableFuture.allOf(contentFuture, likeFuture).join();

        // ================== 数据组装 ==================

        Map<Long, CommentDetail> contentMap = contentFuture.getNow(Collections.emptyMap());
        Map<Long, LikeInfoVo> likeMap = likeFuture.getNow(Collections.emptyMap());

        // 5. 分组与封装 (父子评论归位)
        // 找出所有一级评论 (假设 parentId 为 0 或 null 代表一级)
        // 寻找该一级评论下的二级评论 (Reply)
        // 假设有rootParentId指向顶级

        return indexes.stream()
                .map(index -> convertToVo(index, contentMap, likeMap))
                .collect(Collectors.toList());
    }

    /**
     * 获取评论内容 Map
     * 优化策略: MultiGet(Redis) -> BatchSelect(DB) -> Pipeline SetEx(Redis)
     */
    private Map<Long, CommentDetail> getCommentContentMap(List<Long> commentIds) {
        Map<Long, CommentDetail> resultMap = new HashMap<>();

        // 1. 构造 Redis Keys
        List<String> keys = commentIds.stream()
                .map(id -> COMMENT_CACHE_KEY_PREFIX + id)
                .collect(Collectors.toList());

        // 2. 批量查询 Redis
        List<Object> cacheResults = redisTemplate.opsForValue().multiGet(keys);

        List<Long> missingIds = new ArrayList<>();

        // 3. 分离命中与未命中
        for (int i = 0; i < commentIds.size(); i++) {
            Long id = commentIds.get(i);
            Object value = (cacheResults != null && cacheResults.size() > i) ? cacheResults.get(i) : null;
            if (value instanceof CommentDetail) {
                resultMap.put(id, (CommentDetail) value);
            } else {
                missingIds.add(id);
            }
        }

        // 4. 处理未命中：查库 + Pipeline 回填
        if (!missingIds.isEmpty()) {
            List<CommentDetail> dbDetails = commentDetailMapper.selectBatchIds(missingIds);

            // 准备回填的数据
            Map<String, CommentDetail> cacheUploadMap = new HashMap<>();
            for (CommentDetail detail : dbDetails) {
                resultMap.put(detail.getId(), detail);
                cacheUploadMap.put(COMMENT_CACHE_KEY_PREFIX + detail.getIndexId(), detail);
            }

            if (!cacheUploadMap.isEmpty()) {
                // 使用 Pipeline + SetEx 替代 multiSet，支持过期时间
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    @SuppressWarnings("unchecked")
                    RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

                    for (Map.Entry<String, CommentDetail> entry : cacheUploadMap.entrySet()) {
                        byte[] keyBytes = keySerializer.serialize(entry.getKey());
                        byte[] valBytes = valueSerializer.serialize(entry.getValue());

                        // 生成随机过期时间：基础时间 + 0~3600秒随机值，防止雪崩
                        long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(3600);

                        if (keyBytes != null && valBytes != null) {
                            connection.stringCommands().setEx(keyBytes, ttl, valBytes);
                        }
                    }
                    return null;
                });
            }
        }

        return resultMap;
    }

    /**
     * 获取点赞信息 Map
     * 优化策略: Pipeline Get(Redis) -> BatchSelect(DB) -> Pipeline SetEx(Redis)
     */
    private Map<Long, LikeInfoVo> getCommentLikeMap(List<Long> commentIds, Long curUserId) {
        Map<Long, LikeInfoVo> resultMap = new HashMap<>();

        // 0. 预处理用户点赞集合
        rebuildUserLikedCache(curUserId);
        String userLikeKey = USER_LIKE_KEY_PREFIX + curUserId;

        // 1. 使用 Pipeline 批量获取 (Count + IsLiked)
        List<Object> pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : commentIds) {
                String likeCountKey = LIKE_COUNT_KEY_PREFIX + id;

                // 命令 A: 获取点赞数
                connection.stringCommands().get(likeCountKey.getBytes());

                // 命令 B: 获取是否点赞 (仅当已登录时查询)
                connection.setCommands().sIsMember(userLikeKey.getBytes(), String.valueOf(id).getBytes());
            }
            return null;
        });

        // 2. 解析结果
        // 步长
        int step = 2;

        List<Long> missingCountIds = new ArrayList<>();

        for (int i = 0; i < commentIds.size(); i++) {
            Long id = commentIds.get(i);
            LikeInfoVo info = new LikeInfoVo();

            // --- 解析点赞数 ---
            Object countObj = pipelineResults.get(i * step);
            if (countObj != null) {
                info.setCount(Integer.parseInt((String) countObj));
            } else {
                missingCountIds.add(id);
                info.setCount(0);
            }

            // --- 解析是否点赞 ---
            // pipelineResults可能会包含null，如果命令执行失败
            Object isMemberObj = pipelineResults.get(i * step + 1);
            info.setLiked(isMemberObj instanceof Boolean && (Boolean) isMemberObj);

            resultMap.put(id, info);
        }

        // 3. 批量查库回填点赞数
        if (!missingCountIds.isEmpty()) {
            List<CommentLikeCountDto> dtos = likeMapper.selectCountsBatch(missingCountIds);

            Map<Long, Integer> dbCounts = dtos.stream()
                    .collect(Collectors.toMap(CommentLikeCountDto::getCommentId, CommentLikeCountDto::getCount));

            Map<String, String> cacheUpdateMap = new HashMap<>();

            for (Long missingId : missingCountIds) {
                Integer dbCount = dbCounts.getOrDefault(missingId, 0);

                // 更新返回结果
                resultMap.get(missingId).setCount(dbCount);

                // 准备缓存数据
                cacheUpdateMap.put(LIKE_COUNT_KEY_PREFIX + missingId, String.valueOf(dbCount));
            }

            if (!cacheUpdateMap.isEmpty()) {
                // 【关键优化】使用 Pipeline + SetEx 回填点赞数
                stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (Map.Entry<String, String> entry : cacheUpdateMap.entrySet()) {
                        byte[] keyBytes = entry.getKey().getBytes();
                        byte[] valBytes = entry.getValue().getBytes();

                        // 点赞数属于热点数据，过期时间可以稍短，并加随机
                        long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(3600);

                        connection.stringCommands().setEx(keyBytes, ttl, valBytes);
                    }
                    return null;
                });
            }
        }

        return resultMap;
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
        sender.sendLikeSync(RabbitMQMessageConfig.EXCHANGE_NAME, RabbitMQMessageConfig.ROUTING_KEY_LIKE,like);
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
                    Integer dbCount = likeMapper.countByCommentId(commentId);
                    dbCount = dbCount == null ? 0 : dbCount;

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

    private CommentVo convertToVo(CommentIndexDto index,
                                  Map<Long, CommentDetail> contentMap,
                                  Map<Long, LikeInfoVo> likeMap) {
        CommentVo vo = new CommentVo();
        vo.setId(index.getId());
        vo.setUserId(index.getUserId());
        vo.setUsername(index.getUsername());
        vo.setAvatar(index.getAvatar());
        vo.setCarId(index.getCarId());
        vo.setParentCommentId(index.getParentCommentId());
        vo.setFollowCommentId(index.getFollowCommentId());
        // 点赞数和点赞状态封装
        vo.setLikeCount(likeMap.get(index.getId()) == null ? 0 : likeMap.get(index.getId()).getCount());
        vo.setLiked(likeMap.get(index.getId()) == null ? 0 : likeMap.get(index.getId()).isLiked()? 1:0);
        // 评论内容封装
        if(contentMap.containsKey(index.getId())){
            CommentDetail detail = contentMap.get(index.getId());
            vo.setScore(detail.getScore());
            vo.setContent(detail.getContent());
            vo.setExtraImages(detail.getExtraImages());
        }
        vo.setCreateTime(index.getCreateTime());
        return vo;
    }

}
