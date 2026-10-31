#!/system/bin/sh
# Root-owned fixed provisioning policy. Never source app data or switch any output.
set -eu
umask 077
target=/data/local/wallpanel
state=/data/local/wallpanel/a133-state
init_root=/vendor
test -d "$init_root/etc/init" || init_root=/system
was_ro=0
mount_flags=$(awk -v p="$init_root" '$2==p {v=$4} END {print v}' /proc/mounts)
case ",$mount_flags," in *,ro,*) was_ro=1 ;; esac
setup="$target/setup"
appdata=/data/user/0/local.wallpanel.app
packages='com.sznaner.bgmz9 com.sznaner.rotaryknob com.sznaner.relay com.sznaner.pulldownmenu com.sznaner.backbtn com.sznaner.volumedialog com.sznaner.powermenu'
services=''
configs='display-brightness display-policy knob-settings button-local-bindings audio-output-trim'
legacy=''
inits='wallpanel-privilege.rc'
exec 3>&1
emit(){ printf '%s\n' "$1" >&3; }
fail(){ emit "{\"v\":1,\"ok\":false,\"error\":\"$1\"}";exit 1; }
install_boot(){
    cat > "$setup/boot.rc" <<'RC'
# App-only bootstrap through the system executable (data is mounted nosuid).
service wallpanel_privilege /system/bin/sh /data/local/wallpanel/start-privilege.sh
    class late_start
    user root
    group root input system inet
    seclabel u:r:su:s0
    disabled

on property:sys.boot_completed=1
    start wallpanel_privilege
RC
    mount -o remount,rw "$init_root"
    cp "$setup/boot.rc" $init_root/etc/init/wallpanel-privilege.rc.new
    chown 0:0 $init_root/etc/init/wallpanel-privilege.rc.new
    chmod 644 $init_root/etc/init/wallpanel-privilege.rc.new
    restorecon $init_root/etc/init/wallpanel-privilege.rc.new
    mv $init_root/etc/init/wallpanel-privilege.rc.new $init_root/etc/init/wallpanel-privilege.rc
    sync;if [ "$was_ro" = 1 ];then mount -o remount,ro "$init_root";fi
}
test "$(id -u)" = 0 || fail root_required
uid=$(stat -c %u "$appdata")
case "$uid" in 1[0-9][0-9][0-9][0-9]) ;; *) fail unsupported_app_user ;; esac
package_state(){ dumpsys package "$1" | sed -n 's/.*User 0:.* enabled=\([0-4]\).*/\1/p' | head -n 1; }
matched=false
probe=$("$target/wallpanel-core" --probe 2>/dev/null || true)
if printf '%s' "$probe" | grep -q '"layout_matched":true' && printf '%s' "$probe" | grep -q '"profile":"a133"';then matched=true;fi
boot=false
if test -f "$target/start-privilege.sh" && grep -q '^service wallpanel_privilege /system/bin/sh /data/local/wallpanel/start-privilege.sh$' $init_root/etc/init/wallpanel-privilege.rc 2>/dev/null;then boot=true;fi
conflicts=false
for package in $packages;do case "$(package_state "$package")" in 0|1) conflicts=true ;; esac;done

backup_available=false;if [ -f "$setup/last-backup" ] || [ -f "$setup/apply-active" ];then backup_available=true;fi
policy=false;test "$(getenforce)" != Permissive || policy=true
if [ "${1:-}" = check ];then
    emit "{\"v\":1,\"ok\":true,\"setup_protocol\":2,\"root\":true,\"profile\":\"a133\",\"supported\":$matched,\"policy_ready\":$policy,\"boot_ready\":$boot,\"conflicts\":$conflicts,\"backup_available\":$backup_available}"
    exit 0
