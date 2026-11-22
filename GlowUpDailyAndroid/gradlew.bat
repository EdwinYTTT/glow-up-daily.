@echo off
setlocal
set "VERSION=8.9"
set "BASE=%USERPROFILE%\.gradle\manual-wrapper"
set "DIST=%BASE%\gradle-%VERSION%"
set "ZIP=%BASE%\gradle-%VERSION%-bin.zip"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  echo Descargando Gradle %VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%BASE%' -Force"
  if errorlevel 1 exit /b 1
)
call "%DIST%\bin\gradle.bat" %*
endlocal
