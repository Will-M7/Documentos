@echo off
rem Arma la carpeta "instalacion" lista para copiar a la PC servidor de la oficina:
rem   app.jar, web\, iniciar.cmd, respaldo.cmd, restaurar.cmd, config.ejemplo.cmd
rem Ejecutar desde la raiz del repositorio:  scripts\windows\empaquetar.cmd
setlocal
cd /d "%~dp0..\.."
set SALIDA=%CD%\instalacion

echo Compilando la web...
pushd frontend
call npm ci --no-audit --no-fund || exit /b 1
call npm run build || exit /b 1
popd

echo Compilando el backend...
pushd backend
call mvnw.cmd -B -q -DskipTests package || exit /b 1
popd

if exist "%SALIDA%" rmdir /s /q "%SALIDA%"
mkdir "%SALIDA%"
for %%f in (backend\target\estudio-documental-api-*.jar) do copy /y "%%f" "%SALIDA%\app.jar" >nul
xcopy /e /i /q frontend\dist "%SALIDA%\web" >nul
copy /y scripts\windows\iniciar.cmd "%SALIDA%" >nul
copy /y scripts\windows\respaldo.cmd "%SALIDA%" >nul
copy /y scripts\windows\restaurar.cmd "%SALIDA%" >nul
copy /y scripts\windows\config.ejemplo.cmd "%SALIDA%" >nul

echo.
echo Listo: %SALIDA%
echo Copie esa carpeta a la PC servidor y siga docs\INSTALACION.md
