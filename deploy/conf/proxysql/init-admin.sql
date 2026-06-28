-- =============================================================================
-- OM Platform — ProxySQL 初始化脚本
-- 用法: 挂载到 /etc/proxysql/init.sql，ProxySQL 首次启动时自动执行
--
-- ProxySQL Admin 接口: mysql -h127.0.0.1 -P6032 -uradmin -pradmin
-- ProxySQL SQL 接口:   mysql -h127.0.0.1 -P6033 -uroot -proot
-- =============================================================================

-- 清空可能存在的旧配置（幂等性）
DELETE FROM mysql_servers;
DELETE FROM mysql_group_replication_hostgroups;
DELETE FROM mysql_query_rules;
DELETE FROM mysql_users;
DELETE FROM scheduler;

-- =============================================================================
-- 1. 注册后端 MySQL 节点
-- =============================================================================

-- hostgroup 0: writer (MGR Primary)
INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight, max_connections, comment) VALUES
(0, 'mgr1', 3306, 1, 100, 'MGR writer hostgroup'),
(0, 'mgr2', 3306, 1, 100, 'MGR writer backup'),
(0, 'mgr3', 3306, 1, 100, 'MGR writer backup');

-- hostgroup 1: reader (MGR Secondary + Primary for strong-consistency reads)
INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight, max_connections, comment) VALUES
(1, 'mgr1', 3306, 1, 200, 'MGR reader (包括 Primary，for强一致读)'),
(1, 'mgr2', 3306, 2, 200, 'MGR reader secondary'),
(1, 'mgr3', 3306, 2, 200, 'MGR reader secondary');

-- =============================================================================
-- 2. MGR 监控配置（自动检测主从切换）
-- =============================================================================

INSERT INTO mysql_group_replication_hostgroups (
    writer_hostgroup,          -- hostgroup ID for Primary nodes
    reader_hostgroup,          -- hostgroup ID for Secondary nodes
    backup_writer_hostgroup,   -- 当 Primary 宕机时承接写流量的 hostgroup
    offline_hostgroup,         -- 被标记为 OFFLINE 的节点进入的 hostgroup
    active,                    -- 是否启用
    max_writers,               -- 最大 writer 数量（单主模式 = 1）
    writer_is_also_reader,     -- Primary 是否也参与读
    max_transactions_behind,   -- 最大延迟行数（超过则标记 OFFLINE）
    autoreadonly               -- 是否自动设置 read_only
) VALUES (
    0,    -- writer_hostgroup
    1,    -- reader_hostgroup
    2,    -- backup_writer_hostgroup（在单主模式下很少用）
    3,    -- offline_hostgroup
    1,    -- active
    1,    -- max_writers
    1,    -- writer_is_also_reader（允许主库处理读请求）
    100,  -- max_transactions_behind
    1     -- autoreadonly
);

-- =============================================================================
-- 3. 查询路由规则
-- =============================================================================

-- ProxySQL 规则按 rule_id 顺序匹配，命中后 apply=1 则结束
--
-- 规则策略:
--   1) SELECT ... FOR UPDATE → hostgroup 0（写，能读到未提交数据）
--   2) SELECT ...（不含锁）→ hostgroup 1（读，从从库返回）
--   3) INSERT/UPDATE/DELETE/DDL → hostgroup 0（写）
--   4) 默认 → hostgroup 0（兜底）

-- 规则 1: 带锁的 SELECT 走写库（强一致读）
INSERT INTO mysql_query_rules
    (rule_id, active, match_digest, destination_hostgroup, cache_resultset, apply)
VALUES
    (10, 1, '^SELECT.*FOR UPDATE',          0, 0, 1),
    (11, 1, '^SELECT.*FOR SHARE',           0, 0, 1);

-- 规则 2: 普通 SELECT 走读库（只读事务的 SELECT 也会走这里）
INSERT INTO mysql_query_rules
    (rule_id, active, match_digest, destination_hostgroup, cache_resultset, apply)
VALUES
    (20, 1, '^SELECT',                       1, 0, 1),
    (21, 1, '^WITH ',                        1, 0, 1),
    (22, 1, '^SHOW ',                        1, 0, 1),
    (23, 1, '^DESC ',                        1, 0, 1),
    (24, 1, '^EXPLAIN ',                     1, 0, 1);

-- 规则 99: 非 SELECT 全部走写库（INSERT/UPDATE/DELETE/DDL/SET/etc.）
INSERT INTO mysql_query_rules
    (rule_id, active, match_digest, destination_hostgroup, apply)
VALUES
    (99, 1, '.*',                             0, 1);

-- =============================================================================
-- 4. 用户认证（ProxySQL 管理用户 + 后端用户映射）
-- =============================================================================

-- 后端 MySQL 用户（ProxySQL 认证后转发请求时使用）
INSERT INTO mysql_users (username, password, default_hostgroup, transaction_persistent) VALUES
('root',  'root',  0, 1),
('monitor', 'monitor', 1, 0);

-- Admin 用户（6032 管理接口）
UPDATE global_variables SET variable_value='admin:admin' WHERE variable_name='admin-admin_credentials';
UPDATE global_variables SET variable_value='monitor' WHERE variable_name='mysql-monitor_username';
UPDATE global_variables SET variable_value='monitor' WHERE variable_name='mysql-monitor_password';

-- =============================================================================
-- 5. 监控间隔配置
-- =============================================================================

-- 健康检查间隔（ms）
UPDATE global_variables SET variable_value='1000'  WHERE variable_name='mysql-monitor_connect_interval';   -- 连接检查, 1s
UPDATE global_variables SET variable_value='1000'  WHERE variable_name='mysql-monitor_ping_interval';      -- ping 检查, 1s
UPDATE global_variables SET variable_value='2000'  WHERE variable_name='mysql-monitor_read_only_interval'; -- read_only 检查, 2s
UPDATE global_variables SET variable_value='2000'  WHERE variable_name='mysql-monitor_replication_lag_interval'; -- 延迟检查, 2s

-- =============================================================================
-- 6. 启用配置
-- =============================================================================

LOAD MYSQL SERVERS TO RUNTIME;
LOAD MYSQL QUERY RULES TO RUNTIME;
LOAD MYSQL USERS TO RUNTIME;
LOAD MYSQL VARIABLES TO RUNTIME;
LOAD SCHEDULER TO RUNTIME;
SAVE MYSQL SERVERS TO DISK;
SAVE MYSQL QUERY RULES TO DISK;
SAVE MYSQL USERS TO DISK;
SAVE MYSQL VARIABLES TO DISK;
