#!/bin/sh
set -eu

# This command is deliberately restricted to one local UI database. It does not
# run during application startup or against TEST_DATABASE_URL.
umask 077
reset_project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
reset_env_file="$reset_project_dir/.env"
reset_sql_file="$reset_project_dir/scripts/clear-all-data.sql"

reset_fail() {
    printf 'CLEAR ALL DATA đã dừng: %s\n' "$1" >&2
    exit 1
}

if [ "${1:-}" = '--help' ]; then
    printf '%s\n' 'Cách dùng: ./scripts/clear-all-data.sh'
    printf '%s\n' 'Cần .env local, REPORT_STORAGE_ROOT tuyệt đối và CLEAR_DATA_ARCHIVE_ROOT tuyệt đối.'
    printf '%s\n' 'Dừng ứng dụng, xem trước dữ liệu rồi nhập đúng CLEAR ALL DATA để xác nhận.'
    exit 0
fi
[ "$#" -eq 0 ] || reset_fail 'Lệnh không nhận tham số. Dùng --help để xem hướng dẫn.'
[ -f "$reset_env_file" ] || reset_fail 'Thiếu .env ở repository.'
[ -f "$reset_sql_file" ] || reset_fail 'Thiếu scripts/clear-all-data.sql.'
command -v mysql >/dev/null 2>&1 || reset_fail 'Thiếu mysql client.'
command -v mysqldump >/dev/null 2>&1 || reset_fail 'Thiếu mysqldump.'
command -v lsof >/dev/null 2>&1 || reset_fail 'Thiếu lsof để kiểm tra ứng dụng đang chạy.'

reset_url=''
reset_username=''
reset_password=''
reset_profile=''
reset_seed_enabled='false'
reset_report_root=''
reset_archive_root=${CLEAR_DATA_ARCHIVE_ROOT:-}
reset_server_port='8088'
reset_cr=$(printf '\r')
while IFS= read -r reset_line || [ -n "$reset_line" ]; do
    reset_line=${reset_line%"$reset_cr"}
    case "$reset_line" in ''|'#'*) continue ;; *=*) ;; *) reset_fail 'Dòng .env không hợp lệ.' ;; esac
    reset_name=${reset_line%%=*}
    reset_value=${reset_line#*=}
    case "$reset_name" in
        SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|SPRING_PROFILES_ACTIVE|DEMO_SEED_ENABLED|REPORT_STORAGE_ROOT|CLEAR_DATA_ARCHIVE_ROOT|SERVER_PORT) ;;
        *) continue ;;
    esac
    case "$reset_value" in
        \"*\") reset_value=${reset_value#\"}; reset_value=${reset_value%\"} ;;
        \'*\') reset_value=${reset_value#\'}; reset_value=${reset_value%\'} ;;
    esac
    case "$reset_name" in
        SPRING_DATASOURCE_URL) reset_url=$reset_value ;;
        SPRING_DATASOURCE_USERNAME) reset_username=$reset_value ;;
        SPRING_DATASOURCE_PASSWORD) reset_password=$reset_value ;;
        SPRING_PROFILES_ACTIVE) reset_profile=$reset_value ;;
        DEMO_SEED_ENABLED) reset_seed_enabled=$reset_value ;;
        REPORT_STORAGE_ROOT) reset_report_root=$reset_value ;;
        CLEAR_DATA_ARCHIVE_ROOT) reset_archive_root=$reset_value ;;
        SERVER_PORT) reset_server_port=$reset_value ;;
    esac
done < "$reset_env_file"

reset_url_base=${reset_url%%\?*}
[ "$reset_url_base" = 'jdbc:mysql://127.0.0.1:3307/topic_registration_local' ] ||
    reset_fail 'Chỉ cho phép jdbc:mysql://127.0.0.1:3307/topic_registration_local.'
[ "$reset_profile" = 'local' ] || reset_fail 'SPRING_PROFILES_ACTIVE phải là local.'
[ "$reset_seed_enabled" = 'false' ] || reset_fail 'Đặt DEMO_SEED_ENABLED=false trước khi reset.'
[ -n "$reset_username" ] && [ -n "$reset_password" ] ||
    reset_fail 'Thiếu tài khoản hoặc mật khẩu DB local trong .env.'
case "$reset_server_port" in ''|*[!0-9]*) reset_fail 'SERVER_PORT không hợp lệ.' ;; esac
if lsof -nP -iTCP:"$reset_server_port" -sTCP:LISTEN >/dev/null 2>&1; then
    reset_fail "Ứng dụng/cổng $reset_server_port còn đang mở; hãy dừng trước khi reset."
fi

