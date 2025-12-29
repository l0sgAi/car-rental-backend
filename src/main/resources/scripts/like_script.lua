-- KEYS[1]: 用户点赞列表 Key (user:likes:{uid})
-- KEYS[2]: 文章/评论计数 Key (comment:count:{cid})
-- ARGV[1]: 评论 ID (commentId)
-- ARGV[2]: 用户列表过期时间 (秒)
-- ARGV[3]: 计数缓存过期时间 (秒)

-- 1. 检查缓存是否存在 (如果不存在，返回特殊标记让 Java 层去重建)
if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[2]) == 0 then
    return -1 -- 返回 -1 代表缓存失效，需要重建
end

-- 2. 判断用户是否已点赞
local isLiked = redis.call('SISMEMBER', KEYS[1], ARGV[1])

if isLiked == 1 then
    -- === 逻辑分支：取消点赞 ===
    redis.call('SREM', KEYS[1], ARGV[1])
    redis.call('DECR', KEYS[2])

    -- 续期
    redis.call('EXPIRE', KEYS[1], ARGV[2])
    redis.call('EXPIRE', KEYS[2], ARGV[3])

    return 0 -- 返回 0 代表取消点赞成功
else
    -- === 逻辑分支：执行点赞 ===
    redis.call('SADD', KEYS[1], ARGV[1])
    redis.call('INCR', KEYS[2])

    -- 续期
    redis.call('EXPIRE', KEYS[1], ARGV[2])
    redis.call('EXPIRE', KEYS[2], ARGV[3])

    return 1 -- 返回 1 代表点赞成功
end