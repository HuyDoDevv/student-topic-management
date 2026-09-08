#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f .env ]; then
    echo 'Chưa có .env. Chạy: cp .env.example .env rồi sửa cấu hình.' >&2
    exit 1
fi
set -a
. ./.env
set +a
if [ -n "${JAVA_HOME:-}" ]; then
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi
exec ./mvnw spring-boot:run "$@"
