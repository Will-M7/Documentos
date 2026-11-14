@echo off
rem Inicia Estudio Documental (web + API en un solo programa).
rem Dejar esta ventana abierta mientras se use el sistema, o programarlo al encender la PC
rem (ver docs/INSTALACION.md).
setlocal
cd /d "%~dp0"

if not exist "%~dp0config.cmd" (
  echo Falta config.cmd. Copie config.ejemplo.cmd como config.cmd y complete los valores.
  pause
  exit /b 1
)
call "%~dp0config.cmd"

if not exist "%ARCHIVOS_DIR%" mkdir "%ARCHIVOS_DIR%"
if not exist "%~dp0logs" mkdir "%~dp0logs"

echo Iniciando Estudio Documental en http://localhost:%PUERTO% ...
"%JAVA_EXE%" -Xmx1g -jar "%~dp0app.jar" >> "%~dp0logs\estudio-documental.log" 2>&1
echo El sistema se detuvo. Revise "%~dp0logs\estudio-documental.log"
pause