case "$reset_report_root" in /*) ;; *) reset_fail 'REPORT_STORAGE_ROOT phải là đường dẫn tuyệt đối riêng cho local.' ;; esac
case "$reset_archive_root" in /*) ;; *) reset_fail 'CLEAR_DATA_ARCHIVE_ROOT phải là đường dẫn tuyệt đối ngoài repository.' ;; esac
[ -d "$reset_report_root" ] && [ ! -L "$reset_report_root" ] ||
    reset_fail 'Thư mục REPORT_STORAGE_ROOT không tồn tại hoặc là symlink.'
[ -d "$reset_archive_root" ] && [ ! -L "$reset_archive_root" ] ||
    reset_fail 'Thư mục CLEAR_DATA_ARCHIVE_ROOT không tồn tại hoặc là symlink.'
reset_report_root=$(CDPATH= cd -- "$reset_report_root" && pwd -P)
reset_archive_root=$(CDPATH= cd -- "$reset_archive_root" && pwd -P)
case "$reset_report_root" in /|/private/tmp|/tmp|"$reset_project_dir")
    reset_fail 'REPORT_STORAGE_ROOT quá rộng hoặc trùng repository.' ;;
esac
case "$reset_archive_root" in /|/private/tmp|/tmp|"$reset_project_dir")
    reset_fail 'CLEAR_DATA_ARCHIVE_ROOT quá rộng hoặc trùng repository.' ;;
esac
case "$reset_archive_root/" in "$reset_report_root/"*) reset_fail 'Thư mục lưu trữ nằm trong thư mục PDF.' ;; esac
case "$reset_report_root/" in "$reset_archive_root/"*) reset_fail 'Thư mục PDF nằm trong thư mục lưu trữ.' ;; esac
case "$reset_archive_root/" in "$reset_project_dir/"*) reset_fail 'Thư mục lưu trữ phải ở ngoài repository.' ;; esac

# MYSQL_PWD is inherited only by the two local client processes; it is never
# placed in a command argument, a backup filename, or tool output.
MYSQL_PWD=$reset_password
export MYSQL_PWD
reset_password=''
reset_mysql() {
    mysql --skip-ssl --protocol=TCP --host=127.0.0.1 --port=3307 \
        --user="$reset_username" --database=topic_registration_local \
        --batch --skip-column-names "$@"
}

reset_database_name=$(reset_mysql --execute='SELECT DATABASE()') || reset_fail 'Không kết nối được DB local.'
[ "$reset_database_name" = 'topic_registration_local' ] || reset_fail 'DB kết nối không đúng tên.'
reset_last_migration=$(reset_mysql --execute="SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1") ||
    reset_fail 'Không đọc được lịch sử Flyway.'
[ "$reset_last_migration" = '25' ] || reset_fail 'Schema không ở Flyway V25; cập nhật script trước khi reset.'

reset_expected_tables='announcements
council_members
council_topics
councils
departments
evaluations
faculties
final_results
flyway_schema_history
group_invitations
group_memberships
notification_events
project_reports
registration_members
registration_periods
registrations
reviewer_assignments
reviewer_evaluations
reviewer_reports
roles
score_components
student_groups
student_profiles
topic_supervisors
topics
user_accounts
user_notifications
user_roles'
reset_actual_tables=$(reset_mysql --execute="SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME") ||
    reset_fail 'Không kiểm kê được bảng.'
[ "$reset_actual_tables" = "$reset_expected_tables" ] || reset_fail 'Danh sách bảng đã thay đổi; từ chối reset schema chưa được kiểm tra.'

reset_admin_count=$(reset_mysql --execute="SELECT COUNT(*) FROM user_accounts a JOIN user_roles r ON r.user_id = a.id WHERE a.username = 'admin.demo' AND a.enabled = 1 AND a.faculty_id IS NULL AND a.department_id IS NULL AND r.role_code = 'ADMIN'") || reset_fail 'Không kiểm tra được admin.demo.'
reset_admin_roles=$(reset_mysql --execute="SELECT COUNT(*) FROM user_roles r JOIN user_accounts a ON a.id = r.user_id WHERE a.username = 'admin.demo'") || reset_fail 'Không kiểm tra được role ADMIN.'
reset_faculty_count=$(reset_mysql --execute="SELECT COUNT(*) FROM faculties WHERE BINARY name = BINARY 'Khoa Công nghệ Thông tin'") || reset_fail 'Không kiểm tra được Khoa.'
[ "$reset_admin_count" = '1' ] && [ "$reset_admin_roles" = '1' ] && [ "$reset_faculty_count" = '1' ] ||
    reset_fail 'Cần đúng một admin.demo enabled chỉ có role ADMIN và đúng một Khoa Công nghệ Thông tin.'

reset_business_tables='announcements council_members council_topics councils departments evaluations final_results group_invitations group_memberships notification_events project_reports registration_members registration_periods registrations reviewer_assignments reviewer_evaluations reviewer_reports score_components student_groups student_profiles topic_supervisors topics user_notifications'
reset_expected_triggers='trg_council_members_keep_officers
trg_group_memberships_keep_leader
trg_registration_members_immutable_delete
trg_registration_members_immutable_update'
reset_trigger_query="SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE IN ('council_members', 'group_memberships', 'registration_members') ORDER BY TRIGGER_NAME"
reset_actual_triggers=$(reset_mysql --execute="$reset_trigger_query") || reset_fail 'Không kiểm kê được trigger.'
[ "$reset_actual_triggers" = "$reset_expected_triggers" ] ||
    reset_fail 'Trigger của các bảng cần reset đã thay đổi; từ chối xóa.'
printf '%s\n' 'Dữ liệu sẽ bị xóa khỏi topic_registration_local:'
for reset_table in $reset_business_tables; do
    reset_count=$(reset_mysql --execute="SELECT COUNT(*) FROM $reset_table") || reset_fail 'Không đọc được số dòng dữ liệu.'
    printf '  %-25s %s\n' "$reset_table" "$reset_count"
done
reset_other_users=$(reset_mysql --execute="SELECT COUNT(*) FROM user_accounts WHERE username <> 'admin.demo'") || reset_fail 'Không đếm được tài khoản.'
reset_other_faculties=$(reset_mysql --execute="SELECT COUNT(*) FROM faculties WHERE BINARY name <> BINARY 'Khoa Công nghệ Thông tin'") || reset_fail 'Không đếm được Khoa.'
printf '  %-25s %s\n' 'other user_accounts' "$reset_other_users"
printf '  %-25s %s\n' 'other faculties' "$reset_other_faculties"
printf '%s\n' 'Giữ admin.demo, Khoa Công nghệ Thông tin, danh mục roles, Flyway và bộ đếm ID.'
printf 'Nhập chính xác CLEAR ALL DATA để tiếp tục: '
IFS= read -r reset_confirmation || reset_fail 'Chưa xác nhận.'
[ "$reset_confirmation" = 'CLEAR ALL DATA' ] || reset_fail 'Câu xác nhận không khớp.'

reset_run_dir=$(mktemp -d "$reset_archive_root/clear-$(date +%Y%m%d-%H%M%S).XXXXXX") ||
    reset_fail 'Không tạo được thư mục lưu trữ.'
mkdir -m 700 "$reset_run_dir/pdfs" || reset_fail 'Không tạo được thư mục PDF lưu trữ.'
printf 'Đang sao lưu vào %s\n' "$reset_run_dir"
mysqldump --skip-ssl --protocol=TCP --host=127.0.0.1 --port=3307 \
    --user="$reset_username" --single-transaction --quick \
    --routines --triggers --events --no-tablespaces \
    topic_registration_local > "$reset_run_dir/database-before.sql" ||
    reset_fail 'Sao lưu DB thất bại; chưa xóa dữ liệu.'
[ -s "$reset_run_dir/database-before.sql" ] || reset_fail 'Bản sao DB rỗng; chưa xóa dữ liệu.'
reset_mysql --execute="SELECT TRIGGER_NAME, ACTION_TIMING, EVENT_MANIPULATION, ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE IN ('council_members', 'group_memberships', 'registration_members') ORDER BY TRIGGER_NAME" > "$reset_run_dir/triggers-before.tsv" ||
    reset_fail 'Không sao lưu được định nghĩa trigger; chưa xóa dữ liệu.'
mysqldump --skip-ssl --protocol=TCP --host=127.0.0.1 --port=3307 \
    --user="$reset_username" --no-data --no-create-info --triggers \
    --no-tablespaces topic_registration_local \
    council_members group_memberships registration_members > "$reset_run_dir/triggers-before.sql" ||
    reset_fail 'Không sao lưu được SQL tạo trigger; chưa xóa dữ liệu.'
for reset_trigger in $reset_expected_triggers; do
    grep -Fq "$reset_trigger" "$reset_run_dir/triggers-before.sql" ||
        reset_fail 'Bản sao trigger không đầy đủ; chưa xóa dữ liệu.'
done
reset_mysql --execute='SELECT storage_key FROM project_reports UNION SELECT storage_key FROM reviewer_reports' \
    > "$reset_run_dir/pdf-keys.txt" || reset_fail 'Không lấy được danh sách PDF; chưa xóa dữ liệu.'
while IFS= read -r reset_key || [ -n "$reset_key" ]; do
    [ -n "$reset_key" ] || continue
    printf '%s\n' "$reset_key" | LC_ALL=C grep -Eq \
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.pdf$' ||
        reset_fail 'Storage key PDF không hợp lệ; chưa xóa dữ liệu.'
    [ -f "$reset_report_root/$reset_key" ] && [ ! -L "$reset_report_root/$reset_key" ] ||
        reset_fail 'Thiếu PDF được DB tham chiếu; chưa xóa dữ liệu.'
    cp -p "$reset_report_root/$reset_key" "$reset_run_dir/pdfs/$reset_key" ||
        reset_fail 'Sao lưu PDF thất bại; chưa xóa dữ liệu.'
    cmp -s "$reset_report_root/$reset_key" "$reset_run_dir/pdfs/$reset_key" ||
        reset_fail 'PDF sao lưu không khớp; chưa xóa dữ liệu.'
done < "$reset_run_dir/pdf-keys.txt"

reset_triggers_dropped=false
reset_restore_triggers() {
    [ "$reset_triggers_dropped" = true ] || return 0
    reset_mysql --execute='DROP TRIGGER IF EXISTS trg_council_members_keep_officers; DROP TRIGGER IF EXISTS trg_group_memberships_keep_leader; DROP TRIGGER IF EXISTS trg_registration_members_immutable_delete; DROP TRIGGER IF EXISTS trg_registration_members_immutable_update' || return 1
    reset_mysql < "$reset_run_dir/triggers-before.sql" || return 1
    reset_actual_triggers=$(reset_mysql --execute="$reset_trigger_query") || return 1
    [ "$reset_actual_triggers" = "$reset_expected_triggers" ] || return 1
    reset_mysql --execute="SELECT TRIGGER_NAME, ACTION_TIMING, EVENT_MANIPULATION, ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE IN ('council_members', 'group_memberships', 'registration_members') ORDER BY TRIGGER_NAME" > "$reset_run_dir/triggers-after.tsv" || return 1
    cmp -s "$reset_run_dir/triggers-before.tsv" "$reset_run_dir/triggers-after.tsv" || return 1
    reset_triggers_dropped=false
}
reset_on_exit() {
    reset_exit_status=$?
    trap - EXIT HUP INT TERM
    if [ "$reset_triggers_dropped" = true ] && ! reset_restore_triggers; then
        printf 'CẢNH BÁO: không khôi phục được trigger; dùng bản sao tại %s trước khi mở lại ứng dụng.\n' "$reset_run_dir" >&2
        exit 1
    fi
    exit "$reset_exit_status"
}
trap 'reset_on_exit' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
reset_triggers_dropped=true
reset_mysql --execute='DROP TRIGGER trg_council_members_keep_officers; DROP TRIGGER trg_group_memberships_keep_leader; DROP TRIGGER trg_registration_members_immutable_delete; DROP TRIGGER trg_registration_members_immutable_update' ||
    reset_fail "Không tháo được trigger; kiểm tra bản sao ở $reset_run_dir."
reset_mysql < "$reset_sql_file" || reset_fail "Xóa DB thất bại; kiểm tra bản sao ở $reset_run_dir."
reset_restore_triggers || reset_fail "Không khôi phục được trigger; dùng bản sao ở $reset_run_dir trước khi mở lại ứng dụng."
for reset_table in $reset_business_tables; do
    reset_count=$(reset_mysql --execute="SELECT COUNT(*) FROM $reset_table") ||
        reset_fail 'Không kiểm tra được DB sau reset.'
    [ "$reset_count" = '0' ] || reset_fail "Bảng $reset_table vẫn có dữ liệu; kiểm tra bản sao ở $reset_run_dir."
done
reset_other_users=$(reset_mysql --execute="SELECT COUNT(*) FROM user_accounts WHERE username <> 'admin.demo'")
reset_other_faculties=$(reset_mysql --execute="SELECT COUNT(*) FROM faculties WHERE BINARY name <> BINARY 'Khoa Công nghệ Thông tin'")
[ "$reset_other_users" = '0' ] && [ "$reset_other_faculties" = '0' ] ||
    reset_fail "DB chưa đạt trạng thái sạch; kiểm tra bản sao ở $reset_run_dir."

while IFS= read -r reset_key || [ -n "$reset_key" ]; do
    [ -n "$reset_key" ] || continue
    [ -f "$reset_report_root/$reset_key" ] && [ ! -L "$reset_report_root/$reset_key" ] && \
        cmp -s "$reset_report_root/$reset_key" "$reset_run_dir/pdfs/$reset_key" ||
        reset_fail "DB đã reset nhưng PDF gốc thay đổi; bản sao ở $reset_run_dir."
    rm -- "$reset_report_root/$reset_key" ||
        reset_fail "DB đã reset nhưng chưa dọn hết PDF gốc; bản sao ở $reset_run_dir."
done < "$reset_run_dir/pdf-keys.txt"

printf 'CLEAR ALL DATA hoàn tất. Bản sao DB và PDF: %s\n' "$reset_run_dir"
