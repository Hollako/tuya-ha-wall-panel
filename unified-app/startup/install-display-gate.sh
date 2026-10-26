#!/system/bin/sh
# Back up both display fallback and bridge; detached watchdog survives caller loss.
set -eu
umask 077
state=/data/local/panalume
stage=/data/local/tmp/wallpanel-display-gate
test "$(cat "$state/audio-gate.version")" = 1
test ! -e "$state/display-gate.version"
test "$(getprop init.svc.panalume_mqtt)" = running
test "$(getprop init.svc.panalume_panel)" = running
"$stage/wallpanel-bridge-lease" --hardware-info > "$stage/profile.json"
grep -q '"layout_matched":true' "$stage/profile.json"
"$stage/panel-backlight" --describe > "$stage/display.json"
grep -q '"display_gate":1' "$stage/display.json"
"$stage/wallpanel-bridge-lease" --mcu-lease-status
backup="$state/before-app-display-gate.$(date +%Y%m%d-%H%M%S)"
mkdir "$backup"
cp -p "$state/panel-mqtt" "$backup/panel-mqtt"
cp -p "$state/panel-backlight" "$backup/panel-backlight"
cat > "$backup/recover.sh" <<'RECOVER'
#!/system/bin/sh
set -eu
state=/data/local/panalume
backup=$1
for service in panalume_mqtt panalume_panel; do
    setprop ctl.stop "$service"
    count=0;while [ "$(getprop init.svc.$service)" != stopped ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
    test "$(getprop init.svc.$service)" = stopped
done
for binary in panel-mqtt panel-backlight; do cp -p "$backup/$binary" "$state/$binary.restore";mv "$state/$binary.restore" "$state/$binary";done
rm -f "$state/display-gate.version" "$state/display-gate.backup"
setprop ctl.start panalume_panel
setprop ctl.start panalume_mqtt
RECOVER
cat > "$backup/watchdog.sh" <<'WATCHDOG'
#!/system/bin/sh
trap '' HUP
backup=$1
echo $$ > "$backup/watchdog.pid"
sleep 60
if [ ! -f "$backup/committed" ]; then sh "$backup/recover.sh" "$backup";fi
WATCHDOG
changed=0;watchdog=0
cleanup(){
    result=$?;trap - EXIT
    if [ "$result" != 0 ] && [ "$changed" = 1 ]; then
        if ! sh "$backup/recover.sh" "$backup"; then echo 'Recovery retry remains scheduled';exit "$result";fi
    fi
    if [ "$watchdog" != 0 ]; then kill "$watchdog" 2>/dev/null || true;fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
/system/bin/setsid /system/bin/sh "$backup/watchdog.sh" "$backup" </dev/null > "$backup/watchdog.log" 2>&1 &
watchdog=$!
count=0;while [ ! -s "$backup/watchdog.pid" ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
test -s "$backup/watchdog.pid"
read watchdog < "$backup/watchdog.pid"
kill -0 "$watchdog"
changed=1
for service in panalume_mqtt panalume_panel; do
    setprop ctl.stop "$service"
    count=0;while [ "$(getprop init.svc.$service)" != stopped ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
    test "$(getprop init.svc.$service)" = stopped
done
cp "$stage/wallpanel-bridge-lease" "$state/panel-mqtt.display-new"
cp "$stage/panel-backlight" "$state/panel-backlight.display-new"
for binary in panel-mqtt panel-backlight; do chown 0:0 "$state/$binary.display-new";chmod 700 "$state/$binary.display-new";mv "$state/$binary.display-new" "$state/$binary";done
setprop ctl.start panalume_panel
setprop ctl.start panalume_mqtt
healthy=0;count=0
while [ "$count" -lt 90 ]; do
    if [ -s "$state/mqtt.pid" ] && [ -s /dev/wallpanel-mcu-status.json ] && [ -s "$state/backlight.pid" ] && [ -s /dev/wallpanel-display-state ]; then
        read pid < "$state/mqtt.pid";read display_pid < "$state/backlight.pid"
        case "$pid:$display_pid" in *[!0-9:]*|:*|*:) pid=0;display_pid=0 ;; esac
        if [ "$pid" != 0 ] && [ "$display_pid" != 0 ] && kill -0 "$pid" 2>/dev/null && kill -0 "$display_pid" 2>/dev/null && grep -q "\"pid\":$pid," /dev/wallpanel-mcu-status.json && grep -q '"online":true' /dev/wallpanel-mcu-status.json && grep -q "^v1 $display_pid " /dev/wallpanel-display-state; then healthy=1;break;fi
    fi
    sleep 0.5;count=$((count+1))
done
test "$healthy" = 1
printf '1\n' > "$state/display-gate.version.new"
mv "$state/display-gate.version.new" "$state/display-gate.version"
printf '%s\n' "$backup" > "$state/display-gate.backup"
touch "$backup/committed"
echo "COMMITTED $backup"
