#!/bin/bash
# =============================================================================
# OM Platform — 构建全部 11 个服务镜像
# 用法: cd deploy/k8s && ./build-images.sh
# 前置: Docker Desktop 或 minikube docker-env
# =============================================================================

set -e

DOCKER_TAG="${1:-latest}"
REGISTRY="${2:-omplatform}"
DOCKERFILE="../docker/Dockerfile"

SERVICES=(
  oms-trade
  oms-fulfillment
  oms-finance
  oms-marketing
  oms-risk-integration
  cart-service
  oms-channel-adapter
  igw
  egw
  seckill-service
  wms-service
)

echo "=========================================="
echo "  OM Platform 镜像构建"
echo "  标签: ${DOCKER_TAG}"
echo "  镜像数: ${#SERVICES[@]}"
echo "=========================================="

for svc in "${SERVICES[@]}"; do
  echo ""
  echo "--- [${svc}] 构建中 ---"
  docker build \
    --build-arg MODULE="${svc}" \
    -t "${REGISTRY}/${svc}:${DOCKER_TAG}" \
    -f "${DOCKERFILE}" \
    ../..  # 上下文为项目根目录
  echo "  ✅ ${REGISTRY}/${svc}:${DOCKER_TAG}"
done

echo ""
echo "=========================================="
echo "  全部构建完成 ✅"
echo "=========================================="
docker images --filter "reference=${REGISTRY}/*:${DOCKER_TAG}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
