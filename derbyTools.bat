cd /D %~dp0
REM ij> connect 'jdbc:derby:resources/db';
REM ij> exit;
java -cp resources/lib/* -Dij.protocol=jdbc:derby: org.apache.derby.tools.ij
pause