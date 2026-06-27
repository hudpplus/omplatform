# 支付渠道证书

## 支付宝

- **alipay-private.key** — 应用私钥（PKCS8 格式 PEM）
- **alipay-public.key** — 支付宝公钥（从支付宝开放平台获取）

## 微信支付

- **wechat-apiclient.key** — 商户 API 私钥（PKCS8 格式 PEM）
- **wechat-apiclient-cert.pem** — 商户证书（可选，自动证书管理器可不配置）

## 获取方式

1. 登录对应开放平台 → 开发者中心
2. 生成/下载商户密钥
3. 将 PEM 文件放入此目录
4. 确保 application.yml 或 Nacos 中的路径正确
