#!/system/bin/sh
# One-time compatibility gate. No app worker starts and no relay output is written.
set -eu
umask 077
state=/data/local/panalume
stage=/data/local/tmp/wallpanel-mcu-gate
test ! -e "$state/mcu-gate.version" || { echo 'MCU gate already installed'; exit 3; }
test "$(getprop init.svc.panalume_mqtt)" = running
"$stage/wallpanel-bridge-lease" --hardware-info > "$stage/profile.json"
grep -q '"layout_matched":true' "$stage/profile.json"
grep -q '"cooperative_mcu_lease":1' "$stage/profile.json"
"$stage/wallpanel-bridge-lease" --mcu-lease-status
backup="$state/before-app-mcu-gate.$(date +%Y%m%d-%H%M%S)"
mkdir "$backup"
cp -p "$state/panel-mqtt" "$backup/panel-mqtt"
cp "$state/backlight.pid" "$backup/backlight.pid"
cat > "$backup/recover.sh" <<'RECOVER'
#!/system/bin/sh
set -eu
state=/data/local/panalume
backup=$1
setprop ctl.stop panalume_mqtt
count=0
while [ "$(getprop init.svc.panalume_mqtt)" != stopped ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
test "$(getprop init.svc.panalume_mqtt)" = stopped
cp -p "$backup/panel-mqtt" "$state/panel-mqtt.gate-restore"
mv "$state/panel-mqtt.gate-restore" "$state/panel-mqtt"
rm -f "$state/mcu-gate.version" "$state/mcu-gate.backup"
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
        if ! sh "$backup/recover.sh" "$backup"; then echo 'Recovery retry remains scheduled by watchdog';exit "$result";fi
    fi
    if [ "$watchdog" != 0 ]; then kill "$watchdog" 2>/dev/null || true;fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
/system/bin/setsid /system/bin/sh "$backup/watchdog.sh" "$backup" </dev/null > "$backup/watchdog.log" 2>&1 &
watchdog=$!
count=0
while [ ! -s "$backup/watchdog.pid" ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
test -s "$backup/watchdog.pid"
read watchdog < "$backup/watchdog.pid"
kill -0 "$watchdog"
changed=1
setprop ctl.stop panalume_mqtt
count=0
while [ "$(getprop init.svc.panalume_mqtt)" != stopped ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
test "$(getprop init.svc.panalume_mqtt)" = stopped
cp "$stage/wallpanel-bridge-lease" "$state/panel-mqtt.gate-new"
chown 0:0 "$state/panel-mqtt.gate-new"
chmod 700 "$state/panel-mqtt.gate-new"
mv "$state/panel-mqtt.gate-new" "$state/panel-mqtt"
setprop ctl.start panalume_mqtt
healthy=0;count=0
while [ "$count" -lt 90 ]; do
    if [ -s "$state/mqtt.pid" ] && [ -s /dev/wallpanel-mcu-status.json ]; then
        read pid < "$state/mqtt.pid"
        case "$pid" in ''|*[!0-9]*) pid=0 ;; esac
        if [ "$pid" != 0 ] && kill -0 "$pid" 2>/dev/null && grep -q "\"pid\":$pid," /dev/wallpanel-mcu-status.json && grep -q '"online":true' /dev/wallpanel-mcu-status.json; then healthy=1;break;fi
    fi
    sleep 0.5;count=$((count+1))
done
test "$healthy" = 1
cmp -s "$state/backlight.pid" "$backup/backlight.pid"
printf '1\n' > "$state/mcu-gate.version.new"
mv "$state/mcu-gate.version.new" "$state/mcu-gate.version"
printf '%s\n' "$backup" > "$state/mcu-gate.backup"
touch "$backup/committed"
echo "COMMITTED $backup"
