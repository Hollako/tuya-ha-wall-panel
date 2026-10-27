#!/system/bin/sh
set -eu
test "$(id -u)" = 0
case "${1:-system}" in
 system) file=/system/etc/init/wpar-airplay-ptp.rc ;;
 vendor) file=/vendor/etc/init/wpar-airplay-ptp.rc ;;
 *) exit 2 ;;
esac
stage=/data/local/tmp/wpar-airplay-stage
# Add only this new service. Refuse to overwrite an existing definition.
test ! -e "$file"
was_ro=0
if [ "$file" = /system/etc/init/wpar-airplay-ptp.rc ];then
 grep ' /system .* ro,' /proc/mounts >/dev/null && was_ro=1 || true
fi
if [ "$was_ro" = 1 ];then mount -o remount,rw /system;fi
trap 'if [ "$was_ro" = 1 ];then mount -o remount,ro /system;fi' EXIT
cp "$stage/airplay-ptp.rc" "$file.new"
chown 0:0 "$file.new";chmod 644 "$file.new";mv "$file.new" "$file"
restorecon "$file"
sync
if [ "$was_ro" = 1 ];then mount -o remount,ro /system;fi
trap - EXIT
echo 'Independent AirPlay init entry added. Reboot test pending; no reboot performed.'
