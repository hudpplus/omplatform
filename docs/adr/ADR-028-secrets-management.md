# ADR-028：密钥管理

## 状态

已接受

---

## 背景

### 现状分析

订单中台安全架构（`security.puml`）在 Layer 3「存储层加密」提到了「Vault 密钥管理 90 天自动轮转」，但这是仅有一行文字——完整的密钥管理设计处于空白状态。当前架构在密钥管理方面存在以下问题：

**问题 1：DB/中间件凭证注入未定义**  
20+ 微服务需要连接 OceanBase、Redis、RocketMQ、ES、Nacos、Apollo 等中间件。这些数据库密码和凭证当前**没有定义注入方式**——是写在配置文件里？K8s Secret？还是 Vault Sidecar？这个空白导致上线前必然出现安全问题。

**问题 2：支付网关密钥存储无设计**  
支付回调（payment-callback.puml）提到「验签（RSA256）」，但支付网关的 RSA 私钥如何安全存储和轮换？如果私钥泄露，攻击者可以伪造支付成功回调。

**问题 3：JWT 签名密钥管理缺失**  
ADR-026 定义了 JWT Token 体系，但 JWT 签名密钥（HMAC-SHA256 key 或 RSA 私钥）的生成、分发、轮换均未定义。签名密钥泄露 → 攻击者可伪造任意 JWT Token → 绕过所有鉴权。

**问题 4：AppSecret 管理缺失**  
ADR-025 外部网关依赖 AppKey/AppSecret 进行 HMAC 签名认证，但 AppSecret 如何存储和轮换未定义。AppSecret 泄露 → 第三方应用身份被冒用。

**问题 5：字段级加密密钥层次未定义**  
`security.puml` 提到「AES-256 字段级加密（身份证/银行卡号）」，但加密密钥的层级结构（Master Key → Data Key → Field Key）和轮换策略未定义。

**问题 6：CI/CD 密钥管理缺失**  
`cicd-pipeline.md` 提到 Cosign 镜像签名，但签名私钥如何管理？CI 流水线中如何安全注入 Docker 仓库认证、Maven 仓库认证？这些密钥不应出现在 `.gitlab-ci.yml` 明文配置中。

### 目标

1. **密钥分级分类**：按敏感度和管理域定义密钥层级
2. **零信任注入**：密钥不写配置文件、不落磁盘明文、不在 CI/CD 中硬编码
3. **零停机轮换**：所有密钥轮换不中断业务
4. **审计可追溯**：所有密钥访问留痕
5. **应急响应**：泄露快速轮换 + 根因分析

### 术语定义

| 术语 | 说明 |
|------|------|
| **Master Key** | 顶层加密密钥，由 Vault 管理，用于加密 Data Key |
| **Data Key (DEK)** | 数据加密密钥，被 Master Key 加密后存储，定期轮换 |
| **Field Key** | 字段级加密密钥（AES-256），由 Data Key 派生 |
| **Vault** | HashiCorp Vault，密钥和 secrets 的集中管理平台 |
| **KV v2** | Vault 的 Key-Value 引擎，带版本控制 |
| **Transit** | Vault 的加密引擎，密钥不离 Vault，数据在委托 Vault 加解密 |
| **PKI** | Vault 的证书签发引擎，用于 TLS 证书生命周期管理 |
| **Rotation** | 密钥轮换，用新密钥替换旧密钥的过程 |
| **Dual-key 模式** | 新旧密钥同时有效，避免轮换期间的断服 |

---

## 决策

### 密钥管理平台

**方案：HashiCorp Vault**（企业版或 OSS + Raft 高可用）

| 维度 | Vault（选中） | K8s Secret 明文 | 自建加密服务 |
|------|-------------|----------------|-------------|
| **密钥分类管理** | ✅ Secret 引擎分离 | ❌ 无分类 | ✅ 可设计 |
| **访问审计** | ✅ 内置审计日志 | ❌ 无 | ⚠️ 需自建 |
| **动态密钥** | ✅ DB 动态密码 | ❌ 静态密码 | ❌ |
| **加密即服务** | ✅ Transit 引擎 | ❌ | ⚠️ 需开发 |
| **轮换支持** | ✅ 原生 | ❌ 手动 | ⚠️ 需开发 |
| **社区成熟度** | ✅ 业界标准 | ✅ 简单 | ❌ |

