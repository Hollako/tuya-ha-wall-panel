#!/system/bin/sh
set -eu
state=/data/local/panalume
read backup < "$state/unified-home.enabled"
printf '%s\n' "$backup" | grep -Eq '^/data/local/panalume/before-unified-home\.[0-9]{8}-[0-9]{6}$'
test -f "$backup/committed"
for part in network compat; do
    test -f "$backup/start-$part.sh"
    cp -p "$backup/start-$part.sh" "$state/start-$part.sh.home-restore"
done
rm "$state/unified-home.enabled"
for part in network compat; do
    mv "$state/start-$part.sh.home-restore" "$state/start-$part.sh"
    read old < "$backup/$part.service"
    case "$old" in running|restarting|starting) setprop ctl.start "panalume_$part" ;; *) setprop ctl.stop "panalume_$part" ;; esac
done
echo 'Original browser startup restored; hardware services untouched.'
