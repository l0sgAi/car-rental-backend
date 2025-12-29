package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.losgai.sys.config.RabbitMQMessageConfig;
import com.losgai.sys.dto.CommentIndexDto;
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
import com.losgai.sys.vo.TopCommentVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
        return commentDetailMapper.query(keyWord);
    }

    /**
     *  评论列表异步编排组装：
     *  1. 查询comment_index表，根据carId查询，初始加载10条
     *  2. 根据parentCommentId分组，封装一级和二级评论
     *  3. 注意内容缓存
     * */
    @Override
    public List<TopCommentVo> queryByCarId(Long carId) {
        // 查询评论索引 limit10
        List<CommentIndexDto> indexes = commentIndexMapper.queryByCarIdWithLimit(carId);

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
                .filter(index -> index.getParentCommentId() == 0)
                .map(index -> {
                    TopCommentVo vo = convertToVo(index, contentMap, likeMap);
                    // 寻找该一级评论下的二级评论 (Reply)
                    List<CommentVo> children = indexes.stream()
                            .filter(child ->
                                    index.getId().equals(child.getParentCommentId())) // 假设有rootParentId指向顶级
                            .map(child ->
                                    convertToVo(child, contentMap, likeMap).toChild())
                            .collect(Collectors.toList());
                    vo.setChildren(children);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private TopCommentVo convertToVo(CommentIndexDto index,
                                     Map<Long, CommentDetail> contentMap,
                                     Map<Long, LikeInfoVo> likeMap) {
        TopCommentVo vo = new TopCommentVo();
        vo.setId(index.getId());
        vo.setUserId(index.getUserId());
        vo.setUsername(index.getUsername());
        vo.setAvatar(index.getAvatar());
        vo.setCarId(index.getCarId());
        vo.setParentCommentId(index.getParentCommentId());
        vo.setFollowCommentId(index.getFollowCommentId());
        vo.setCarName(index.getCarName());
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

    /**
     * 获取评论内容 Map (Redis -> DB -> Redis)
     */
    private Map<Long, CommentDetail> getCommentContentMap(List<Long> commentIds) {
        // TODO: pipeline 和DB批量查询优化
        Map<Long, CommentDetail> resultMap = new HashMap<>();

        // 简单实现：循环查缓存
        for (Long id : commentIds) {
            String cacheKey = COMMENT_CACHE_KEY_PREFIX + id;
            CommentDetail commentDetail = (CommentDetail) redisTemplate.opsForValue().get(cacheKey);
            if (commentDetail == null) {
                commentDetail = commentDetailMapper.selectByPrimaryKey(id);
                redisTemplate.opsForValue().set(COMMENT_CACHE_KEY_PREFIX + id, commentDetail);
                resultMap.put(id, commentDetail);
            }
        }
        return resultMap;
    }

    /**
     * 获取点赞信息 Map (Redis -> DB -> Redis)
     */
    private Map<Long, LikeInfoVo> getCommentLikeMap(List<Long> commentIds, Long curUserId) {
        // TODO: pipeline 批量查询优化
        Map<Long, LikeInfoVo> resultMap = new HashMap<>();
        // 使用 Set 存储点赞该用户点赞的评论ID
        String userLikeKey = USER_LIKE_KEY_PREFIX + curUserId;
        for (Long id : commentIds) {
            LikeInfoVo info = new LikeInfoVo();
            String likeCountKey = LIKE_COUNT_KEY_PREFIX + id;
            // 缓存击穿/不存在：检查/重建缓存
            rebuildCommentCountCache(id);
            // 1. 获取点赞数
            String countStr = stringRedisTemplate.opsForValue().get(likeCountKey);
            if (countStr != null) {
                info.setCount(Integer.parseInt(countStr));
            }

            // 2. 获取当前用户是否点赞
            if (curUserId != null) {
                rebuildUserLikedCache(curUserId);
                // 判断 Set 中是否存在 userId
                Boolean isMember = stringRedisTemplate.opsForSet().isMember(userLikeKey, String.valueOf(id));
                // 注意：如果 Redis key 不存在，isMember 也是 false。
                // 严谨逻辑：如果 countKey 存在但 userLikeKey 不存在，说明可能是冷数据被驱逐，需查库判断状态
                if (isMember != null && isMember) {
                    info.setLiked(true);
                }
            } else {
                info.setLiked(false);
            }
            resultMap.put(id, info);
        }
        return resultMap;
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

        // 收集评论ID
        List<Long> parentIds = topComments.stream()
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