fi
restore_backup(){
    restore=$1
    case "$restore" in "$setup"/backups/*) ;; *) return 1 ;; esac
    test -f "$restore/complete-snapshot" || return 1
    mount -o remount,rw "$init_root"
    for file in $inits;do
        if [ -f "$restore/init/$file" ];then cp -p "$restore/init/$file" "$init_root/etc/init/$file.restore";restorecon "$init_root/etc/init/$file.restore";mv "$init_root/etc/init/$file.restore" "$init_root/etc/init/$file";elif [ "$file" != wallpanel-privilege.rc ];then rm -f "$init_root/etc/init/$file";fi
    done
    sync;if [ "$was_ro" = 1 ];then mount -o remount,ro "$init_root";fi
    for file in $configs;do
        if [ -f "$restore/config/$file" ];then cp -p "$restore/config/$file" "$state/$file";else rm -f "$state/$file";fi
    done
    for file in $legacy;do
        if [ -f "$restore/legacy/$file" ];then cp -p "$restore/legacy/$file" "$state/$file";else rm -f "$state/$file";fi
    done
    while read package enabled;do
        case "$package" in com.sznaner.bgmz9|com.sznaner.rotaryknob|com.sznaner.relay|com.sznaner.pulldownmenu|com.sznaner.backbtn|com.sznaner.volumedialog|com.sznaner.powermenu) ;; *) return 1 ;; esac
        case "$enabled" in 0) pm default-state --user 0 "$package" ;; 1) pm enable --user 0 "$package" ;; 2) pm disable --user 0 "$package" ;; 3) pm disable-user --user 0 "$package" ;; 4) pm disable-until-used --user 0 "$package" ;; absent) : ;; *) return 1 ;; esac
    done < "$restore/packages"
    read awake < "$restore/awake"
    case "$awake" in null) settings delete global stay_on_while_plugged_in ;; 0|1|2|3|4|5|6|7) settings put global stay_on_while_plugged_in "$awake" ;; *) return 1 ;; esac
    if [ -f "$restore/accessibility-services" ];then
        previous=$(cat "$restore/accessibility-services")
        if [ "$previous" = null ];then settings delete secure enabled_accessibility_services;else settings put secure enabled_accessibility_services "$previous";fi
        previous=$(cat "$restore/accessibility-enabled")
        case "$previous" in null) settings delete secure accessibility_enabled ;; 0|1) settings put secure accessibility_enabled "$previous" ;; *) return 1 ;; esac
    fi
    read home < "$restore/home"
    case "$home" in *[!A-Za-z0-9_./\$]*) : ;; */*) cmd package set-home-activity "$home" || true ;; esac
    while read service status;do
        case "$service" in panalume_panel|panalume_mqtt|panalume_network|panalume_compat) ;; *) return 1 ;; esac
        case "$status" in running|starting|restarting) setprop ctl.start "$service" ;; absent) : ;; *) setprop ctl.stop "$service" ;; esac
    done < "$restore/services"
    if [ ! -f "$restore/init/wallpanel-privilege.rc" ];then mount -o remount,rw "$init_root";rm -f $init_root/etc/init/wallpanel-privilege.rc;sync;if [ "$was_ro" = 1 ];then mount -o remount,ro "$init_root";fi;fi
    touch "$restore/restored"
}
if [ "${1:-}" = recover ];then
    mkdir -p "$setup";exec >> "$setup/setup.log" 2>&1
    restore_backup "$2"
    rm -f "$setup/apply-active"
    rmdir "$setup/operation.lock" 2>/dev/null || true
    exit 0
fi
case "${1:-}" in apply|restore) ;; *) fail invalid_setup_operation ;; esac
test "$matched" = true || fail unsupported_hardware
test "$policy" = true || fail selinux_policy_setup_required
mkdir -p "$setup/backups";chmod 700 "$setup" "$setup/backups"
mkdir "$setup/operation.lock" 2>/dev/null || fail setup_already_running
exec >> "$setup/setup.log" 2>&1
cleanup_lock(){ rmdir "$setup/operation.lock" 2>/dev/null || true; }
trap cleanup_lock EXIT
if [ "$1" = restore ];then
    if [ -f "$setup/apply-active" ];then read previous < "$setup/apply-active";else test -f "$setup/last-backup" || fail no_previous_setup;read previous < "$setup/last-backup";fi
    printf '%s\n' "$previous" > "$setup/apply-active"
    restore_backup "$previous"
    rm -f "$setup/apply-active"
    emit '{"v":1,"ok":true,"status":"restored","hardware_stopped":true}'
    exit 0
