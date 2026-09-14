#!/bin/bash
# 배포용 docker compose 실행 스크립트.
# apt로 설치한 서버는 docker compose(플러그인)가 아니라 구버전 docker-compose(하이픈)라
# 어느 쪽이 있는지 자동으로 감지해서 실행한다.
set -e

cd "$(dirname "$0")"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

$COMPOSE -f docker-compose.prod.yml --env-file .env.prod up -d --build
