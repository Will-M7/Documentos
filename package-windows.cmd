@echo off
setlocal
cd /d "%~dp0"

where jpackage >nul 2>nul
if errorlevel 1 (
  echo Se requiere JDK 21 con jpackage en PATH.
  exit /b 1
)

if exist target\package-input rmdir /S /Q target\package-input
call mvnw.cmd -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\package-input
if errorlevel 1 exit /b 1

copy /Y target\estudio-documental-1.0.0.jar.original target\package-input\studio.jar >nul
if errorlevel 1 exit /b 1

if not exist target\installer mkdir target\installer
jpackage --type exe --name EstudioDocumental --app-version 1.0.0 --input target\package-input --dest target\installer --main-jar studio.jar --main-class com.ejemplo.crudpersona.Launcher --win-menu --win-shortcut
if errorlevel 1 exit /b 1

echo Instalador creado en target\installer
