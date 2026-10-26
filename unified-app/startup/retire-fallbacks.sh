#!/system/bin/sh
# Fixed G10 migration only; independent watchdog restores the old installation.
set -eu
umask 077
state=/data/local/panalume
stage=/data/local/tmp/wallpanel-app-only
pending="$state/app-only.pending"
case "${1:-}" in
prepare)
    test ! -e "$state/app-only.version" && test ! -e "$pending"
    for part in mcu knob audio display management; do test "$(cat "$state/$part-gate.version")" = 1;done
    test -f /system/etc/init/panalume-panel.rc && test -f /system/etc/init/panalume-mqtt.rc
    test ! -e /system/etc/init/wallpanel-privilege.rc
    test -f "$stage/wallpanel-privilege.rc" && test -s /data/local/wallpanel/privilege.pid
    mount -o remount,rw /system
    mount -o remount,ro /system
    test "$(cat "$stage/audio-enabled")" = 0 || test "$(cat "$stage/audio-enabled")" = 1 || test "$(cat "$stage/audio-enabled")" = 3
    backup="$state/before-app-only.$(date +%Y%m%d-%H%M%S)"
    mkdir "$backup" "$backup/init" "$backup/retired"
    cp -p /system/etc/init/panalume-panel.rc /system/etc/init/panalume-mqtt.rc "$backup/init/"
    cp -p /data/user/0/local.wallpanel.app/shared_prefs/wall_panel.xml "$backup/wall_panel.xml"
    cp "$stage/audio-enabled" "$backup/audio-enabled"
    for part in panel mqtt network compat; do cp -p "$state/start-$part.sh" "$backup/retired/";getprop "init.svc.panalume_$part" > "$backup/$part.service";done
    for file in panel-mqtt panel-backlight panel-screenshot; do cp -p "$state/$file" "$backup/retired/";done
    cp "$stage/recover-fallbacks.sh" "$backup/recover.sh"
    cat > "$backup/watchdog.sh" <<'WATCHDOG'
#!/system/bin/sh
trap '' HUP
backup=$1
echo $$ > "$backup/watchdog.pid"
sleep 120
test -f "$backup/committed" || sh "$backup/recover.sh" "$backup"
WATCHDOG
    printf '%s\n' "$backup" > "$pending"
    /system/bin/setsid /system/bin/sh "$backup/watchdog.sh" "$backup" </dev/null > "$backup/watchdog.log" 2>&1 &
    count=0
    while [ ! -s "$backup/watchdog.pid" ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
    test -s "$backup/watchdog.pid"
    changed=1
    cleanup(){ result=$?;trap - EXIT;if [ "$result" != 0 ];then sh "$backup/recover.sh" "$backup" || true;fi;exit "$result"; }
    trap cleanup EXIT
    trap 'exit 130' HUP INT TERM
    for part in mqtt panel network compat; do
        setprop ctl.stop "panalume_$part"
        count=0
        while [ "$(getprop init.svc.panalume_$part)" != stopped ] && [ "$count" -lt 50 ];do sleep 0.1;count=$((count+1));done
        test "$(getprop init.svc.panalume_$part)" = stopped
    done
    pm disable-user --user 0 local.wallpanel.audio
    am force-stop local.wallpanel.audio
    mount -o remount,rw /system
    rm -f /system/etc/init/panalume-panel.rc /system/etc/init/panalume-mqtt.rc
    cp "$stage/wallpanel-privilege.rc" /system/etc/init/wallpanel-privilege.rc
    chown 0:0 /system/etc/init/wallpanel-privilege.rc
    chmod 644 /system/etc/init/wallpanel-privilege.rc
    restorecon /system/etc/init/wallpanel-privilege.rc
    sync
    mount -o remount,ro /system
    for part in panel mqtt network compat; do
        printf '%s\n' '#!/system/bin/sh' "setprop ctl.stop panalume_$part || true" 'while :;do sleep 3600;done' > "$state/start-$part.sh"
        chmod 700 "$state/start-$part.sh"
    done
    for file in panel-mqtt panel-backlight panel-screenshot;do rm -f "$state/$file";done
    printf '1\n' > "$state/app-only.version"
    echo "PREPARED $backup"
    ;;
commit)
    read backup < "$pending"
    case "$backup" in "$state"/before-app-only.*) ;; *) exit 2 ;; esac
    test -f "$state/app-only.version" && test -f "$backup/watchdog.pid"
    mkdir "$backup/recovery-lock"
    trap 'rmdir "$backup/recovery-lock"' EXIT
    test ! -f "$backup/recovered"
    touch "$backup/committed"
    read watchdog < "$backup/watchdog.pid"
    case "$watchdog" in ''|*[!0-9]*) exit 2 ;; esac
    kill "$watchdog" 2>/dev/null || true
    mv "$pending" "$state/app-only.backup"
    echo "COMMITTED $backup"
    ;;
*) echo 'Expected prepare or commit';exit 2 ;;
esac
