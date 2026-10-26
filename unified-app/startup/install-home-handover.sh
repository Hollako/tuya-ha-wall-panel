#!/system/bin/sh
# Retire only the old browser guardians. Do not edit init/system or hardware services.
set -eu
umask 077
state=/data/local/panalume
test ! -e "$state/unified-home.enabled" || { echo 'Unified Home handover already installed'; exit 3; }
test -f "$state/start-network.sh" && test -f "$state/start-compat.sh"
test -s "$state/mqtt.pid" && test -s "$state/backlight.pid"
backup="$state/before-unified-home.$(date +%Y%m%d-%H%M%S)"
mkdir "$backup"
cp -p "$state/start-network.sh" "$backup/start-network.sh"
cp -p "$state/start-compat.sh" "$backup/start-compat.sh"
cp "$state/mqtt.pid" "$backup/mqtt.pid"
cp "$state/backlight.pid" "$backup/backlight.pid"
getprop init.svc.panalume_network > "$backup/network.service"
getprop init.svc.panalume_compat > "$backup/compat.service"
changed=0
rollback(){
    if [ "$changed" = 1 ]; then
        rm -f "$state/unified-home.enabled" "$state/unified-home.enabled.new"
        for part in network compat; do
            cp -p "$backup/start-$part.sh" "$state/start-$part.sh.home-restore"
            mv "$state/start-$part.sh.home-restore" "$state/start-$part.sh"
            read old < "$backup/$part.service"
            case "$old" in running|restarting|starting) setprop ctl.start "panalume_$part" ;; *) setprop ctl.stop "panalume_$part" ;; esac
        done
    fi
}
cleanup(){ result=$?;trap - EXIT;if [ "$result" != 0 ]; then rollback;fi;exit "$result"; }
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
for part in network compat; do
    {
        printf '%s\n' '#!/system/bin/sh' '# wallpanel-unified-home-gate-v1' \
            "if [ -f '$state/unified-home.enabled' ]; then" \
            "    setprop ctl.stop panalume_$part || true" \
            '    while :; do sleep 3600; done' 'fi'
        cat "$backup/start-$part.sh"
    } > "$state/start-$part.sh.home-new"
    chmod 700 "$state/start-$part.sh.home-new"
    chown 0:0 "$state/start-$part.sh.home-new"
done
changed=1
for part in network compat; do
    setprop ctl.stop "panalume_$part"
    count=0
    while [ "$(getprop init.svc.panalume_$part)" != stopped ] && [ "$count" -lt 30 ]; do sleep 0.1;count=$((count+1));done
    test "$(getprop init.svc.panalume_$part)" = stopped
done
for part in network compat; do mv "$state/start-$part.sh.home-new" "$state/start-$part.sh";done
printf '%s\n' "$backup" > "$state/unified-home.enabled.new"
mv "$state/unified-home.enabled.new" "$state/unified-home.enabled"
cmp -s "$state/mqtt.pid" "$backup/mqtt.pid"
cmp -s "$state/backlight.pid" "$backup/backlight.pid"
touch "$backup/committed"
echo "COMMITTED $backup"
