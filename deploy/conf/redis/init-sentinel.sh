#!/bin/bash
# =============================================================================
# OM Platform — Redis Sentinel 引导脚本
# 用法: docker exec redis-master bash /etc/redis/init-sentinel.sh
# 说明: 首次启动 Redis Sentinel 后执行，验证集群状态
# =============================================================================

set -e

REDIS_CMD="redis-cli -a omplatform"

echo "=========================================="
echo "  Redis Sentinel 集群引导验证"
echo "  $(date)"
echo "=========================================="

echo ""
echo "--- 1. 验证 Master ---"
INFO=$($REDIS_CMD -h redis-master -p 6379 INFO replication 2>/dev/null | grep role)
echo "  redis-master:6379 → $INFO"

echo ""
echo "--- 2. 验证 Replica ---"
for NODE in redis-replica1:6380 redis-replica2:6381; do
    HOST=${NODE%:*}
    PORT=${NODE#*:}
    ROLE=$($REDIS_CMD -h "$HOST" -p "$PORT" INFO replication 2>/dev/null | grep role)
    echo "  $NODE → $ROLE"
done

echo ""
echo "--- 3. 验证 Sentinel ---"
SENTINEL_INFO=$($REDIS_CMD -h sentinel1 -p 26379 SENTINEL master mymaster 2>/dev/null \
    | grep -E "^(ip|port|num-slaves|status)" | tr '\r\n' ' ')
echo "  sentinel mymaster → $SENTINEL_INFO"

for NODE in sentinel1:26379 sentinel2:26380 sentinel3:26381; do
    HOST=${NODE%:*}
    PORT=${NODE#*:}
    RESULT=$($REDIS_CMD -h "$HOST" -p "$PORT" SENTINEL REPLICAS mymaster 2>/dev/null \
        | grep -c "name" || echo "0")
    echo "  $NODE → replicas: $RESULT"
done

echo ""
echo "--- 4. 验证 Sentinel 高可用 ---"
SENTINEL_MASTER=$($REDIS_CMD -h sentinel1 -p 26379 SENTINEL get-master-addr-by-name mymaster 2>/dev/null)
echo "  当前 Master: $SENTINEL_MASTER"

echo ""
echo "=========================================="
echo "  Redis Sentinel 集群验证完成 ✅"
echo "  连接应用:"
echo "    spring.redis.sentinel.master=mymaster"
echo "    spring.redis.sentinel.nodes=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381"
echo "=========================================="
