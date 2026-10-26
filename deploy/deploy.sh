#!/usr/bin/env bash
# 최신 코드로 다시 빌드해 올린다. 서버에서 실행. DB 데이터는 볼륨에 남는다.
#   ~/campus-alimi/deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

git pull --ff-only
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
docker image prune -f >/dev/null

echo
docker compose -f docker-compose.prod.yml ps
echo
echo "로그: docker compose -f docker-compose.prod.yml logs -f app"
echo "첫 수집: docker compose -f docker-compose.prod.yml exec app sh -c 'RUN_ONCE=all java -jar /app/app.jar'"