### 注入方式

**方案：Vault CSI Provider + SecretStore CRD（K8s 原生集成）**

```
Pod → Volume Mount /vault/secrets/* → 应用读取文件
```

**理由**：Vault CSI 驱动将密钥挂载为临时卷，密钥不在 K8s Secret 对象中持久化。Pod 删除后密钥数据自动消失。

### 加密策略

**方案：Vault Transit 引擎（密钥不出 Vault）**

**理由**：Transit 引擎将加解密操作委托给 Vault，应用数据发送到 Vault 加密后返回密文。密钥永远不离开 Vault，极大降低泄露风险。

---

## 详细设计

### 1. 密钥分类矩阵

| 类别 | 密钥/凭证 | 敏感度 | 引擎 | 轮换周期 | 存储位置 |
|------|----------|--------|------|---------|---------|
| **基础设施** | OceanBase 密码 | L3 | KV v2 | 90 天 | `secret/omp/{env}/db/{service}` |
| **基础设施** | Redis 密码 | L3 | KV v2 | 90 天 | `secret/omp/{env}/redis` |
| **基础设施** | RocketMQ 密码 | L3 | KV v2 | 90 天 | `secret/omp/{env}/rocketmq` |
| **基础设施** | ES 密码 | L3 | KV v2 | 90 天 | `secret/omp/{env}/elasticsearch` |
| **基础设施** | Nacos 鉴权 | L3 | KV v2 | 90 天 | `secret/omp/{env}/nacos` |
| **基础设施** | Apollo 鉴权 Token | L3 | KV v2 | 90 天 | `secret/omp/{env}/apollo` |
| **应用密钥** | 支付网关 RSA 私钥 | L4 | Transit | 按渠道要求 | `transit/omp/{env}/payment/{channel}` |
| **应用密钥** | JWT 签名密钥（HMAC/RSA） | L4 | Transit | 30 天 | `transit/omp/{env}/jwt` |
| **应用密钥** | AppSecret（ADR-025） | L3 | KV v2（DB 存 Hash） | 按需 | `secret/omp/{env}/openapi/{appKey}` |
| **加密密钥** | AES-256 字段加密密钥 | L4 | Transit | 90 天 | `transit/omp/{env}/field-encryption` |
| **CI/CD** | Docker 仓库密码 | L3 | KV v2 | 180 天 | `secret/omp/ci/docker-registry` |
| **CI/CD** | Maven 仓库密码 | L2 | KV v2 | 180 天 | `secret/omp/ci/maven` |
| **CI/CD** | Cosign 签名私钥 | L4 | PKI | 1 年 | `pki/omp/ci/cosign` |
| **TLS** | 服务间 mTLS 证书 | L3 | PKI | 1 年 | `pki/omp/internal` |

### 2. Vault 部署架构

```
┌────────────────────────────────────────────────────┐
│                  Vault Cluster                       │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│
│   │ Vault-0     │  │ Vault-1     │  │ Vault-2     ││
│   │ AZ-A        │  │ AZ-B        │  │ AZ-C        ││
│   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘│
│          └────────┬───────┴───────┬────────┘        │
│                   │ Raft 共识     │                   │
│                   └──────────────┘                   │
└──────────────────────────────────────────────────────┘
          │
          │ Auto Unseal (KMS / 硬件 HSM)
          ▼
┌──────────────────────────────────────────────────────┐
│                 K8s 集群                               │
│                                                       │
│  ┌──────────────── Pod ───────────────┐               │
│  │  order-core                        │               │
│  │  ├── /vault/secrets/db-password ◄──┼── Vault CSI  │
│  │  ├── /vault/secrets/redis-password │    Volume     │
│  │  └── 应用进程 ← 文件读取                           │
│  └────────────────────────────────────┘               │
│                                                       │
│  Vault CSI Provider (DaemonSet) ← SecretStore CRD    │
└──────────────────────────────────────────────────────┘
```

### 3. K8s 集成方案（Vault CSI）

#### SecretStore CRD 定义

