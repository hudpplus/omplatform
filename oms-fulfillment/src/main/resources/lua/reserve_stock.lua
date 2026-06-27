-- KEYS[1]: stock:{sku_id}:available
-- KEYS[2]: stock:{sku_id}:reserved
-- ARGV[1]: sku_id
-- ARGV[2]: quantity（预占数量）
-- ARGV[3]: request_id（幂等键）
-- ARGV[4]: hold_expire_seconds（预占过期时间，默认 900=15min）
--
-- 返回: {code, message, holdId, quantity}
--   code: 200=成功 4001=库存不足 200=幂等

local available_key = KEYS[1]
local reserved_key = KEYS[2]
local sku_id = ARGV[1]
local quantity = tonumber(ARGV[2])
local request_id = ARGV[3]
local hold_expire = tonumber(ARGV[4]) or 900

-- 库存不足检查
local available = redis.call('GET', available_key)
if not available or tonumber(available) < quantity then
    return {4001, '库存不足',
            tostring(available or 0), tostring(quantity)}
end

-- 幂等检查：预占记录已存在则直接返回
local hold_key = 'stock:hold:' .. request_id
if redis.call('EXISTS', hold_key) == 1 then
    return {200, '已预占（幂等）',
            redis.call('GET', hold_key), tostring(quantity)}
end

-- 原子库存移动：available → reserved
redis.call('DECRBY', available_key, quantity)
redis.call('INCRBY', reserved_key, quantity)

-- 记录预占信息
local hold_id = 'HOLD_' .. request_id
redis.call('SET', hold_key, hold_id)
redis.call('EXPIRE', hold_key, hold_expire)

-- 预占详情 Hash（用于 HoldReleaseJob 扫描）
local hold_detail_key = 'stock:hold_detail:' .. hold_id
redis.call('HMSET', hold_detail_key,
    'sku_id', sku_id,
    'quantity', tostring(quantity),
    'request_id', request_id,
    'status', 'RESERVED',
    'expire_at', redis.call('TIME')[1] + hold_expire
)
redis.call('EXPIRE', hold_detail_key, hold_expire + 86400)  -- 多保留 1d

return {200, '预占成功', hold_id, tostring(quantity)}
