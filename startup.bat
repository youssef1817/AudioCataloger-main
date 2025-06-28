cd /D %~dp0
jdk\bin\java -Dfile.encoding=UTF-8 -Xms512m -Xmx1024m -Dprism.lcdtext=false --module-path resources\mods --add-modules javafx.controls,javafx.graphics,javafx.media,jdk.incubator.vector -jar AudioCataloger.jar
pause