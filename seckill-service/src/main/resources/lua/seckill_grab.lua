-- seckill_grab.lua
-- 原子抢库存（三级库存模型）
--
-- KEYS[1]: available key   可用库存  seckill:stock:{aid}:{sku}:available
-- KEYS[2]: held key        预留库存  seckill:stock:{aid}:{sku}:held
-- KEYS[3]: hold detail key 持有记录  seckill:hold:{orderNo}
-- ARGV[1]: quantity        抢购数量
-- ARGV[2]: hold ttl        持有过期秒数
-- ARGV[3]: orderNo         订单号
--
-- 三级库存分配：available -1, held +1
-- 同时写入 hold 记录（TTL 兜底）
--
-- 返回: {1, "OK", remainingAvailable}  成功
--       {0, "SOLD_OUT", available}     售罄
--       {0, "ACTIVITY_NOT_READY", 0}   未预热

local available_key = KEYS[1]
local held_key = KEYS[2]
local hold_key = KEYS[3]
local quantity = tonumber(ARGV[1]) or 1
local ttl = tonumber(ARGV[2]) or 900
local order_no = ARGV[3]

local available = tonumber(redis.call('GET', available_key))
if not available then return {0, 'ACTIVITY_NOT_READY', 0} end
if available < quantity then return {0, 'SOLD_OUT', available} end

-- 可用 -1，预留 +1，持有记录
redis.call('DECRBY', available_key, quantity)
redis.call('INCRBY', held_key, quantity)
redis.call('SET', hold_key, order_no, 'EX', ttl)

local remaining = available - quantity
return {1, 'OK', remaining}
