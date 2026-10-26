#!/system/bin/sh
set -eu
umask 077
target=/data/local/wallpanel
stage=/data/local/tmp/wallpanel-boot-fix
backup="$target/before-shell-boot.$(date +%Y%m%d-%H%M%S)"
mkdir "$backup"
cp -p /system/etc/init/wallpanel-privilege.rc "$backup/wallpanel-privilege.rc"
test ! -f "$target/start-privilege.sh" || cp -p "$target/start-privilege.sh" "$backup/start-privilege.sh"
setprop ctl.stop wallpanel_privilege
cp "$stage/start-privilege.sh" "$target/start-privilege.sh"
chown 0:0 "$target/start-privilege.sh"
chmod 700 "$target/start-privilege.sh"
mount -o remount,rw /system
trap 'mount -o remount,ro /system' EXIT
cp "$stage/wallpanel-privilege.rc" /system/etc/init/wallpanel-privilege.rc
chown 0:0 /system/etc/init/wallpanel-privilege.rc
chmod 644 /system/etc/init/wallpanel-privilege.rc
restorecon /system/etc/init/wallpanel-privilege.rc
sync
mount -o remount,ro /system
trap - EXIT
# Existing init keeps its old command until the user's next boot.
/system/bin/setsid /system/bin/sh "$target/start-privilege.sh" </dev/null >/dev/null 2>&1 &
printf '%s
' "$backup"
