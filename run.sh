#!/bin/bash
# 배포용 docker compose 명령어 단축 스크립트.
# apt로 설치한 서버는 docker compose(플러그인)가 아니라 구버전 docker-compose(하이픈)라
# 어느 쪽이 있는지 자동으로 감지해서 실행한다.
set -e

cd "$(dirname "$0")"

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

FILES="-f docker-compose.prod.yml --env-file .env.prod"
CMD="${1:-up}"
[ $# -gt 0 ] && shift

case "$CMD" in
  up|start|deploy)
    $COMPOSE $FILES up -d --build "$@"
    ;;
  stop)
    $COMPOSE $FILES stop "$@"
    ;;
  restart)
    $COMPOSE $FILES restart "$@"
    ;;
  down)
    $COMPOSE $FILES down "$@"
    ;;
  ps|status)
    $COMPOSE $FILES ps "$@"
    ;;
  logs)
    $COMPOSE $FILES logs -f "$@"
    ;;
  *)
    echo "사용법: $0 {up|stop|restart|down|ps|logs} [서비스명]"
    echo "  up      - 이미지 새로 빌드하고 백그라운드로 실행 (기본값, 인자 없으면 이걸 실행)"
    echo "  stop    - 컨테이너 중지 (삭제 안 함)"
    echo "  restart - 컨테이너 재시작. 서비스명 생략 시 전체 재시작 (예: $0 restart backend)"
    echo "  down    - 컨테이너 삭제 (Docker 표준 docker compose down과 동일)"
    echo "  ps      - 컨테이너 상태 확인"
    echo "  logs    - 로그 확인, 서비스명 생략 시 전체 (예: $0 logs backend)"
    exit 1
    ;;
esac