```yaml
apiVersion: external-secrets.io/v1alpha1
kind: SecretStore
metadata:
  name: omp-vault-store
  namespace: omplatform
spec:
  provider:
    vault:
      server: "https://vault.omp.internal:8200"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "omp-service-role"
---
apiVersion: external-secrets.io/v1alpha1
kind: ExternalSecret
metadata:
  name: order-core-secrets
  namespace: omplatform
spec:
  secretStoreRef:
    name: omp-vault-store
  target:
    name: order-core-db-secret    # K8s Secret 名称（短期存在）
    deletionPolicy: Delete
  data:
    - secretKey: db-password
      remoteRef:
        key: secret/omp/prod/db/order-core
        property: password
```

#### Pod 挂载方式（推荐——直接 Vault CSI Volume）

```yaml
# Deployment 示例
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-core
spec:
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "omp-service-role"
        vault.hashicorp.com/agent-inject-secret-db-password: "secret/omp/prod/db/order-core"
        vault.hashicorp.com/agent-inject-template-db-password: |
          {{- with secret "secret/omp/prod/db/order-core" -}}
          {{ .Data.data.password }}
          {{- end -}}
    spec:
      containers:
        - name: order-core
          image: omp/order-core:2.3.0
          volumeMounts:
            - name: vault-secrets
              mountPath: /vault/secrets
              readOnly: true
      volumes:
        - name: vault-secrets
          csi:
            driver: secrets-store.csi.k8s.io
            readOnly: true
            volumeAttributes:
              secretProviderClass: "omp-vault-secrets"
```

#### Spring Boot 读取方式

```yaml
# bootstrap.yml — 通过环境变量引用挂载的文件
spring:
  datasource:
    password: ${DB_PASSWORD_FILE:/vault/secrets/db-password}
  redis:
    password: ${REDIS_PASSWORD_FILE:/vault/secrets/redis-password}
```

> `$VAR_FILE` 语法由 Spring Boot 2.4+ 支持，表示从文件路径读取内容。

### 4. 密钥轮换策略

#### 双密码轮换模式（DB 密码）

```
阶段 1：生成新密码
  Vault 更新 secret/omp/prod/db/order-core → password=v2_new
  应用 Pod：自动重新挂载（热更新）或滚动重启
  使用新密码连接 DB

阶段 2：双密码共存
  DB 端 ALTER USER identified BY 'v2_new' 且保留 v1_old
  应用端滚动升级使用新密码
  监控：确认所有 Pod 使用新密码

阶段 3：清理旧密码
  DB 端 DROP USER identified BY 'v1_old'
  Vault 归档旧版本
```

#### Transit 密钥轮换（JWT 签名密钥）

```bash
# 步骤 1：Vault Transit 创建新的密钥版本
vault write -f transit/keys/jwt-signing/rotate

# 步骤 2：验证新旧密钥共存
vault read transit/keys/jwt-signing
# 输出显示 latest_version=2，min_encryption_version=1

# 步骤 3：JWT 签发用新密钥
# Vault Transit encrypt → 使用最新版本密钥签名
# JWT header 带 kid 标识密钥版本

# 步骤 4：JWT 校验时用对应版本密钥解密
# 从 JWT header 取 kid → Vault Transit decrypt 指定版本
```

#### 零停机保障原则

1. **双密钥共存窗口**：新旧密钥至少有 24h 共存期
2. **先应用后底层**：先轮换应用层密钥（如 JWT），再轮换基础设施密钥
3. **灰度验证**：先在一个服务/实例验证新密钥，再全量推广
4. **回滚能力**：保留上一版本密钥直到确认轮换成功

### 5. 字段级加密设计（与 Transit 引擎配合）

```java
// 利用 Vault Transit 做字段级加解密
@Service
public class FieldEncryptionService {

    private final VaultTransitOperations transit;

    @Value("${vault.transit.key:field-encryption}")
    private String keyName;

    /**
     * 加密敏感字段（如身份证号）
     * 响应中返回密文（Base64），落库存储密文
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        String ciphertext = transit.encrypt(keyName, plaintext.getBytes(StandardCharsets.UTF_8));
        return ciphertext;  // vault:v1:abc123...
    }

    /**
     * 解密已加密字段
     * 仅在需要明文展示时调用（如 admin 查看身份证）
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (!ciphertext.startsWith("vault:")) {
            return ciphertext;  // 未加密的兼容处理
        }
        byte[] plaintext = transit.decrypt(keyName, ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
```

