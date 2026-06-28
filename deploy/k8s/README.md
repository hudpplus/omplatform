# OM Platform — K8s 部署指南

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                     K8s Cluster                          │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │ oms-trade│  │ oms-ful- │  │ oms-     │  ... 共 11 个 │
│  │  :8080   │  │ fillment  │  │ finance  │     服务     │
│  │          │  │  :8081   │  │  :8082   │               │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘               │
│       │              │              │                     │
│       └──────────────┼──────────────┘                    │
│                      │  Dubbo (Nacos 注册发现)            │
│              ┌───────▼────────┐                          │
│              │  Nacos Service  │  ExternalName           │
│              │  → host.docker  │  → docker-compose       │
│              │    .internal:   │                          │
│              │    8848        │                          │
│              └────────────────┘                          │
│                      │                                    │
│              ┌───────▼────────┐                          │
│              │  MySQL/Redis/  │  ExternalName            │
│              │  RocketMQ/ES   │  → host.docker.internal   │
│              └────────────────┘                          │
└─────────────────────────────────────────────────────────┘
                         │
              ┌──────────▼──────────┐
              │  Docker Compose      │
              │  基础设施              │
              │  (MySQL MGR, Redis   │
              │   Sentinel, Nacos,   │
              │   RocketMQ, ES)      │
              └─────────────────────┘
```

- **11 个 Java 服务**运行在 K8s Pod 中
- **基础设施**（MySQL/Redis/Nacos/RocketMQ/ES）在 Docker Compose 中
- **K8s ExternalName Service** 桥接基础设施

## 前置条件

1. **Docker Desktop**（启用 K8s）：Settings → Kubernetes → Enable Kubernetes
2. **基础设施运行中**：
   ```bash
   cd deploy
   docker-compose -f docker-compose-mgr.yml up -d
   docker exec mgr1 bash /etc/mysql/init-mgr.sh
   ```
3. **kubectl** 配置正确：`kubectl cluster-info`

## 快速部署

```bash
# 1. 构建全部镜像 + 部署
cd deploy/k8s
./deploy.sh

# 2. 查看状态
./deploy.sh status

# 3. 验证 Nacos 服务注册
# 访问 http://localhost:8848/nacos → 服务管理 → 服务列表
# 应看到 11 个服务已注册
```

## 分步操作

### 仅构建镜像

```bash
./deploy.sh build-only
# 或手动构建单个服务
docker build --build-arg MODULE=oms-trade -t omplatform/oms-trade:latest -f ../docker/Dockerfile ../..
```

### 仅部署到 K8s

```bash
./deploy.sh deploy-only
```

### 查看状态

```bash
./deploy.sh status

# 或手动查看
kubectl get pods -n omplatform -o wide
kubectl get svc -n omplatform
kubectl logs -n omplatform deployment/oms-trade -f
```

### 清理

```bash
./deploy.sh destroy
```

### 访问服务

通过内部网关 IGW：

```bash
# IGW 端口 8087（ExternalName → host.docker.internal:8087）
curl http://localhost:8087/api/v1/orders/...

# 直连 oms-trade
kubectl port-forward -n omplatform deployment/oms-trade 8080:8080
curl http://localhost:8080/actuator/health
```

## 环境变量覆盖说明

所有 `localhost` 地址通过 K8s ConfigMap 的环境变量覆盖，**零代码改动**。

| 应用配置 | 环境变量 | 来源 |
|---------|---------|------|
| `server.port` | `SERVER_PORT` | Service ConfigMap |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Service ConfigMap |
| `spring.cloud.nacos.*.server-addr` | `SPRING_CLOUD_NACOS_*_SERVER_ADDR` | Shared ConfigMap |
| `dubbo.registry.address` | `DUBBO_REGISTRY_ADDRESS` | Shared ConfigMap |
| `rocketmq.name-server` | `ROCKETMQ_NAME_SERVER` | Shared ConfigMap |
| `spring.data.redis.sentinel.*` | `SPRING_DATA_REDIS_SENTINEL_*` | Shared ConfigMap |
| `spring.elasticsearch.uris` | `SPRING_ELASTICSEARCH_URIS` | Shared ConfigMap |
| `management.endpoints.*` | `MANAGEMENT_ENDPOINTS_*` | Service ConfigMap |

## 故障排查

| 症状 | 排查 |
|------|------|
| Pod CrashLoopBackOff | `kubectl logs -n omplatform pod/<name>` |
| 连不上 Nacos | 检查 docker-compose 是否运行 + `kubectl get svc nacos -n omplatform` |
| Dubbo 调用失败 | 检查 Nacos 服务列表是否有调用方和被调用方 |
| ShardingSphere 路由异常 | 检查 `kubectl logs -n omplatform deployment/oms-trade` 的分片日志 |
| 内存不足 | `kubectl describe pod -n omplatform <name>` 查看 OOM 事件 |
