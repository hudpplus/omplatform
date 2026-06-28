# =============================================================================
# OM Platform — 统一多阶段构建 Dockerfile
# 构建方式:
#   docker build --build-arg MODULE=oms-trade -t omplatform/oms-trade:latest .
#   docker build --build-arg MODULE=oms-fulfillment -t omplatform/oms-fulfillment:latest .
# =============================================================================

# ---- Stage 1: 编译 ----
FROM maven:3.9-eclipse-temurin-25 AS builder
ARG MODULE=oms-trade
WORKDIR /build

# 1. 仅复制 POM 文件（利用 Docker 层缓存）
COPY pom.xml ./
COPY oms-saga/pom.xml oms-saga/
COPY oms-common/pom.xml oms-common/
COPY oms-api/pom.xml oms-api/
COPY oms-trade/pom.xml oms-trade/
COPY oms-marketing/pom.xml oms-marketing/
COPY oms-finance/pom.xml oms-finance/
COPY oms-fulfillment/pom.xml oms-fulfillment/
COPY oms-channel-adapter/pom.xml oms-channel-adapter/
COPY oms-risk-integration/pom.xml oms-risk-integration/
COPY cart-service/pom.xml cart-service/
COPY seckill-service/pom.xml seckill-service/
COPY wms-service/pom.xml wms-service/
COPY igw/pom.xml igw/
COPY egw/pom.xml egw/
COPY oms-examples/pom.xml oms-examples/

# 2. 下载依赖（层缓存，源码不变就不重下）
RUN mvn dependency:go-offline -pl ${MODULE} -am -B || true

# 3. 复制源码并编译
COPY . .
RUN mvn clean package -pl ${MODULE} -am -DskipTests -B

# ---- Stage 2: 运行时 ----
FROM eclipse-temurin:25-jre
ARG MODULE=oms-trade
ARG PORT=8080
LABEL org.opencontainers.image.title="OM Platform - ${MODULE}" \
      org.opencontainers.image.description="订单中台微服务" \
      org.opencontainers.image.version="1.0.0"

# 非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser -m -d /app appuser

WORKDIR /app
COPY --from=builder --chown=appuser:appuser /build/${MODULE}/target/*.jar app.jar

# 健康检查（需引入 actuator）
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1

USER appuser

EXPOSE ${PORT}

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-XX:-OmitStackTraceInFastThrow", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
