# Nacos 配置导入指南

## 方式一：Nacos 控制台导入

1. 启动 Nacos：`docker-compose up -d nacos`
2. 访问 http://localhost:8848/nacos (默认账号: nacos/nacos)
3. 左侧菜单 → 配置管理 → 配置列表
4. 点击 "导入配置" → 选择本目录 `deploy/nacos-config/`
5. 确认导入

## 方式二：CURL 导入

```bash
# common.yaml
curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
  -d "dataId=common.yaml&group=DEFAULT_GROUP&content=$(cat deploy/nacos-config/common.yaml | base64)"

# 各服务配置（替换 dataId 和文件）
for f in deploy/nacos-config/*.yaml; do
  dataId=$(basename $f)
  [ "$dataId" = "README.md" ] && continue
  curl -X POST "http://localhost:8848/nacos/v1/cs/configs" \
    -d "dataId=$dataId&group=DEFAULT_GROUP&content=$(cat $f | base64)"
done
```

## 配置列表

| Data ID | 说明 |
|---------|------|
| common.yaml | 共享默认配置（数据源/Redis/Dubbo/RocketMQ/MyBatis-Plus） |
| oms-trade.yaml | 交易核心（ShardingSphere 分表、卡单检测） |
| oms-marketing.yaml | 营销服务 |
| oms-finance.yaml | 资金服务（支付渠道密钥） |
| cart-service.yaml | 购物车服务 |
| oms-fulfillment.yaml | 履约服务 |
| oms-channel-adapter.yaml | 渠道适配 |
| oms-risk-integration.yaml | 风控集成 |
| igw.yaml | 内部网关 |
| egw.yaml | 外部网关 |