fi
test ! -f "$setup/apply-active" || fail previous_setup_needs_recovery
# Verify system access and the exact legacy scope before disabling any package.
mount -o remount,rw "$init_root" || fail system_not_writable
if [ "$was_ro" = 1 ];then mount -o remount,ro "$init_root";fi || fail system_remount_failed
backup="$setup/backups/$(date +%Y%m%d-%H%M%S)-$$"
mkdir "$backup" "$backup/init" "$backup/config" "$backup/legacy"
for file in $inits;do test ! -f "$init_root/etc/init/$file" || cp -p "$init_root/etc/init/$file" "$backup/init/$file";done
for file in $configs;do test ! -f "$state/$file" || cp -p "$state/$file" "$backup/config/$file";done
for file in $legacy;do test ! -f "$state/$file" || cp -p "$state/$file" "$backup/legacy/$file";done
for package in $packages;do enabled=$(package_state "$package");printf '%s %s\n' "$package" "${enabled:-absent}";done > "$backup/packages"
for service in $services;do value=$(getprop "init.svc.$service");printf '%s %s\n' "$service" "${value:-absent}";done > "$backup/services"
settings get global stay_on_while_plugged_in > "$backup/awake"
settings get secure enabled_accessibility_services > "$backup/accessibility-services"
settings get secure accessibility_enabled > "$backup/accessibility-enabled"
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 > "$backup/home"
touch "$backup/complete-snapshot"
printf '%s\n' "$$" > "$backup/apply.pid"
printf '%s\n' "$backup" > "$setup/apply-active"
cat > "$backup/watchdog.sh" <<'WATCHDOG'
#!/system/bin/sh
trap '' HUP
backup=$1
echo $$ > "$backup/watchdog.pid"
sleep 120
if [ ! -f "$backup/committed" ];then
    read owner < "$backup/apply.pid"
    case "$owner" in ''|*[!0-9]*) exit 2 ;; esac
    kill -KILL -"$owner" 2>/dev/null || true
    /system/bin/sh /data/local/wallpanel/setup-panel.sh recover "$backup"
fi
WATCHDOG
rollback(){
    result=$?;trap - EXIT;recovered=1
    if [ "$result" != 0 ];then
        recovered=0
        # A separate shell preserves errexit inside restoration; an OR-list around
        # the function itself would silently suppress failed restoration commands.
        if /system/bin/sh "$target/setup-panel.sh" recover "$backup";then recovered=1;fi
        if [ "$was_ro" = 1 ];then mount -o remount,ro "$init_root";fi || true
    fi
    if [ -s "$backup/watchdog.pid" ];then read guard < "$backup/watchdog.pid";kill "$guard" 2>/dev/null || true;fi
    if [ "$recovered" = 1 ];then rm -f "$setup/apply-active";fi
    cleanup_lock;exit "$result"
}
trap rollback EXIT
trap 'exit 130' HUP INT TERM
/system/bin/setsid /system/bin/sh "$backup/watchdog.sh" "$backup" </dev/null > "$backup/watchdog.log" 2>&1 3>&- &
count=0
while [ ! -s "$backup/watchdog.pid" ] && [ "$count" -lt 30 ];do sleep 0.1;count=$((count+1));done
test -s "$backup/watchdog.pid" || fail watchdog_failed
install_boot
mkdir -p "$state";chmod 700 "$state"
for file in $configs;do if [ -f "$state/$file" ];then test ! -L "$state/$file";chown 0:0 "$state/$file";chmod 600 "$state/$file";fi;done
test -f "$state/display-brightness" || printf '128\n' > "$state/display-brightness"
test -f "$state/display-policy" || printf 'v1 60 300 26\n' > "$state/display-policy"
test -f "$state/button-local-bindings" || printf '0 0\n0 0\n0 0\n0 0\n' > "$state/button-local-bindings"
cmd package set-home-activity local.wallpanel.app/.HomeActivity
test "$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)" = local.wallpanel.app/.HomeActivity
for service in $services;do
    value=$(getprop "init.svc.$service")
    if [ -n "$value" ];then
        setprop ctl.stop "$service";count=0
        while [ "$(getprop "init.svc.$service")" != stopped ] && [ "$count" -lt 50 ];do sleep 0.1;count=$((count+1));done
        test "$(getprop "init.svc.$service")" = stopped
    fi
done
while read package enabled;do
    case "$enabled" in absent|3) : ;; *) pm disable-user --user 0 "$package";am force-stop "$package" ;; esac
done < "$backup/packages"
settings put global stay_on_while_plugged_in 7
# A fixed settings-only Back button; preserve all other accessibility services.
navigation=local.wallpanel.app/local.wallpanel.app.SettingsBackService
previous=$(settings get secure enabled_accessibility_services)
case "$previous" in null|'') previous= ;; esac
case ":$previous:" in *":$navigation:"*|*":local.wallpanel.app/.SettingsBackService:"*) : ;; *)
    settings put secure enabled_accessibility_services "${previous:+$previous:}$navigation" ;;
esac
settings put secure accessibility_enabled 1
# Never relax /dev permissions. Root worker owns I/O; only its socket belongs to app.
chmod 711 "$target"
for file in wallpanel-core wallpanel-capture wallpanel-privilege wallpanel-system-ui.jar setup-panel.sh start-privilege.sh;do chown 0:0 "$target/$file";chmod 700 "$target/$file";done
for file in $legacy;do rm -f "$state/$file";done
printf '%s\n' "$backup" > "$setup/last-backup"
printf '2\n' > "$setup/version"
touch "$backup/committed"
emit '{"v":1,"ok":true,"status":"prepared","backup_created":true,"reboot_required":true}'
