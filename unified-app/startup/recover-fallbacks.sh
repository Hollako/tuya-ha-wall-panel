#!/system/bin/sh
set -eu
umask 077
state=/data/local/panalume
backup=$1
case "$backup" in "$state"/before-app-only.*) ;; *) exit 2 ;; esac
test -f "$backup/init/panalume-panel.rc" && test -f "$backup/init/panalume-mqtt.rc"
mkdir "$backup/recovery-lock" 2>/dev/null || exit 0
trap 'rmdir "$backup/recovery-lock"' EXIT
am start-foreground-service -n local.wallpanel.app/.BridgeService -a local.wallpanel.app.STOP_HARDWARE >/dev/null 2>&1 || true
am force-stop local.wallpanel.app
setprop ctl.stop wallpanel_privilege || true
if [ -s /data/local/wallpanel/privilege.pid ];then
    read broker < /data/local/wallpanel/privilege.pid
    case "$broker" in ''|*[!0-9]*) exit 2 ;; esac
    kill "$broker" 2>/dev/null || true
fi
# Restore the saved setup while explicitly keeping updated-app hardware stopped.
# The app is stopped; writing its existing private file preserves ownership/mode.
prefs=/data/user/0/local.wallpanel.app/shared_prefs/wall_panel.xml
test -f "$prefs"
sed -e 's/name="runtime_enabled" value="true"/name="runtime_enabled" value="false"/' -e 's/name="runtime_verified" value="true"/name="runtime_verified" value="false"/' "$backup/wall_panel.xml" > "$prefs"
grep -q 'name="runtime_enabled" value="false"' "$prefs"
count=0
while ! "$backup/retired/panel-mqtt" --mcu-lease-status >/dev/null 2>&1 && [ "$count" -lt 50 ];do sleep 0.1;count=$((count+1));done
"$backup/retired/panel-mqtt" --mcu-lease-status >/dev/null
mount -o remount,rw /system
rm -f /system/etc/init/wallpanel-privilege.rc
cp -p "$backup/init/panalume-panel.rc" "$backup/init/panalume-mqtt.rc" /system/etc/init/
sync
mount -o remount,ro /system
for part in panel mqtt network compat;do cp -p "$backup/retired/start-$part.sh" "$state/start-$part.sh";done
for file in panel-mqtt panel-backlight panel-screenshot;do cp -p "$backup/retired/$file" "$state/$file";done
rm -f "$state/app-only.version" "$state/app-only.pending" "$state/app-only.backup"
read enabled < "$backup/audio-enabled"
case "$enabled" in 0) pm default-state --user 0 local.wallpanel.audio ;; 1) pm enable --user 0 local.wallpanel.audio ;; 3) pm disable-user --user 0 local.wallpanel.audio ;; *) exit 2 ;; esac
for part in panel mqtt network compat;do
    read old < "$backup/$part.service"
    case "$old" in running|restarting|starting) setprop ctl.start "panalume_$part" ;; *) setprop ctl.stop "panalume_$part" ;; esac
done
touch "$backup/recovered"
echo "RESTORED $backup; updated app hardware is stopped"
