-- KEYS[1]: stock:{sku_id}:reserved
-- KEYS[2]: stock:{sku_id}:deducted
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id（预占 ID）
-- ARGV[3]: quantity
-- ARGV[4]: request_id（幂等键）
--
-- 返回: {code, message}
--   code: 200=成功 4004=预占不存在 4005=状态异常

local reserved_key = KEYS[1]
local deducted_key = KEYS[2]
local hold_id = ARGV[2]
local quantity = tonumber(ARGV[3])

-- 查询预占详情
local hold_detail_key = 'stock:hold_detail:' .. hold_id
local status = redis.call('HGET', hold_detail_key, 'status')

if not status then
    return {4004, '预占记录不存在或已过期'}
end

if status ~= 'RESERVED' then
    if status == 'CONFIRMED' then
        return {200, '已确认（幂等）'}
    end
    if status == 'RELEASED' then
        return {4005, '预占已释放，无法确认'}
    end
    return {4005, '预占状态异常', status}
end

-- 原子移动：reserved → deducted
redis.call('DECRBY', reserved_key, quantity)
redis.call('INCRBY', deducted_key, quantity)
redis.call('HSET', hold_detail_key, 'status', 'CONFIRMED')
redis.call('EXPIRE', hold_detail_key, 86400)  -- 延长到 24h

return {200, '确认扣减成功'}
