#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$project_dir"

if [ ! -f .env ]; then
    echo 'Chưa có .env. Chạy: cp .env.example .env rồi sửa cấu hình.' >&2
    exit 1
fi

# Read KEY=VALUE without evaluating shell expressions. This also accepts a .env
# created on Windows and keeps JDBC URLs containing ampersands intact.
carriage_return=$(printf '\r')
while IFS= read -r env_line || [ -n "$env_line" ]; do
    env_line=${env_line%"$carriage_return"}
    case "$env_line" in
        ''|'#'*) continue ;;
        *=*) ;;
        *)
            echo "Dòng .env không hợp lệ: $env_line" >&2
            exit 1
            ;;
    esac

    env_name=${env_line%%=*}
    env_value=${env_line#*=}
    case "$env_name" in
        [A-Za-z_]* )
            case "$env_name" in
                *[!A-Za-z0-9_]* )
                    echo "Tên biến .env không hợp lệ: $env_name" >&2
                    exit 1
                    ;;
            esac
            ;;
        *)
            echo "Tên biến .env không hợp lệ: $env_name" >&2
            exit 1
            ;;
    esac

    case "$env_value" in
        \"*\") env_value=${env_value#\"}; env_value=${env_value%\"} ;;
        \'*\') env_value=${env_value#\'}; env_value=${env_value%\'} ;;
    esac
    export "$env_name=$env_value"
done < .env

if [ -n "${JAVA_HOME:-}" ]; then
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi

exec ./mvnw spring-boot:run "$@"
