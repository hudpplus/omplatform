#!/bin/bash
# =============================================================================
# OM Platform — MGR 集群引导脚本
# 用法: docker exec mgr1 bash /etc/mysql/init-mgr.sh
# 说明: 首次启动 MGR 集群后执行一次，引导组复制
# =============================================================================

set -e

MYSQL_CMD="mysql -uroot -p${MYSQL_ROOT_PASSWORD:-root} -h127.0.0.1"

echo "=========================================="
echo "  MGR 集群引导"
echo "  $(date)"
echo "=========================================="

# 等待当前节点就绪
echo "[1/6] 等待 MySQL 就绪..."
for i in $(seq 1 30); do
    if $MYSQL_CMD -e "SELECT 1" > /dev/null 2>&1; then
        echo "  ✅ MySQL 就绪"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "  ❌ MySQL 未能就绪，退出"
        exit 1
    fi
    sleep 1
done

# 等待所有 MGR 节点就绪
echo "[2/6] 等待 MGR 节点就绪（mgr2, mgr3）..."
# 利用 docker networking 的 hostname 解析
for NODE in mgr2 mgr3; do
    for i in $(seq 1 60); do
        if mysql -h"$NODE" -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" -e "SELECT 1" > /dev/null 2>&1; then
            echo "  ✅ $NODE 就绪"
            break
        fi
        if [ "$i" -eq 60 ]; then
            echo "  ❌ $NODE 未能就绪，退出"
            exit 1
        fi
        sleep 1
    done
done

# 创建复制用户
echo "[3/6] 创建复制用户..."
$MYSQL_CMD <<'EOSQL'
SET SQL_LOG_BIN=0;
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE, BACKUP_ADMIN ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
SET SQL_LOG_BIN=1;
EOSQL
echo "  ✅ 复制用户已创建"

# 清理之前可能残留的 MGR 配置（用于重跑）
$MYSQL_CMD <<'EOSQL'
STOP GROUP_REPLICATION;
SET GLOBAL group_replication_bootstrap_group=OFF;
EOSQL
echo "  🧹 已清理之前的 MGR 状态"

# 在 Primary 引导 MGR
echo "[4/6] 在 Primary (mgr1) 引导组复制..."
$MYSQL_CMD <<'EOSQL'
SET GLOBAL group_replication_bootstrap_group=ON;
START GROUP_REPLICATION;
SET GLOBAL group_replication_bootstrap_group=OFF;
EOSQL
echo "  ✅ Primary 组复制已启动"

# Secondary 加入
echo "[5/6] Secondary (mgr2, mgr3) 加入组复制..."
for NODE in mgr2 mgr3; do
    mysql -h"$NODE" -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" <<EOSQL
CHANGE MASTER TO MASTER_USER='repl', MASTER_PASSWORD='repl_password' FOR CHANNEL 'group_replication_recovery';
START GROUP_REPLICATION;
EOSQL
    echo "  ✅ $NODE 已加入组复制"
done

# 验证
echo "[6/6] 验证 MGR 集群状态..."
sleep 2
$MYSQL_CMD -e "
SELECT member_host AS '节点', member_port AS '端口', member_state AS '状态'
FROM performance_schema.replication_group_members\G
"

# 创建 ProxySQL 监控用户
$MYSQL_CMD <<'EOSQL'
CREATE USER IF NOT EXISTS 'monitor'@'%' IDENTIFIED BY 'monitor';
GRANT USAGE, REPLICATION CLIENT, SELECT ON *.* TO 'monitor'@'%';
FLUSH PRIVILEGES;
EOSQL
echo "  ✅ ProxySQL 监控用户已创建"

echo ""
echo "=========================================="
echo "  MGR 集群引导完成 ✅"
echo "  主节点: mgr1"
echo "  从节点: mgr2, mgr3"
echo "  连接 ProxySQL: mysql -h127.0.0.1 -P6033 -uroot -proot"
echo "=========================================="
