-- KEYS[1]: stock:{sku_id}:frozen
-- ARGV[1]: sku_id
-- ARGV[2]: frozen_key_ttl（冻结时长秒数，-1=永久）
--
-- 设置冻结标记，阻止新预占
-- 返回: {code, message}
--   code: 200=成功（含已冻结幂等）

local frozen_key = KEYS[1]
local ttl = tonumber(ARGV[2])

if redis.call('EXISTS', frozen_key) == 1 then
    return {200, '商品已冻结（幂等）'}
end

redis.call('SET', frozen_key, '1')
if ttl and ttl > 0 then
    redis.call('EXPIRE', frozen_key, ttl)
end

return {200, '商品已冻结'}
