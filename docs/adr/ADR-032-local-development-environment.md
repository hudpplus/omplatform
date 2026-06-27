# ADR-032: 本地开发环境

## 状态
已接受

## 背景

### 现状分析

当前项目无任何本地开发环境设计。开发者 onboarding 需手动安装和配置 6 种中间件，依赖共享 DEV 环境进行调试。

**存在的问题**：

1. **Onboarding 成本高**：新开发者需逐一安装 OceanBase / ES / Redis / RocketMQ / Nacos / Apollo，无统一启动脚本，耗时 1-3 天
2. **依赖共享环境**：所有开发者共用 DEV 集群，互相干扰；网络离线时完全无法工作
3. **无外部依赖 Mock**：支付网关、物流商接口依赖真实沙箱环境，无法本地模拟异常场景
4. **无测试数据**：本地启动后数据库为空，新开发者需手动构造测试数据
5. **OceanBase 本地部署重**：OB 容器需 8G+ 内存，低配开发机无法运行
6. **调试困难**：跨服务调用链追踪需依赖共享 SkyWalking，本地无法模拟全链路

### 目标

1. 提供 `docker compose up -d` 一键启动所有中间件
2. MySQL 替代 OceanBase 本地开发，降低资源消耗
3. Testcontainers + WireMock 集成测试基类
4. 测试数据播种脚本
5. 本地开发配置模板 + Apollo 降级模式

## 决策

### 方案对比：本地 OceanBase 策略

| 维度 | 方案 A：完整 OB 容器 | 方案 B：MySQL 8.0 替代 | 方案 C：Remote OB 代理 |
|------|-------------------|----------------------|---------------------|
| 资源消耗 | 8G+ RAM，高 | < 1G RAM，低 | 无本地消耗 |
| OB 特性支持 | 全部 | 部分（分区、租户需 `@Profile("ob")`） | 全部 |
| 离线可用 | 是 | 是 | 否（需网络） |
| 启动速度 | 5-10min | 10s | N/A |
| 一致性风险 | 无 | SQL 方言差异可能导致 Prod 问题 | 无 |

**选择：方案 B（MySQL 8.0 替代）+ 方案 C 兜底**

本地开发默认使用 MySQL 8.0 容器；OB 特有功能（如 `ALTER TABLE ... ALGORITHM=INSTANT`、`OUTLINE`）加 `@Profile("ob")` 注解，CI 和 PRE/PROD 环境使用真实 OB。

### MySQL 兼容性策略

```java
// 自动检测数据库类型，切换 SQL
@Component
public class DatabaseProfileConfig {
    
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    public boolean isOceanBase() {
        return jdbcUrl.contains("oceanbase") || jdbcUrl.contains(":2883");
    }
}

// MyBatis 动态 SQL：通过 databaseId 区分
// 在 OceanBase 环境使用 OB 特有语法，MySQL 环境使用 MySQL 语法
```

## 详细设计

### 1. docker-compose 中间件栈

**docker-compose.yml**：

