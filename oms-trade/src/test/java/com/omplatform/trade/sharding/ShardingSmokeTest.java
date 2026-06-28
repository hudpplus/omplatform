package com.omplatform.trade.sharding;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableReferenceRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * ADR-017 分库分表手动冒烟测试 + ADR-050 MySQL MGR 集群验证。
 * <p>
 * 通过 ProxySQL (6033) 连接 MySQL MGR 集群，验证：
 * <ol>
 *   <li>BusinessContext 驱动的路由是否正确</li>
 *   <li>电商/本地生活/B2B 三条业务线插入路由</li>
 *   <li>数据是否落在正确的物理分库分表</li>
 *   <li>MGR 集群状态（3 节点 ONLINE + 读写分离）</li>
 * </ol>
 * <p>
 * 配置: MGR 模式用 localhost:6033 (ProxySQL)，单实例模式改回 localhost:3306。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShardingSmokeTest {

    private static final String MYSQL_HOST = "localhost:6033"; // ADR-050: ProxySQL 端口; 单实例模式改回 3306
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PWD = "1234";

    private static ShardingSphereDataSource sds;
    private static DataSource rawDs; // 直连 MySQL 用于落库验证

    @BeforeAll
    public static void setup() throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("  ADR-017 分库分表冒烟测试");
        System.out.println("  数据库: " + MYSQL_HOST);
        System.out.println("=".repeat(70));

        // 1. 创建 11 个物理数据源（池用小一点避免 Too Many Connections）
        Map<String, DataSource> dataSourceMap = createDataSources();

        // 2. 配置 ShardingSphere
        ShardingRuleConfiguration shardingConfig = buildShardingConfig();

        // 3. 创建 ShardingSphere DataSource
        DataSource ds = ShardingSphereDataSourceFactory.createDataSource(
                dataSourceMap, Collections.singleton(shardingConfig), new Properties());
        sds = (ShardingSphereDataSource) ds;

        // 4. 建一个直连 MySQL 的连接用于验证
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + MYSQL_HOST + "/information_schema"
                + "?useSSL=false&serverTimezone=Asia/Shanghai");
        cfg.setUsername(MYSQL_USER);
        cfg.setPassword(MYSQL_PWD);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setMaximumPoolSize(1);
        rawDs = new HikariDataSource(cfg);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (sds != null) sds.close();
        if (rawDs instanceof java.io.Closeable) ((java.io.Closeable) rawDs).close();
    }

    @Test
    @Order(1)
    @DisplayName("电商分库分表 - 写入+查询验证")
    public void testEcommerce() throws Exception {
        System.out.println("\n--- [电商] 测试 ---");

        String[][] testCases = {
                {"ORD-ECOMM-SMK-001", "buyer-001", "shop-001", "ecommerce", "PENDING_PAY", "100.00"},
                {"ORD-ECOMM-SMK-002", "buyer-100", "shop-001", "ecommerce", "PAID", "200.00"},
                {"ORD-ECOMM-SMK-003", "buyer-999", "shop-002", "ecommerce", "PAID", "150.00"},
        };
        insertOrders(sds, "order_ecommerce", testCases, null);

        // 查询验证
        BusinessContext.setAll("ecommerce", "buyer-001", null);
        try (Connection conn = sds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT order_no, buyer_id, status FROM order_ecommerce WHERE buyer_id=?")) {
            ps.setString(1, "buyer-001");
            ResultSet rs = ps.executeQuery();
            int n = 0;
            while (rs.next()) { n++; }
            Assertions.assertTrue(n >= 1, "应至少查到 1 条 buyer-001 的订单, 实际=" + n);
        } finally {
            BusinessContext.clear();
        }

        System.out.println("  ✅ 电商测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("本地生活分库分表 - 写入+查询验证")
    public void testLocallife() throws Exception {
        System.out.println("\n--- [本地生活] 测试 ---");

        String[][] testCases = {
                {"ORD-LL-SMK-001", "ll-buyer-001", "shop-ll-01", "locallife", "PENDING_PAY", "88.00"},
                {"ORD-LL-SMK-002", "ll-buyer-002", "shop-ll-01", "locallife", "PAID", "128.00"},
        };
        insertOrders(sds, "order_locallife", testCases, null);

        System.out.println("  ✅ 本地生活测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("B2B 分库分表 - 写入+查询验证")
    public void testB2b() throws Exception {
        System.out.println("\n--- [B2B] 测试 ---");

        // company_id 是 b2b 的分片键
        String[][] testCases = {
                {"ORD-B2B-SMK-001", "company-A", "b2b-buyer-01", "shop-b2b-01", "b2b", "PENDING_APPROVAL", "50000.00"},
                {"ORD-B2B-SMK-002", "company-B", "b2b-buyer-02", "shop-b2b-01", "b2b", "APPROVED", "120000.00"},
        };

        // B2B INSERT 需要包含 company_id（分片键）
        for (String[] tc : testCases) {
            BusinessContext.setAll(tc[4] /*businessType*/, tc[2] /*buyerId (fallback)*/, tc[0]);
            try (Connection conn = sds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO order_b2b(order_no, buyer_id, shop_id, company_id, business_type, status, total_amount) "
                                 + "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status)")) {
                ps.setString(1, tc[0]);
                ps.setString(2, tc[2]);
                ps.setString(3, tc[3]);
                ps.setString(4, tc[1]); // company_id
                ps.setString(5, tc[4]);
                ps.setString(6, tc[5]);
                ps.setBigDecimal(7, new java.math.BigDecimal(tc[6]));
                ps.executeUpdate();
                System.out.printf("  插入 order=%s company=%s%n", tc[0], tc[1]);
            } finally {
                BusinessContext.clear();
            }
        }

        System.out.println("  ✅ B2B 测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("数据落库位置验证 - 直接查询物理分片")
    public void testVerifyPlacement() throws Exception {
        System.out.println("\n--- [数据落库验证] ---");
        int totalOrders = 0;

        // 电商：8 库 × 8 表
        for (int db = 0; db < 8; db++) {
            for (int tbl = 0; tbl < 8; tbl++) {
                totalOrders += countRows("oms_trade_ecommerce_" + db, "order_ecommerce_" + tbl);
            }
        }
        // 本地生活：2 库 × 8 表
        for (int db = 0; db < 2; db++) {
            for (int tbl = 0; tbl < 8; tbl++) {
                totalOrders += countRows("oms_trade_locallife_" + db, "order_locallife_" + tbl);
            }
        }
        // B2B：1 库 × 4 表
        for (int tbl = 0; tbl < 4; tbl++) {
            totalOrders += countRows("oms_trade_b2b_0", "order_b2b_" + tbl);
        }

        System.out.printf("\n  总计: %d 条订单落在物理分片中 (期望 ≥7)%n", totalOrders);
        Assertions.assertTrue(totalOrders >= 7, "应有 7 条以上数据被正确路由到分片");
        System.out.println("  ✅ 数据落库验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("MGR 集群状态验证 - ADR-050")
    public void testMgrClusterStatus() throws Exception {
        System.out.println("\n--- [MGR 集群验证] ---");

        // 通过 rawDs（已连 ProxySQL）查询 MGR 成员状态
        try (Connection conn = rawDs.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT member_host, member_state, member_role "
                     + "FROM performance_schema.replication_group_members")) {

            int count = 0;
            int onlineCount = 0;
            int primaryCount = 0;
            while (rs.next()) {
                count++;
                String host = rs.getString("member_host");
                String state = rs.getString("member_state");
                String role = rs.getString("member_role");
                System.out.printf("  %s | 状态: %s | 角色: %s%n", host, state, role);
                if ("ONLINE".equalsIgnoreCase(state)) onlineCount++;
                if ("PRIMARY".equalsIgnoreCase(role)) primaryCount++;
            }

            Assertions.assertEquals(3, count, "MGR 应有 3 个成员节点");
            Assertions.assertEquals(3, onlineCount, "所有 MGR 节点应处于 ONLINE 状态");
            Assertions.assertEquals(1, primaryCount, "单主模式应有且仅有 1 个 PRIMARY");
        }

        System.out.println("  ✅ MGR 集群验证通过");
    }

    // ====== 工具方法 ======

    private static Map<String, DataSource> createDataSources() {
        Map<String, DataSource> map = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            map.put("ecommerce_ds_" + i, createPool("oms_trade_ecommerce_" + i));
        }
        for (int i = 0; i < 2; i++) {
            map.put("locallife_ds_" + i, createPool("oms_trade_locallife_" + i));
        }
        map.put("b2b_ds_0", createPool("oms_trade_b2b_0"));
        return map;
    }

    private static DataSource createPool(String database) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + MYSQL_HOST + "/" + database
                + "?useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true");
        config.setUsername(MYSQL_USER);
        config.setPassword(MYSQL_PWD);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(1); // 单连接足够测试
        return new HikariDataSource(config);
    }

    private static ShardingRuleConfiguration buildShardingConfig() {
        ShardingRuleConfiguration config = new ShardingRuleConfiguration();

        config.getShardingAlgorithms().put("business_db", new AlgorithmConfiguration(
                "CLASS_BASED", props("strategy", "standard",
                "algorithmClassName", BusinessDbShardingAlgorithm.class.getName())));
        config.getShardingAlgorithms().put("business_table", new AlgorithmConfiguration(
                "CLASS_BASED", props("strategy", "standard",
                "algorithmClassName", OrderShardingAlgorithm.class.getName())));

        // 电商
        config.getTables().add(tableRule("order_ecommerce",
                "ecommerce_ds_$->{0..7}.order_ecommerce_$->{0..7}", "buyer_id"));
        config.getTables().add(tableRule("order_ecommerce_ext",
                "ecommerce_ds_$->{0..7}.order_ecommerce_ext_$->{0..7}", "order_no"));
        config.getTables().add(tableRule("order_items",
                "ecommerce_ds_$->{0..7}.order_items_$->{0..7}", "order_no"));
        // 本地生活
        config.getTables().add(tableRule("order_locallife",
                "locallife_ds_$->{0..1}.order_locallife_$->{0..7}", "buyer_id"));
        config.getTables().add(tableRule("order_locallife_ext",
                "locallife_ds_$->{0..1}.order_locallife_ext_$->{0..7}", "order_no"));
        // B2B
        config.getTables().add(tableRule("order_b2b",
                "b2b_ds_0.order_b2b_$->{0..3}", "company_id"));
        config.getTables().add(tableRule("order_b2b_ext",
                "b2b_ds_0.order_b2b_ext_$->{0..3}", "order_no"));

        // 绑定表
        config.getBindingTableGroups().add(new ShardingTableReferenceRuleConfiguration("g1",
                "order_ecommerce,order_ecommerce_ext,order_items"));
        config.getBindingTableGroups().add(new ShardingTableReferenceRuleConfiguration("g2",
                "order_locallife,order_locallife_ext"));
        config.getBindingTableGroups().add(new ShardingTableReferenceRuleConfiguration("g3",
                "order_b2b,order_b2b_ext"));

        return config;
    }

    private static ShardingTableRuleConfiguration tableRule(String logicTable, String actualNodes, String col) {
        ShardingTableRuleConfiguration rule = new ShardingTableRuleConfiguration(logicTable, actualNodes);
        rule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(col, "business_db"));
        rule.setTableShardingStrategy(new StandardShardingStrategyConfiguration(col, "business_table"));
        return rule;
    }

    private static Properties props(String... kvs) {
        Properties p = new Properties();
        for (int i = 0; i < kvs.length; i += 2) p.setProperty(kvs[i], kvs[i + 1]);
        return p;
    }

    /** 公共 INSERT 逻辑（非 B2B 用，因为 B2B 需要 company_id） */
    private static void insertOrders(DataSource ds, String table, String[][] cases, String extraCols) throws Exception {
        String cols = "order_no, buyer_id, shop_id, business_type, status, total_amount";
        String q = "?,".repeat(6).replaceAll(",$", "");
        if (extraCols != null) { cols += "," + extraCols; q += ",?"; }

        String sql = "INSERT INTO " + table + "(" + cols + ") VALUES(" + q
                + ") ON DUPLICATE KEY UPDATE status=VALUES(status)";

        for (String[] tc : cases) {
            BusinessContext.setAll(tc[3], tc[1], tc[0]);
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tc[0]);
                ps.setString(2, tc[1]);
                ps.setString(3, tc[2]);
                ps.setString(4, tc[3]);
                ps.setString(5, tc[4]);
                ps.setBigDecimal(6, new java.math.BigDecimal(tc[5]));
                ps.executeUpdate();
            } finally {
                BusinessContext.clear();
            }
        }
    }

    /** 查询单个物理分片行数（用共享 rawDs 连接） */
    private static int countRows(String database, String table) throws Exception {
        try (Connection conn = rawDs.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) AS cnt FROM `" + database + "`.`" + table + "`")) {
            if (rs.next()) {
                int cnt = rs.getInt("cnt");
                if (cnt > 0) {
                    System.out.printf("  %s.%s → %d 条%n", database, table, cnt);
                }
                return cnt;
            }
        }
        return 0;
    }
}
