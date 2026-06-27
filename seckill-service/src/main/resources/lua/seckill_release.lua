-- seckill_release.lua
-- 释放秒杀库存（三级库存模型：held -1, available +1）
--
-- KEYS[1]: available key   可用库存  seckill:stock:{aid}:{sku}:available
-- KEYS[2]: held key        预留库存  seckill:stock:{aid}:{sku}:held
-- KEYS[3]: hold detail key 持有记录  seckill:hold:{orderNo}
-- ARGV[1]: quantity        释放数量
-- ARGV[2]: orderNo         订单号
--
-- 返回: {code, message}
--   {1,"OK"}             正常释放（hold 存在且匹配）
--   {1,"OK_EXPIRED_HOLD"} hold 已过期，但库存已归还
--   {0,"HOLD_MISMATCH"}   orderNo 不匹配（异常，不释放）

local available_key = KEYS[1]
local held_key = KEYS[2]
local hold_key = KEYS[3]
local quantity = tonumber(ARGV[1]) or 1
local order_no = ARGV[2]

-- 校验持有记录
local held = redis.call('GET', hold_key)
if not held then
    -- hold 已过期（TTL 自动删除），但 available 和 held 是 DECR/INCR 过的，
    -- Redis 不会自动调整。必须归还：available +1, held -1。
    redis.call('INCRBY', available_key, quantity)
    local current_held = tonumber(redis.call('GET', held_key) or 0)
    redis.call('SET', held_key, math.max(0, current_held - quantity))
    return {1, 'OK_EXPIRED_HOLD'}
end
if held ~= order_no then
    return {0, 'HOLD_MISMATCH'}
end

-- 正常释放：available +1, held -1
redis.call('INCRBY', available_key, quantity)
redis.call('DECRBY', held_key, quantity)
redis.call('DEL', hold_key)
return {1, 'OK'}
