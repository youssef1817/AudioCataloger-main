#!/bin/bash

archs="64"

# [ "$(uname -a)" = "x86_64" ]  will work as well but 32 bit response will be i386, i686, i86pc ...
if [ "$(getconf LONG_BIT)" != "$archs" ]; then
	echo "This version of Maknoon Audio Cataloger is not for this platform $(uname -m)"
	exit 1
fi

if [ "$(uname)" != "Linux" ]; then
	echo "This version of Maknoon Audio Cataloger is not for this platform $(uname)"
	exit 1
fi

which vlc > /dev/null
if [ $? != 0 ]; then
	echo "VLC is not installed. please install it"
	exit 1
fi

OS=`awk -F= '/^NAME/{print $2}' /etc/os-release`
if [[ $OS == *"Ubuntu"* ]]; then
	COUNT=`dpkg-query -l | grep libvlc | wc -l`
	if [ "$COUNT" -eq "0" ]; then
		echo "VLC is not installed by apt-get. please install it manually using 'sudo snap install vlc' and remove the snap version if installed"
		exit 1
	fi
fi

ARCHIVE=`awk '/^__ARCHIVE_BELOW__/ {print NR + 1; exit 0; }' $0`

mkdir AudioCataloger
cd AudioCataloger
CDIR=`pwd`
tail -n+$ARCHIVE ../$0 | tar -xzmo

echo "[Desktop Entry]
Comment=برنامج المفهرس لمسموعات أهل العلم من أهل السنة والجماعة
Exec=\"$CDIR/startup.sh\"
Name=مفهرس المحاضرات
Icon=$CDIR/resources/images/icon_128.png
Terminal=false
Type=Application
Path=$CDIR" > maknoon-audiocataloger.desktop

xdg-desktop-menu install maknoon-audiocataloger.desktop

if [ "$(id -u)" != "0" ]; then
	xdg-desktop-icon install maknoon-audiocataloger.desktop
fi

xdg-icon-resource install --context apps --size 16 resources/images/icon.png application-audiocataloger
xdg-icon-resource install --context apps --size 32 resources/images/icon_32.png application-audiocataloger
xdg-icon-resource install --context apps --size 64 resources/images/icon_64.png application-audiocataloger
xdg-icon-resource install --context apps --size 128 resources/images/icon_128.png application-audiocataloger

# To prevent any security issue with launcher
if [ "$(id -u)" = "0" ]; then
	chmod -R 777 ../AudioCataloger # 777 important since the user needs to edit the db and other files
else
	chmod -R u+rwx ../AudioCataloger
	chmod u+rwx ~/Desktop/maknoon-audiocataloger.desktop
fi

echo "Installation is completed. Please right click on Desktop file and select 'Allow Launching'"
exit 0
__ARCHIVE_BELOW__