```yaml
version: '3.8'

services:
  # ─── 数据库 ───
  mysql:
    image: mysql:8.0
    container_name: omplatform-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: omplatform_dev
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 1G

  # ─── 缓存 ───
  redis:
    image: redis:7.2
    container_name: omplatform-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 256M

  # ─── 消息队列 ───
  rocketmq-namesrv:
    image: apache/rocketmq:5.1
    container_name: omplatform-rocketmq-ns
    command: sh mqnamesrv
    ports:
      - "9876:9876"
    deploy:
      resources:
        limits:
          memory: 512M

  rocketmq-broker:
    image: apache/rocketmq:5.1
    container_name: omplatform-rocketmq-broker
    depends_on:
      - rocketmq-namesrv
    environment:
      NAMESRV_ADDR: rocketmq-namesrv:9876
    ports:
      - "10909:10909"
      - "10911:10911"
    volumes:
      - rocketmq-data:/home/rocketmq/store
    deploy:
      resources:
        limits:
          memory: 1G

  # ─── 搜索引擎 ───
  elasticsearch:
    image: elasticsearch:8.11
    container_name: omplatform-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9200/_cluster/health"]
      interval: 30s
      timeout: 10s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 1G

  # ─── 注册与配置中心 ───
  nacos:
    image: nacos/nacos-server:2.2
    container_name: omplatform-nacos
    environment:
      MODE: standalone
      MYSQL_SERVICE_DB_NAME: omplatform_dev
    ports:
      - "8848:8848"
    deploy:
      resources:
        limits:
          memory: 512M

  apollo:
    image: apolloconfig/apollo-quick-start:2.0
    container_name: omplatform-apollo
    depends_on:
      mysql:
        condition: service_healthy
    ports:
      - "8070:8070"
    environment:
      - SPRING_DATABASE_URL=jdbc:mysql://mysql:3306/ApolloConfigDB?characterEncoding=utf8
      - SPRING_DATABASE_USERNAME=root
      - SPRING_DATABASE_PASSWORD=root123
    deploy:
      resources:
        limits:
          memory: 512M

  # ─── 模拟外部依赖 ───
  wiremock:
    image: wiremock/wiremock:3.3
    container_name: omplatform-wiremock
    ports:
      - "18080:8080"
    volumes:
      - ./wiremock/__files:/home/wiremock/__files
      - ./wiremock/mappings:/home/wiremock/mappings
    deploy:
      resources:
        limits:
          memory: 128M

volumes:
  mysql-data:
  redis-data:
  rocketmq-data:
  es-data:
```

**启动命令**：

```bash
# 启动全部中间件
docker compose up -d

# 启动指定中间件（前端开发只需 MySQL + Redis）
docker compose up -d mysql redis

# 停止全部
docker compose down

# 重置数据（清空所有持久化卷）
docker compose down -v
```

### 2. 测试数据播种

**种子脚本 data-dev.sql**：

```sql
-- 订单基础数据
INSERT INTO `order` (id, order_no, buyer_id, seller_id, status, business_type, total_amount, gmt_create)
VALUES 
(1, 'DEV2026010100001', 1001, 2001, 'PAID', 'ecommerce', 9999, NOW()),
(2, 'DEV2026010100002', 1002, 2001, 'REFUNDING', 'ecommerce', 19900, NOW()),
(3, 'DEV2026010100003', 1003, 2002, 'PENDING_PAY', 'locallife', 5000, NOW());

-- 用户角色
INSERT INTO auth_user_role (user_id, role, biz_scope, status)
VALUES 
(1001, 'merchant', 'ecommerce', 'ACTIVE'),
(2001, 'cs', 'all', 'ACTIVE'),
(9999, 'super_admin', 'all', 'ACTIVE');
```

**WireMock Mock 配置**（`wiremock/mappings/payment-success.json`）：

```json
{
  "request": {
    "method": "POST",
    "url": "/gateway/pay/unifiedorder"
  },
  "response": {
    "status": 200,
    "jsonBody": {
      "return_code": "SUCCESS",
      "result_code": "SUCCESS",
      "out_trade_no": "{{request.body.out_trade_no}}",
      "transaction_id": "MOCK2026010100000001",
      "total_fee": "{{request.body.total_fee}}"
    },
    "headers": {
      "Content-Type": "application/json"
    },
    "transformers": ["response-template"]
  }
}
```

**XXL-Job 播种任务**（可选）：

```java
@Component
public class DataSeedJob {
    
    @XxlJob("dataSeedJob")
    public ReturnT<String> seed(String param) {
        // 在 DEV 环境初始化测试数据
        if (!"dev".equals(env)) return ReturnT.SUCCESS;
        
        // 按 Profile 播种不同数据量
        String profile = System.getProperty("spring.profiles.active");
        if ("dev".equals(profile)) {
            seedDevData();     // 20 条基础数据
        } else if ("test".equals(profile)) {
            seedTestData();    // 1000 条测试数据
        }
        
        return ReturnT.SUCCESS;
    }
}
```

### 3. 配置管理策略

