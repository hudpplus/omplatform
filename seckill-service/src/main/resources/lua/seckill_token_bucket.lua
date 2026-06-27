-- seckill_token_bucket.lua
-- Redis 令牌桶限流（带浮点精确度补水）。
--
-- KEYS[1]: seckill:token_bucket:{activityId}
-- ARGV[1]: capacity        桶容量（最大令牌数）
-- ARGV[2]: refillRate      令牌补充速率（个/秒，支持小数）
-- ARGV[3]: requestedTokens 本次请求消耗的令牌数（默认 1）
-- ARGV[4]: currentTimeMs   当前时间戳（调用方传入，Lua 内不调 os.time）
--
-- 返回: {1, remainingTokens} 允许通过
--       {0, availableTokens} 拒绝（令牌不足）

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3]) or 1
local now = tonumber(ARGV[4])

-- 读取桶状态
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens, lastRefill

if bucket[1] then
    tokens = tonumber(bucket[1])
    lastRefill = tonumber(bucket[2])
else
    tokens = capacity
    lastRefill = now
end

-- 计算时间窗口内应补充的令牌数
local elapsed = now - lastRefill
if elapsed > 0 and refillRate > 0 then
    local refill = refillRate * elapsed / 1000.0
    if refill > 0.0001 then
        tokens = math.min(capacity, tokens + refill)
        lastRefill = now
    end
end

-- 判断是否允许通过
if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    -- TTL = 桶满水时间的 2 倍 + 兜底 1s
    local ttl = math.ceil(capacity / math.max(refillRate, 0.001)) * 2 + 1
    redis.call('EXPIRE', key, ttl)
    return {1, math.floor(tokens)}
else
    -- 即使拒绝也要更新 last_refill，防止时间窗口浪费
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    local ttl = math.ceil(capacity / math.max(refillRate, 0.001)) * 2 + 1
    redis.call('EXPIRE', key, ttl)
    return {0, math.floor(tokens)}
end