**数据流转**：
```
  写入：明文 → FieldEncryptionService.encrypt() → Vault Transit 加密 → 密文落库
  读取：密文落库 → 需要明文时 decrypt() → Vault Transit 解密 → 返回明文
  查询脱敏：密文落库 → 不调用 decrypt() → 直接返回脱敏值（如 "110101******1234"）
```

### 6. 审计日志

Vault 内置审计日志，记录所有密钥操作：

```bash
# 启用审计
vault audit enable file file_path=/vault/audit/audit.log

# 审计日志内容示例
{
  "time": "2026-06-12T10:30:00Z",
  "type": "response",
  "auth": {
    "client_token": "hmac-sha256:abc...",
    "policies": ["omp-service-policy"],
    "metadata": {
      "service_account": "order-core",
      "namespace": "omplatform"
    }
  },
  "request": {
    "path": "secret/omp/prod/db/order-core",
    "operation": "read"
  },
  "response": {
    "data": {
      "password": "hmac-sha256:def..."  # 敏感值自动 HMAC 脱敏
    }
  }
}
```

审计日志流向：Vault 审计文件 → Fluentd → Loki（ADR-027）→ 长期存储保留 3 年（合规要求）

### 7. 应急响应流程

```
密钥泄露认定标准：
  - 密码意外提交到 Git
  - 疑似未授权访问 Vault
  - 第三方通报密钥泄露
  - CI/CD 日志中暴露密钥

应急响应：
  1. 确认泄露 → Vault 管控台立即吊销泄露密钥
  2. 自动轮换 → 执行对应密钥的 rotate 操作
  3. 验证影响 → 检查审计日志，确定泄露范围和持续时间
  4. 应用更新 → 确认所有使用该密钥的服务已切换到新密钥
  5. 根因分析 → 写入安全复盘报告，更新密钥管理策略

RTO（恢复时间目标）：< 15min（从认定泄露到完成轮换）
RPO（数据丢失容忍）：当前事务不受影响（双密钥模式）
```

### 8. 非密钥配置管理（与密钥分离）

```yaml
Apollo 配置中心存储非敏感的运行时配置（与 Vault 互补）：
  ✅ 特性开关：desensitize.enabled=true
  ✅ 灰度规则：gray.percentage=5%
  ✅ 超时配置：dubbo.timeout=5000
  ✅ 白名单：ip.whitelist=10.0.0.0/8

  ❌ 不存储密码、密钥、Token 等敏感信息
  ❌ 不存储支付渠道的商户私钥
  ❌ 不存储三方对接密钥
```

### 9. 密钥访问策略（Policy）

```hcl
# OMP 基础策略——所有服务可读自己的 secrets
path "secret/omp/prod/db/+/order-core" {
  capabilities = ["read", "list"]
}
path "secret/omp/prod/redis" {
  capabilities = ["read"]
}

# 仅管理员可写
path "secret/omp/prod/*" {
  capabilities = ["create", "update", "delete"]
  required_parameters = ["admin-approval-token"]
}

# Transit 加密——仅特定服务可用
path "transit/encrypt/field-encryption" {
  capabilities = ["create", "update"]
}
path "transit/decrypt/field-encryption" {
  capabilities = ["create", "update"]
  # 仅 admin 服务有 decrypt 权限
  allowed_parameters = {
    "admin-token" = []
  }
}
```

### 10. 不同环境的密钥隔离

```yaml
Vault 路径结构完全隔离每个环境：

secret/omp/dev/       # 开发环境——可读可写，无严格管控
  db/order-core/        # 密码：dev_123456
  redis/                # 密码：dev_redis

secret/omp/staging/   # 预发布环境——接近生产
  db/order-core/
  redis/
  payment/alipay/       # 沙箱密钥

secret/omp/prod/      # 生产环境——最严格管控
  db/order-core/        # 密码符合复杂度要求，自动轮换
  redis/
  payment/alipay/       # 真实商户私钥
  payment/wechat/

CI/CD 环境：          # 构建/部署用
  secret/omp/ci/
    docker-registry/
    maven-settings/
```

---

## 备选方案评估

### 密钥管理平台