**application-dev.yml**：

```yaml
spring:
  profiles: dev
  
  datasource:
    url: jdbc:mysql://localhost:3306/omplatform_dev?useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver

  # Apollo 降级：本地开发时跳过 Apollo 配置加载
  apollo:
    bootstrap:
      enabled: false  # 本地开发禁用 Apollo，改用本地配置

  # 本地用 Nacos 做服务发现
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

# 本地日志：控制台文本格式（非 JSON）
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

# 本地脱敏：默认关闭（方便调试）
omplatform:
  desensitize:
    enabled: false
  idempotent:
    enabled: true
```

**Apollo 降级模式**：

```java
@Configuration
@ConditionalOnProperty(name = "spring.apollo.bootstrap.enabled", havingValue = "false", matchIfMissing = false)
public class LocalConfigFallback {
    
    /**
     * Apollo 禁用时，从本地 application-dev.yml 加载配置。
     * 通过 @Value 和 @ConfigurationProperties 注入。
     * 
     * 关键点：本地开发只需覆盖测试需要的配置项，
     * 无需维护完整的 Apollo 配置副本。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentConfig idempotentConfig() {
        return new IdempotentConfig(true, "redis", 86400);
    }
}
```

### 4. 调试与热重载

**IntelliJ IDEA Remote JVM Debug**：

```bash
# 服务启动参数
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar order-core.jar --spring.profiles.active=dev
```

**Spring Boot DevTools**：

```yaml
# 仅对非核心服务启用 DevTools 热重载
spring:
  devtools:
    restart:
      enabled: true
      additional-paths: src/main/java
      exclude: static/**,public/**
    livereload:
      enabled: true
```

**跨服务调试**：

```
场景：订单创建流程涉及 order-core → inventory → payment 三个服务
本地调试流程：
  1. 三个服务均在本地 IDEA 中以 Debug 模式启动
  2. Dubbo 调用走 localhost，Nacos 注册本地实例
  3. 在 order-core 的 createOrder() 处设置断点
  4. 发起 HTTP 请求到 Internal Gateway (localhost:8080)
  5. 调用链：Gateway → order-core (断点暂停) → inventory → payment
```

**DSL 调试**：

```java
// 本地开发时通过 DSL 接口直接触发特定场景
@RestController
@RequestMapping("/dev/v1/debug")
@Profile("dev")  // 仅在开发环境可用
public class DebugController {
    
    @PostMapping("/trigger-saga")
    public String triggerSaga(@RequestParam String sagaId) {
        sagaExecutor.triggerRecovery(sagaId);
        return "Saga recovery triggered: " + sagaId;
    }
    
    @PostMapping("/publish-event")
    public String publishEvent(@RequestBody Object event) {
        eventPublisher.publish("debug-event", event);
        return "Event published";
    }
    
    @PostMapping("/simulate-timeout")
    public String simulateTimeout(@RequestParam int seconds) {
        // 模拟慢调用，测试 Sentinel 熔断
        Thread.sleep(seconds * 1000L);
        return "Simulated " + seconds + "s timeout";
    }
}
```

### 5. Testcontainers 集成测试

**测试基类**：

