#!/system/bin/sh
# Initial trusted-root entry point. Source is fixed signed-APK staging, not RPC input.
set -eu
umask 077
target=/data/local/wallpanel
source=${1:-}
mode=${2:-apply}
case "$source" in /data/local/tmp/wall-panel-setup.*|/data/user/0/local.wallpanel.app/files/panel-setup) ;; *) echo '{"v":1,"ok":false,"error":"invalid_staging_path"}';exit 2 ;; esac
case "$mode" in apply|bootstrap-only) ;; *) exit 2 ;; esac
test ! -d "$target/setup/operation.lock" || { echo '{"v":1,"ok":false,"error":"setup_already_running"}';exit 1; }
test "$(id -u)" = 0 || { echo '{"v":1,"ok":false,"error":"root_required"}';exit 1; }
uid=$(stat -c %u /data/user/0/local.wallpanel.app)
case "$uid" in 1[0-9][0-9][0-9][0-9]) ;; *) exit 2 ;; esac
"$source/wallpanel-core" --probe | grep -q '"layout_matched":true' || { echo '{"v":1,"ok":false,"error":"unsupported_hardware"}';exit 1; }
test "$(getenforce)" = Permissive || { echo '{"v":1,"ok":false,"error":"selinux_policy_setup_required"}';exit 1; }
test ! -L "$target"
mkdir -p "$target";chmod 711 "$target"
mkdir "$target/bootstrap.lock" 2>/dev/null || { echo '{"v":1,"ok":false,"error":"bootstrap_busy"}';exit 1; }
backup="$target/bootstrap-backups/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$backup"
files='wallpanel-core wallpanel-capture wallpanel-privilege wallpanel-system-ui.jar setup-panel.sh start-privilege.sh'
for file in $files;do test ! -f "$target/$file" || cp -p "$target/$file" "$backup/$file";done
changed=0
restore_bootstrap(){
    result=$?;trap - EXIT
    if [ "$result" != 0 ] && [ "$changed" = 1 ];then
        if [ -s "$target/privilege.pid" ];then read failed < "$target/privilege.pid";case "$failed" in ''|*[!0-9]*) : ;; *) kill "$failed" 2>/dev/null || true ;; esac;fi
        for file in $files;do if [ -f "$backup/$file" ];then cp -p "$backup/$file" "$target/$file.restore";mv "$target/$file.restore" "$target/$file";else rm -f "$target/$file";fi;done
        if [ -f "$target/start-privilege.sh" ];then /system/bin/setsid /system/bin/sh "$target/start-privilege.sh" </dev/null >/dev/null 2>&1 & fi
    fi
    rmdir "$target/bootstrap.lock" 2>/dev/null || true
    exit "$result"
}
trap restore_bootstrap EXIT
trap 'exit 130' HUP INT TERM
setprop ctl.stop wallpanel_privilege || true
if [ -s "$target/privilege.pid" ];then
    read pid < "$target/privilege.pid"
    case "$pid" in ''|*[!0-9]*) exit 2 ;; esac
    if [ "$(readlink "/proc/$pid/exe" 2>/dev/null || true)" = "$target/wallpanel-privilege" ];then kill "$pid" 2>/dev/null || true;fi
fi
count=0
while [ -e "$target/privilege.pid" ] && [ "$count" -lt 50 ];do sleep 0.1;count=$((count+1));done
if [ -f "$target/privilege.pid" ];then
    read stale < "$target/privilege.pid"
    case "$stale" in ''|*[!0-9]*) exit 2 ;; esac
    if [ "$(readlink "/proc/$stale/exe" 2>/dev/null || true)" = "$target/wallpanel-privilege" ];then kill -KILL "$stale" 2>/dev/null || true;fi
    rm -f "$target/privilege.pid"
fi
changed=1
for file in wallpanel-core wallpanel-capture wallpanel-privilege wallpanel-system-ui.jar setup-panel.sh;do
    test -f "$source/$file" && test ! -L "$source/$file"
    cp "$source/$file" "$target/$file.new";chown 0:0 "$target/$file.new";chmod 700 "$target/$file.new";mv "$target/$file.new" "$target/$file"
done
cat > "$target/start-privilege.sh.new" <<WRAPPER
#!/system/bin/sh
set -eu
umask 077
if [ -s $target/setup/apply-active ];then
    read pending < $target/setup/apply-active
    /system/bin/sh $target/setup-panel.sh recover "\$pending" >> $target/privilege.log 2>&1
fi
exec $target/wallpanel-privilege $uid /data/user/0/local.wallpanel.app/files/g10-runtime/worker-input.conf $target >> $target/privilege.log 2>&1
WRAPPER
chown 0:0 "$target/start-privilege.sh.new";chmod 700 "$target/start-privilege.sh.new";mv "$target/start-privilege.sh.new" "$target/start-privilege.sh"
if [ "$mode" = apply ];then /system/bin/setsid /system/bin/sh "$target/setup-panel.sh" apply;fi
/system/bin/setsid /system/bin/sh "$target/start-privilege.sh" </dev/null >/dev/null 2>&1 &
ready=0;count=0
while [ "$count" -lt 50 ];do
    if [ -s "$target/privilege.pid" ] && [ -S "$target/control.sock" ];then read active < "$target/privilege.pid";case "$active" in ''|*[!0-9]*) : ;; *) if kill -0 "$active" 2>/dev/null;then ready=1;break;fi ;; esac;fi
    sleep 0.1;count=$((count+1))
done
test "$ready" = 1
printf '%s\n' "$backup" > "$target/last-bootstrap-backup"
if [ "$mode" = bootstrap-only ];then echo '{"v":1,"ok":true,"status":"bootstrap_ready"}';fi
