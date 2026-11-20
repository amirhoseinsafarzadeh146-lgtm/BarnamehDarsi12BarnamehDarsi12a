@echo off
setlocal
set GRADLE_VERSION=8.9
set DIST_NAME=gradle-%GRADLE_VERSION%-bin
set BASE=%USERPROFILE%\.gradle\manual-wrapper
set INSTALL_DIR=%BASE%\%DIST_NAME%
set GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
set ZIP=%BASE%\%DIST_NAME%.zip
if not exist "%GRADLE_BIN%" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/%DIST_NAME%.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%INSTALL_DIR%' -Force"
)
call "%GRADLE_BIN%" %*
endlocal
