#!/system/bin/sh
set -eu
umask 077
mkdir -p /data/local/wallpanel
chmod 711 /data/local/wallpanel
for file in wallpanel-privilege wallpanel-core wallpanel-capture;do
    cp /data/local/tmp/wallpanel-app-only/$file /data/local/wallpanel/$file.new
    chown 0:0 /data/local/wallpanel/$file.new
    chmod 700 /data/local/wallpanel/$file.new
    mv /data/local/wallpanel/$file.new /data/local/wallpanel/$file
done
cp /data/local/tmp/wallpanel-app-only/start-privilege.sh /data/local/wallpanel/start-privilege.sh
chown 0:0 /data/local/wallpanel/start-privilege.sh
chmod 700 /data/local/wallpanel/start-privilege.sh
/system/bin/setsid /system/bin/sh /data/local/wallpanel/start-privilege.sh </dev/null >/dev/null 2>&1 &
