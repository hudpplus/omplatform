-- KEYS[1]: stock:{sku_id}:available
-- KEYS[2]: stock:{sku_id}:reserved
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id
-- ARGV[3]: quantity
-- ARGV[4]: request_id（幂等键）
--
-- 返回: {code, message}
--   code: 200=成功 4004=预占不存在 4006=已确认不可释放

local available_key = KEYS[1]
local reserved_key = KEYS[2]
local hold_id = ARGV[2]
local quantity = tonumber(ARGV[3])

local hold_detail_key = 'stock:hold_detail:' .. hold_id
local status = redis.call('HGET', hold_detail_key, 'status')

if not status then
    return {4004, '预占记录不存在'}
end

if status == 'RELEASED' then
    return {200, '已释放（幂等）'}
end

if status == 'CONFIRMED' then
    return {4006, '预占已确认，无法释放，请走 undo_deduct'}
end

-- 原子归还：reserved → available
redis.call('INCRBY', available_key, quantity)
redis.call('DECRBY', reserved_key, quantity)
redis.call('HSET', hold_detail_key, 'status', 'RELEASED')
redis.call('EXPIRE', hold_detail_key, 86400)

return {200, '预占释放成功'}
