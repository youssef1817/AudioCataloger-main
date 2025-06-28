#!/bin/sh

cd "$(dirname "${0}")" || exit 1

../PlugIns/jdk/bin/java \
	-Dapple.laf.useScreenMenuBar=true \
	-Dfile.encoding=UTF-8 \
	-Xms512m \
	-Xmx1024m \
	-Dprism.lcdtext=false \
	--module-path resources/mods \
	--add-modules javafx.controls,javafx.graphics,javafx.media,jdk.incubator.vector \
	-Xdock:name="المفهرس" \
	-Xdock:icon=../Resources/icon.icns \
	-jar AudioCataloger.jar
