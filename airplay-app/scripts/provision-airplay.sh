#!/system/bin/sh
# Trusted ADB-root installation. Touches only the separate AirPlay directory.
set -eu
umask 077
base=/data/local/wpar-airplay
stage=/data/local/tmp/wpar-airplay-stage
test "$(id -u)" = 0
test "$(getenforce)" = Permissive || { echo 'SELinux policy required; no global changes made';exit 1; }
uid=$(stat -c %u /data/user/0/com.wallpanel.audioreceiver)
case "$uid" in 1[0-9][0-9][0-9][0-9]) ;; *) exit 2 ;; esac
test ! -L "$base"
mkdir -p "$base";chown 0:0 "$base";chmod 711 "$base"
test ! -L "$base/shm"
mkdir -p "$base/shm";chown "$uid:$uid" "$base/shm";chmod 700 "$base/shm"
for name in wpar-ptp-helper nqptp;do
 test -f "$stage/$name" && test ! -L "$stage/$name"
 cp "$stage/$name" "$base/$name.new";chown 0:0 "$base/$name.new";chmod 700 "$base/$name.new";mv "$base/$name.new" "$base/$name"
done
cat > "$base/start-helper.sh.new" <<'WRAPPER'
#!/system/bin/sh
set -eu
uid=$(stat -c %u /data/user/0/com.wallpanel.audioreceiver)
case "$uid" in 1[0-9][0-9][0-9][0-9]) ;; *) exit 2 ;; esac
test "$(stat -c %u /data/local/wpar-airplay/shm)" = "$uid"
exec /data/local/wpar-airplay/wpar-ptp-helper "$uid" >> /data/local/wpar-airplay/helper.log 2>&1
WRAPPER
chown 0:0 "$base/start-helper.sh.new";chmod 700 "$base/start-helper.sh.new";mv "$base/start-helper.sh.new" "$base/start-helper.sh"
/system/bin/setsid /system/bin/sh "$base/start-helper.sh" </dev/null >/dev/null 2>&1 &
echo "AirPlay helper provisioned for UID $uid. Boot entry installed separately after verification."
