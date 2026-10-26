#!/system/bin/sh
set -eu
umask 077
exec /data/local/wallpanel/wallpanel-privilege 10065 /data/user/0/local.wallpanel.app/files/g10-runtime/worker-input.conf /data/local/wallpanel >> /data/local/wallpanel/privilege.log 2>&1
