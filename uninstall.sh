#!/bin/sh
cd "$(dirname "${0}")"

if [ "$(id -u)" != "0" ]; then
	# Check if the folder is created by root user by testing owner file inside it. you can use 'find AudioCataloger.jar -user root' as well
	if [ "$(ls -l | awk '{if ($8 == "AudioCataloger.jar" && $3 == "root") print $8}')" = "AudioCataloger.jar" ]; then
		echo "You cannot uninstall Maknoon Audio Cataloger since it is created by root user."
		exit 0
	fi
fi

xdg-desktop-menu uninstall maknoon-audiocataloger.desktop

if [ "$(id -u)" != "0" ]; then
	xdg-desktop-icon uninstall maknoon-audiocataloger.desktop
fi

xdg-icon-resource uninstall --context apps --size 16 maknoon-audiocataloger
xdg-icon-resource uninstall --context apps --size 32 maknoon-audiocataloger
xdg-icon-resource uninstall --context apps --size 64 maknoon-audiocataloger
xdg-icon-resource uninstall --context apps --size 128 maknoon-audiocataloger

cd ..; rm -rf AudioCataloger