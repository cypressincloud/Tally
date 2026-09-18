#!/system/bin/sh

PACKAGE_NAME="$1"
ACCESSIBILITY_COMPONENT="$2"
NOTIFICATION_COMPONENT="$3"
WORK_DIR="$4"
PID_FILE="$WORK_DIR/daemon.pid"

echo $$ > "$PID_FILE"
cleanup_pid() {
    [ -f "$PID_FILE" ] && [ "$(cat "$PID_FILE")" = "$$" ] && rm -f "$PID_FILE"
}
trap cleanup_pid EXIT
trap 'exit 0' INT TERM

apply_background_policies() {
    cmd deviceidle whitelist +"$PACKAGE_NAME" >/dev/null 2>&1
    cmd appops set "$PACKAGE_NAME" RUN_IN_BACKGROUND allow >/dev/null 2>&1
    cmd appops set "$PACKAGE_NAME" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
    am set-standby-bucket "$PACKAGE_NAME" active >/dev/null 2>&1
    am set-inactive --user 0 "$PACKAGE_NAME" false >/dev/null 2>&1
    am set-bg-restriction-level --user 0 "$PACKAGE_NAME" unrestricted >/dev/null 2>&1
    am set-foreground-service-delegate --user 0 "$PACKAGE_NAME" start >/dev/null 2>&1
    am unfreeze --sticky "$PACKAGE_NAME" >/dev/null 2>&1
}

restore_accessibility() {
    [ -f "$WORK_DIR/auto_accessibility" ] || return

    services="$(settings get secure enabled_accessibility_services)"
    [ "$services" = "null" ] && services=""
    echo "$services" | grep -q -F "$ACCESSIBILITY_COMPONENT" && return

    settings put secure accessibility_enabled 0
    [ -n "$services" ] && services="$services:"
    settings put secure enabled_accessibility_services "$services$ACCESSIBILITY_COMPONENT"
    settings put secure accessibility_enabled 1
}

grant_permissions() {
    [ -f "$WORK_DIR/auto_permissions" ] || return
    pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO >/dev/null 2>&1
    pm grant "$PACKAGE_NAME" android.permission.CAMERA >/dev/null 2>&1
    appops set "$PACKAGE_NAME" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    cmd notification allow_listener "$NOTIFICATION_COMPONENT" 0 >/dev/null 2>&1
}

apply_background_policies
grant_permissions

policy_tick=0
while [ -f "$WORK_DIR/enabled" ]; do
    restore_accessibility
    if [ "$policy_tick" -eq 0 ]; then
        apply_background_policies
        grant_permissions
    fi
    policy_tick=$((policy_tick + 1))
    [ "$policy_tick" -ge 6 ] && policy_tick=0
    sleep 5
done
