-- KEYS[1]: stock:{sku_id}:available
-- KEYS[2]: stock:{sku_id}:deducted
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id
-- ARGV[3]: quantity
-- ARGV[4]: request_id（幂等键）
--
-- 返回: {code, message}
--   code: 200=成功 4004=预占不存在 4005=未确认不可撤销

local available_key = KEYS[1]
local deducted_key = KEYS[2]
local hold_id = ARGV[2]
local quantity = tonumber(ARGV[3])

local hold_detail_key = 'stock:hold_detail:' .. hold_id
local status = redis.call('HGET', hold_detail_key, 'status')

if not status then
    return {4004, '预占记录不存在'}
end

if status == 'UNDONE' then
    return {200, '已撤销（幂等）'}
end

if status ~= 'CONFIRMED' then
    return {4005, '预占未确认，不可执行 undo_deduct', status}
end

-- 原子归还：deducted → available
redis.call('INCRBY', available_key, quantity)
redis.call('DECRBY', deducted_key, quantity)
redis.call('HSET', hold_detail_key, 'status', 'UNDONE')
redis.call('EXPIRE', hold_detail_key, 86400)

return {200, '扣减已撤销'}
