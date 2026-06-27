-- seckill_deduct.lua
-- 支付确认扣减（三级库存模型：held -1，代表"预留→已售"）
--
-- KEYS[1]: held key        预留库存  seckill:stock:{aid}:{sku}:held
-- KEYS[2]: hold detail key 持有记录  seckill:hold:{orderNo}
-- ARGV[1]: quantity        扣减数量
-- ARGV[2]: orderNo         订单号
--
-- 返回: {code, message}
--   {1,"OK"}             成功扣减
--   {0,"HOLD_NOT_FOUND"} hold 不存在
--   {0,"HOLD_MISMATCH"}  orderNo 不匹配

local held_key = KEYS[1]
local hold_key = KEYS[2]
local quantity = tonumber(ARGV[1]) or 1
local order_no = ARGV[2]

local held = redis.call('GET', hold_key)
if not held then return {0, 'HOLD_NOT_FOUND'} end
if held ~= order_no then return {0, 'HOLD_MISMATCH'} end

-- 预留 -1（已售由 DB deductDbStock 跟踪）
redis.call('DECRBY', held_key, quantity)
redis.call('DEL', hold_key)
return {1, 'OK'}
