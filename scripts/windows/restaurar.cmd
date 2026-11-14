@echo off
rem Restaura un respaldo hecho con respaldo.cmd.
rem Uso:  restaurar.cmd respaldos\20261114_2300
rem ATENCION: reemplaza TODOS los datos actuales por los del respaldo.
rem Detenga el sistema (cierre la ventana de iniciar.cmd) antes de restaurar.
setlocal
cd /d "%~dp0"
call "%~dp0config.cmd"

if "%~1"=="" (
  echo Indique la carpeta del respaldo. Ejemplo: restaurar.cmd respaldos\20261114_2300
  exit /b 1
)
if not exist "%~1\base.dump" (
  echo No se encontro %~1\base.dump
  exit /b 1
)

echo Se reemplazaran TODOS los datos actuales por los del respaldo %~1
set /p CONFIRMA=Escriba SI para continuar:
if /i not "%CONFIRMA%"=="SI" (
  echo Cancelado.
  exit /b 1
)

set PGPASSWORD=%DB_CLAVE%
"%PG_BIN%\pg_restore.exe" -h localhost -U %DB_USUARIO% -d %DB_NOMBRE% --clean --if-exists --no-owner "%~1\base.dump"
set ERROR_RESTAURAR=%ERRORLEVEL%
set PGPASSWORD=
if not "%ERROR_RESTAURAR%"=="0" (
  echo ERROR al restaurar la base de datos.
  exit /b 1
)

robocopy "%~1\archivos" "%ARCHIVOS_DIR%" /MIR /NFL /NDL /NJH /NJS /NP >nul
echo Restauracion terminada. Inicie el sistema con iniciar.cmd
exit /b 0
