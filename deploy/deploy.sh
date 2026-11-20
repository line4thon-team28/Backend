#!/usr/bin/env bash
# 서버(EC2)에서 실행: 최신 코드·이미지를 받아 컨테이너를 교체합니다.
# GitHub Actions 가 호출하며, 수동으로도 실행할 수 있습니다:
#   bash ~/Backend/deploy/deploy.sh            # latest 태그
#   bash ~/Backend/deploy/deploy.sh <커밋SHA>   # 백엔드를 특정 버전으로 (되돌리기)
set -euo pipefail

TAG="${1:-latest}"
cd "$HOME/Backend"

# 배포한 태그를 .env 에 저장 → 태그 없이 docker compose up 해도 마지막 배포 버전으로 뜸
# (새 배포 때는 아래 export BACKEND_TAG 가 .env 보다 우선이라 그대로 새 태그로 배포됨)
if grep -q '^BACKEND_TAG=' .env; then
  sed -i "s/^BACKEND_TAG=.*/BACKEND_TAG=$TAG/" .env
else
  # 마지막 줄에 줄바꿈이 없으면 앞 줄에 붙어버리므로 먼저 줄바꿈 추가
  [ -n "$(tail -c1 .env)" ] && echo >> .env
  echo "BACKEND_TAG=$TAG" >> .env
fi

echo "==> 코드 업데이트 (compose, nginx 설정 등)"
git pull --ff-only origin main

export BACKEND_TAG="$TAG"
# 임베딩 이미지는 embedding-service 가 바뀔 때만 새로 빌드되므로 항상 latest 사용
export EMBEDDING_TAG="latest"

# 매칭 코드가 있고 .env 에서 켜져 있을 때만 임베딩 컨테이너 포함
PROFILE_ARGS=()
if [ -f embedding-service/server.py ] && grep -q '^MATCHING_WORKER_ENABLED=true' .env; then
  PROFILE_ARGS=(--profile matching)
fi

echo "==> 이미지 받기 (tag: $TAG)"
docker compose "${PROFILE_ARGS[@]}" pull backend $([ ${#PROFILE_ARGS[@]} -gt 0 ] && echo embedding)

# 서버 안의 latest 를 방금 배포한 버전으로 맞춤.
# (누가 docker compose up 을 직접 실행해도 예전 이미지가 뜨지 않게)
if [ "$TAG" != "latest" ]; then
  DH_USER="$(grep -E '^DOCKERHUB_USERNAME=' .env | cut -d= -f2)"
  docker tag "${DH_USER}/pawpose-backend:${TAG}" "${DH_USER}/pawpose-backend:latest"
fi

echo "==> 컨테이너 교체"
docker compose "${PROFILE_ARGS[@]}" up -d --no-build

echo "==> nginx 설정 다시 읽기 (바뀐 컨테이너 주소 반영)"
docker compose exec -T nginx nginx -s reload || docker compose restart nginx

echo "==> 오래된 이미지 정리"
docker image prune -f

echo "==> 상태"
docker compose "${PROFILE_ARGS[@]}" ps
