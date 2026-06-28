#!/bin/bash
# =============================================================================
# OM Platform — 一键部署到 K8s
# 用法:
#   ./deploy.sh              # 构建 + 部署全部
#   ./deploy.sh build-only   # 仅构建镜像
#   ./deploy.sh deploy-only  # 仅部署到 K8s
#   ./deploy.sh status       # 查看部署状态
#
# 前置条件:
#   1. Docker Desktop K8s 已启用
#   2. 基础设施运行中: docker-compose -f deploy/docker-compose-mgr.yml up -d
#   3. MGR 已引导:     docker exec mgr1 bash /etc/mysql/init-mgr.sh
# =============================================================================

set -e

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
K8S_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_TAG="${DOCKER_TAG:-latest}"
REGISTRY="${REGISTRY:-omplatform}"

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

build_images() {
  echo "=========================================="
  echo "  构建 Docker 镜像"
  echo "  标签: ${DOCKER_TAG}"
  echo "=========================================="

  for svc in "${SERVICES[@]}"; do
    echo "--- [${svc}] ---"
    docker build \
      --build-arg MODULE="${svc}" \
      -t "${REGISTRY}/${svc}:${DOCKER_TAG}" \
      -f "${ROOT_DIR}/deploy/docker/Dockerfile" \
      "${ROOT_DIR}"
    echo "  ✅ 完成"
  done
}

deploy_to_k8s() {
  echo "=========================================="
  echo "  部署到 K8s"
  echo "  命名空间: omplatform"
  echo "=========================================="

  # 应用 Kustomize
  kubectl apply -k "${K8S_DIR}/overlays/local-dev"

  echo ""
  echo "等待 Pod 就绪（最长 5 分钟）..."
  kubectl wait --for=condition=Available --timeout=300s \
    deployment -n omplatform --all

  echo ""
  echo "=========================================="
  echo "  Pod 状态"
  echo "=========================================="
  kubectl get pods -n omplatform
}

show_status() {
  echo "=========================================="
  echo "  OM Platform K8s 状态"
  echo "=========================================="
  echo ""
  echo "--- Pods ---"
  kubectl get pods -n omplatform -o wide
  echo ""
  echo "--- Services ---"
  kubectl get svc -n omplatform
  echo ""
  echo "--- ConfigMaps ---"
  kubectl get configmap -n omplatform
  echo ""
  echo "--- 健康检查 ---"
  for svc in "${SERVICES[@]}"; do
    IP=$(kubectl get pod -n omplatform -l app="${svc}" -o jsonpath='{.items[0].status.podIP}' 2>/dev/null || echo "")
    if [ -n "$IP" ]; then
      PORT=${svc##*-}  # 简化端口获取
      echo "  ${svc}: 检查中..."
    fi
  done
}

destroy() {
  echo "删除 K8s 资源..."
  kubectl delete namespace omplatform --ignore-not-found
  echo "  ✅ 已删除"
}

case "${1:-all}" in
  build-only)
    build_images
    ;;
  deploy-only)
    deploy_to_k8s
    ;;
  status)
    show_status
    ;;
  destroy)
    destroy
    ;;
  all|*)
    build_images
    deploy_to_k8s
    show_status
    ;;
esac