| 维度 | Vault（选中） | AWS Secrets Manager | 阿里云 KMS | 自建 AES 密钥表 |
|------|-------------|-------------------|-----------|----------------|
| **部署方式** | ✅ 私有化部署 | ❌ 云服务锁死 | ❌ 云服务锁死 | ✅ 可控 |
| **密钥引擎** | ✅ KV/Transit/PKI | ⚠️ 仅 KV | ⚠️ 仅加密 | ❌ 需自建 |
| **动态密钥** | ✅ DB 动态密码 | ✅ RDS 集成 | ⚠️ RDS 部分 | ❌ |
| **审计** | ✅ 详细审计 | ✅ CloudTrail | ✅ 操作审计 | ❌ |
| **K8s 集成** | ✅ CSI + Agent | ✅ CSI + Agent | 🔄 进行中 | ❌ |
| **开源/可控** | ✅ 开源 OSS | ❌ 商业 | ❌ 商业 | ✅ |

**结论**：Vault 是唯一支持私有化部署 + 丰富引擎 + K8s CSI 原生集成的方案。

### 注入方式

| 维度 | Vault CSI（选中） | K8s External Secrets | Init Container |
|------|-----------------|---------------------|---------------|
| **密钥是否落 K8s Secret** | ❌ 不落 | ⚠️ 落（同步后删除） | ❌ 不落 |
| **Pod 启动依赖** | ✅ 无额外依赖 | ✅ 无 | ⚠️ 需 init 容器 |
| **热更新** | ✅ Sidecar 监听 | ❌ 需重建 Pod | ❌ 需重建 Pod |
| **运维复杂度** | ✅ 低（CSI Driver） | ✅ 低 | ⚠️ 需维护 Init 脚本 |

**结论**：Vault CSI 直接将密钥挂载为 Volume，密钥不落 K8s 任何对象，安全性最高。

---

## 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| P1 Vault 集群搭建 | 3 节点 Raft 集群 + TLS + Auto Unseal + 初始化 | 1.5d | Vault 集群可用 + 初始密钥写入 |
| P2 K8s 集成 | Vault CSI Provider 部署 + SecretStore CRD + 首个 Pod 接入 | 1.5d | 密钥安全注入到 Pod |
| P3 密钥迁移 | 各中间件/服务密钥迁移到 Vault + 双密钥验证 | 2d | 所有服务从 Vault 读取密钥 |
| P4 Transit 加密 | Transit 引擎配置 + FieldEncryptionService + JWT 签名 | 1.5d | 字段级加密 + JWT 密钥轮换 |
| P5 审计与 CI/CD | Vault 审计日志 → Loki + CI/CD 密钥注入 + Polyn 策略 | 1.5d | 审计链路打通 + CI/CD 密钥安全 |

**合计**：8 人天

---

## 上线检查清单

- [ ] Vault 集群 Raft 共识正常（3/5 可用）
- [ ] Auto Unseal 配置验证（KMS 模式，重启自动解封）
- [ ] Vault CSI Provider DaemonSet 部署验证
- [ ] SecretStore CRD 连通性测试
- [ ] Pod 启动后 `/vault/secrets/*` 文件正确挂载
- [ ] Spring Boot `$VAR_FILE` 读取配置验证
- [ ] 所有服务不包含明文密码配置
- [ ] CI/CD 流水线不输出密钥到日志
- [ ] Transit 加解密集成测试（加密 → 解密 → 结果一致）
- [ ] JWT 签名密钥轮换后，旧 Token 在新密钥下仍可验证
- [ ] DB 密码轮换零停机验证（双密码模式）
- [ ] 审计日志输出到 Loki 验证（ADR-027）
- [ ] Vault Policy 权限最小化原则验证
- [ ] Vault 恢复演练（逐个节点停止，验证不中断服务）
- [ ] 应急预案中密钥泄露处置流程验证

---

## 与现有文档的关联

- **security.puml**：Layer 3 存储层加密 + Layer 4 审计合规的详细设计落地
- **ADR-025**：AppSecret 存储和轮换机制（`secret/omp/{env}/openapi/{appKey}`）
- **ADR-026**：JWT 签名密钥由 Vault Transit 管理（`transit/omp/{env}/jwt`）
- **ADR-027**：Vault 审计日志 → Loki 聚合 → Grafana 可观测；密钥过期告警
- **cicd-pipeline.md**：Cosign 签名私钥由 Vault PKI 管理，流水线读取签名
