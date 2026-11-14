@echo off
rem Respaldo completo: base de datos (pg_dump) + carpeta de archivos (Word y PDF).
rem Programarlo una vez al dia con el Programador de tareas (ver docs/INSTALACION.md).
rem Copie la carpeta de respaldos a un disco externo o a la nube con regularidad.
setlocal
cd /d "%~dp0"
call "%~dp0config.cmd"

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set FECHA=%%i
set DESTINO=%RESPALDOS_DIR%\%FECHA%
mkdir "%DESTINO%"

echo [%FECHA%] Respaldando base de datos...
set PGPASSWORD=%DB_CLAVE%
"%PG_BIN%\pg_dump.exe" -h localhost -U %DB_USUARIO% -d %DB_NOMBRE% -F c -f "%DESTINO%\base.dump"
set ERROR_DUMP=%ERRORLEVEL%
set PGPASSWORD=
if not "%ERROR_DUMP%"=="0" (
  echo ERROR: no se pudo respaldar la base de datos.
  exit /b 1
)

echo Respaldando archivos...
robocopy "%ARCHIVOS_DIR%" "%DESTINO%\archivos" /E /NFL /NDL /NJH /NJS /NP >nul
if %ERRORLEVEL% GEQ 8 (
  echo ERROR: no se pudieron copiar los archivos.
  exit /b 1
)

echo Borrando respaldos de mas de %RESPALDOS_DIAS% dias...
powershell -NoProfile -Command "Get-ChildItem -LiteralPath '%RESPALDOS_DIR%' -Directory | Where-Object { $_.CreationTime -lt (Get-Date).AddDays(-%RESPALDOS_DIAS%) } | Remove-Item -Recurse -Force"

echo Respaldo listo: %DESTINO%
exit /b 0
