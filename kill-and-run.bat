@echo off
echo [1/3] Killing all java.exe processes...
taskkill /F /IM java.exe /T >NUL 2>&1
echo Done.

echo [2/3] Waiting 2 seconds...
timeout /t 2 /nobreak >NUL

echo [3/3] Starting WMS Bot...
call mvnw.cmd spring-boot:run -DskipTests
