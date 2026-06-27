-- KEYS[1]: stock:{sku_id}:frozen
-- ARGV[1]: sku_id
--
-- 移除冻结标记，恢复可预占
-- 返回: {code, message}
--   code: 200=成功

local frozen_key = KEYS[1]
redis.call('DEL', frozen_key)
return {200, '商品已解冻'}
