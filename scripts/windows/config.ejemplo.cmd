@echo off
rem ==========================================================================
rem  Configuracion de la instalacion. Copie este archivo como config.cmd
rem  (en la misma carpeta) y cambie TODOS los valores marcados.
rem  config.cmd tiene claves: no lo comparta ni lo suba a GitHub.
rem ==========================================================================

rem --- Base de datos (PostgreSQL instalado en esta PC) ---
set DB_URL=jdbc:postgresql://localhost:5432/estudio_documental
set DB_NOMBRE=estudio_documental
set DB_USUARIO=estudio
rem CAMBIAR:
set DB_CLAVE=cambie-esta-clave

rem --- Seguridad: claves largas al azar (minimo 32 caracteres cada una) ---
rem CAMBIAR. Para generarlas, en PowerShell:
rem   -join ((48..57)+(65..90)+(97..122) ^| Get-Random -Count 48 ^| %% {[char]$_})
set JWT_SECRETO=cambie-por-una-clave-larga-y-aleatoria-de-32-o-mas
rem Si se pierde esta, hay que volver a registrar los tokens de Factiliza/APIsPERU:
set APP_CLAVE_MAESTRA=cambie-por-otra-clave-larga-y-aleatoria-de-32-o-mas

rem --- Primer administrador (solo se crea si no hay usuarios) ---
set ADMIN_USUARIO=admin
rem CAMBIAR (minimo 10 caracteres). Cambiela desde la web despues del primer ingreso:
set ADMIN_CLAVE=cambie-esta-clave-inicial
set ADMIN_NOMBRE=Administrador

rem --- Carpetas (por defecto, junto a estos scripts) ---
set WEB_DIR=%~dp0web
set ARCHIVOS_DIR=%~dp0datos\archivos
set RESPALDOS_DIR=%~dp0respaldos
rem Dias que se guardan los respaldos antes de borrarse:
set RESPALDOS_DIAS=30

rem --- Red: las demas PCs entran por http://<nombre-de-esta-PC>:PUERTO ---
set PUERTO=8080

rem --- Programas ---
rem Carpeta "bin" de PostgreSQL (para respaldos):
set PG_BIN=C:\Program Files\PostgreSQL\16\bin
rem Java 21 (vacio = el que este en el PATH):
set JAVA_EXE=java