```java
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {
    
    // 单容器：MySQL（替代 OceanBase）
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("omplatform_test")
        .withUsername("test")
        .withPassword("test");
    
    // 单容器：Redis
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
        .withExposedPorts(6379);
    
    // 单容器：Elasticsearch
    @Container
    static ElasticsearchContainer es = new ElasticsearchContainer("elasticsearch:8.11")
        .withEnv("xpack.security.enabled", "false");
    
    // 动态属性覆盖
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.elasticsearch.uris", 
            () -> "http://localhost:" + es.getMappedPort(9200));
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

**测试示例**：

```java
class OrderServiceTest extends AbstractIntegrationTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Test
    void testCreateOrder() {
        // 使用 Testcontainers 启动的真实 MySQL + Redis
        CreateOrderRequest request = new CreateOrderRequest();
        request.setBuyerId(1001L);
        request.setTotalAmount(new BigDecimal("9999"));
        
        OrderResponse response = orderService.createOrder(request);
        
        assertThat(response.getOrderNo()).isNotBlank();
        assertThat(orderMapper.selectByOrderNo(response.getOrderNo())).isPresent();
    }
}
```

**硬件要求**：

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核 |
| 内存 | 8G（全部中间件） | 16G |
| 磁盘 | 20G 可用 | 50G SSD |
| Docker | Docker Desktop 4.20+ | Docker Desktop 4.20+ |
| JDK | JDK 17 | JDK 21 |
| Maven | Maven 3.8+ | Maven 3.9+ |

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | docker-compose.yml 全部中间件配置 | 0.5d | compose 文件 + 健康检查 |
| P2 | 测试数据播种：data-dev.sql + WireMock mock 配置 | 0.5d | 种子脚本 + Mock 映射 |
| P3 | application-dev.yml + Apollo 降级 + Profile 配置 | 0.5d | 配置文件模板 |
| P4 | Testcontainers 基类 + 集成测试示例 | 0.5d | 测试基类 + 文档 |
| P5 | IDE 调试文档 + DevTools 配置 | 0.5d | README + 配置 |
| P6 | MySQL ↔ OB 兼容性检查清单 | 0.5d | Profile + databaseId 配置 |

**合计**：3 人天

## 上线检查清单

- [ ] 基础设施：docker-compose.yml 可正常 `docker compose up -d`
- [ ] 基础设施：所有 6 种中间件健康检查通过
- [ ] 基础设施：MySQL 替代 OB 的兼容性 SQL 清单（标记 OB 特有语法）
- [ ] 配置：application-dev.yml + application-test.yml + application-ob.yml
- [ ] 配置：Apollo 降级模式验证（禁用 Apollo 后服务可正常启动）
- [ ] 代码：`@Profile("ob")` 标记 OB 特有功能
- [ ] 代码：MyBatis databaseId 动态 SQL 切换
- [ ] 代码：DevTools 热重载配置
- [ ] 代码：DebugController（仅在 dev Profile 下注册）
- [ ] 测试：Testcontainers 基类可正常启动容器
- [ ] 测试：集成测试示例可以通过
- [ ] 文档：本地开发 README（启动步骤 + 硬件要求 + 调试指引）

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-011** (在线 DDL) | Liquibase 本地运行（localhost:3306）验证 DDL 变更；OB 特有 SQL 用 `@Profile("ob")` 隔离 |
| **ADR-022** (全链路灰度) | 本地灰度环境通过 docker-compose.override 启动多版本服务 |
| **ADR-023** (脱敏) | 本地开发默认关闭脱敏（`omplatform.desensitize.enabled=false`）方便调试 |
| **ADR-025** (外部 Gateway) | 本地 WireMock 模拟三方支付 API |
| **ADR-026** (认证授权) | 本地开发 Token 不过期，可配置固定 JWT 用于调试 |
| **ADR-029** (内部 Gateway) | 本地 Gateway 可直连 Nacos 上的本地服务实例 |
| **ADR-030** (幂等框架) | 本地可关闭幂等（`omplatform.idempotent.enabled=false`） |
| **ADR-027** (可观测性) | 本地轻量观测栈（Prometheus + Grafana + Loki 可选） |
| **CICD 流水线** (cicd-pipeline.md) | 本地预提交检查（单元测试 → 集成测试 → SQL Review）与 CI 阶段对应 |

## 备选方案评估

### 方案 A：完整 OB 容器

- **优点**：与生产完全一致，无兼容性问题
- **缺点**：最低 8G 内存，低配 Mac/Windows 开发机无法运行；启动需 5-10 分钟
- **适用场景**：性能测试、特定 OB 功能验证

### 方案 C：Remote OB 代理

- **优点**：零本地资源消耗，完全 OB 兼容
- **缺点**：依赖网络，离线无法工作；共享 DEV 数据库可能互相干扰；延迟高于本地
- **适用场景**：仅调试 OB 特有功能时作为 Complement
