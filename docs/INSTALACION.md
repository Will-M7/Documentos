# Instalación en la oficina

Cómo dejar Estudio Documental funcionando en la PC servidor de la oficina, **sin Docker** y sin costo de licencias. Las demás PCs entran desde el navegador.

Hay una alternativa con Docker en `infra/` (ver [DESARROLLO.md](DESARROLLO.md), sección 5); esta guía usa la instalación directa en Windows.

## 1. Qué se necesita

**PC servidor** (una de la oficina, encendida en horario de atención):

| Requisito | Recomendado |
|---|---|
| Sistema | Windows 10 u 11 |
| Memoria | 8 GB de RAM |
| Disco | SSD, con 10 GB libres como mínimo |
| Red | Cable, IP fija o nombre de PC conocido |

**Programas** (todos gratuitos):

| Programa | Para qué | Dónde |
|---|---|---|
| PostgreSQL 16 o 17 | Base de datos | postgresql.org/download/windows |
| Java 21 (Temurin JRE) | Ejecutar el sistema | adoptium.net |
| LibreOffice | PDF idénticos al Word (opcional) | libreoffice.org |

**Costo:** S/ 0 mientras corra en una PC de la oficina. Solo habría costo de servidor (unos US$ 5–12 al mes) si más adelante se publica en internet para el portal de clientes.

## 2. Preparar la base de datos

1. Instale PostgreSQL con las opciones por defecto (puerto 5432) y guarde la contraseña del usuario `postgres`.
2. Abra **pgAdmin 4** → *Servers* → *PostgreSQL* → *Databases* → *Query Tool*.
3. Ejecute **una línea a la vez** (seleccionar la línea y F5), cambiando la contraseña por una propia y segura:

```sql
CREATE ROLE estudio WITH LOGIN PASSWORD 'una-clave-segura-de-la-oficina';
CREATE DATABASE estudio_documental OWNER estudio;
```

Las tablas las crea el sistema al arrancar por primera vez.

## 3. Armar el paquete (en la PC de desarrollo)

Desde la raíz del repositorio:

```bash
scripts\windows\empaquetar.cmd
```

Se crea la carpeta `instalacion\` con:

```
instalacion\
├── app.jar              El sistema (API + web)
├── web\                 Pantallas
├── iniciar.cmd          Inicia el sistema
├── respaldo.cmd         Respaldo de base y archivos
├── restaurar.cmd        Restaurar un respaldo
└── config.ejemplo.cmd   Configuración de ejemplo
```

Copie esa carpeta a la PC servidor, por ejemplo a `C:\EstudioDocumental`.

## 4. Configurar

1. En `C:\EstudioDocumental`, copie `config.ejemplo.cmd` como `config.cmd`.
2. Ábralo con el Bloc de notas y cambie los valores marcados con `CAMBIAR`:
   - `DB_CLAVE`: la contraseña del paso 2.
   - `JWT_SECRETO` y `APP_CLAVE_MAESTRA`: dos claves distintas de al menos 32 caracteres al azar. Para generarlas, en PowerShell:

     ```powershell
     -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 48 | % {[char]$_})
     ```

   - `ADMIN_CLAVE`: contraseña inicial del administrador (al menos 10 caracteres).
   - `PG_BIN`: la carpeta `bin` de PostgreSQL si no es la versión 16.
3. **Guarde una copia de `config.cmd` en un lugar seguro**, fuera de la PC. Si se pierde `APP_CLAVE_MAESTRA`, hay que volver a registrar los tokens de Factiliza/APIsPERU.

## 5. Primer arranque

1. Doble clic en `iniciar.cmd`. La ventana queda abierta mientras el sistema funcione.
2. En el navegador de esa PC: http://localhost:8080
3. Ingrese con `admin` y la `ADMIN_CLAVE` del paso 4.
4. Cambie la contraseña (ícono 🔑 arriba a la derecha).
5. Cree los usuarios del personal en **Usuarios**, suba las plantillas en **Plantillas** y, si tiene, registre el token de Factiliza o APIsPERU en **Configuración**.

Si algo falla, revise `C:\EstudioDocumental\logs\estudio-documental.log`.

## 6. Acceso desde las demás PCs

1. Averigüe el nombre de la PC servidor: *Configuración → Sistema → Acerca de → Nombre del dispositivo* (por ejemplo, `RECEPCION`).
2. Permita el puerto en el firewall. En la PC servidor, abra PowerShell **como administrador** y ejecute:

   ```powershell
   New-NetFirewallRule -DisplayName "Estudio Documental" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```

3. En las otras PCs, abra http://RECEPCION:8080 (o la IP del servidor, por ejemplo http://192.168.1.10:8080) y guárdelo en favoritos.

Conviene que el router le asigne siempre la misma IP a la PC servidor (reserva DHCP).

## 7. Que arranque solo al encender la PC

Con el **Programador de tareas** de Windows:

1. *Crear tarea…* → Nombre: `Estudio Documental`.
2. *General*: marque **Ejecutar tanto si el usuario inició sesión como si no** y **Ejecutar con los privilegios más altos**.
3. *Desencadenadores* → Nuevo → **Al iniciar el sistema**.
4. *Acciones* → Nuevo → Programa: `C:\EstudioDocumental\iniciar.cmd`, Iniciar en: `C:\EstudioDocumental`.
5. *Configuración*: desmarque **Detener la tarea si se ejecuta durante más de…**.

Reinicie la PC y compruebe que http://localhost:8080 responde sin abrir nada a mano.

## 8. Respaldos

`respaldo.cmd` guarda la base de datos y los archivos (Word y PDF) en `respaldos\AAAAMMDD_HHMM\` y borra los de más de 30 días.

1. Pruébelo una vez con doble clic: debe terminar con `Respaldo listo`.
2. Prográmelo a diario en el **Programador de tareas**: desencadenador *Diariamente* a las 21:00; acción `C:\EstudioDocumental\respaldo.cmd`.
3. **Copie la carpeta `respaldos` fuera de la PC** al menos una vez por semana: disco externo, Google Drive o OneDrive. Un respaldo en la misma PC no sirve si el disco se daña.
4. Una vez al mes, pruebe restaurar en otra PC (ver abajo).
5. En la web, **Configuración → Respaldos** muestra los últimos respaldos y si están completos, y tiene el botón **Respaldar ahora**. Si pasan más de 2 días sin un respaldo completo, el administrador ve un aviso en **Inicio**.

### Restaurar

1. Cierre la ventana de `iniciar.cmd` (o detenga la tarea programada).
2. En `C:\EstudioDocumental`, desde una consola: `restaurar.cmd respaldos\20261114_2100` y escriba `SI`.
3. Inicie de nuevo con `iniciar.cmd`.

## 9. Traer los datos de la versión anterior (v1)

Se hace una sola vez, al cambiar de la aplicación de escritorio a esta.

1. En la aplicación de escritorio, **Configuración**: use **Exportar Clientes** y **Exportar Plantillas** (alcance *Todas*). Puede exportar ambas en un solo ZIP o en dos.
2. En la web, como administrador: **Configuración → Traer datos de la versión anterior** → **Elegir ZIP** → **Importar**.
3. Lea el resumen: cuántos clientes y plantillas entraron y cuáles no (por ejemplo, un DNI con menos de 8 dígitos). Puede corregir el dato en la v1, volver a exportar e importar de nuevo: lo que ya entró no se duplica.
4. Revise cada plantilla importada en **Plantillas**: el Word y las variables (etiqueta y tipo) se traen, pero los participantes hay que configurarlos en la v2 (marcas como `{{otorgante.nombre_completo}}`).

## 10. Portal de clientes

Los clientes pueden pedir documentos en `http://NOMBRE-PC:8080/portal` (solo se ofrecen las plantillas marcadas **En el portal**). Así como está, el portal funciona **dentro de la red de la oficina** (por ejemplo, en una PC o tablet de recepción). Para abrirlo a internet hace falta un dominio con HTTPS y un servidor publicado; no exponga el puerto 8080 directamente a internet.

Para evitar abusos, desde una misma conexión se aceptan 5 solicitudes cada 10 minutos (los envíos con errores no cuentan). Si los clientes piden desde una PC o tablet de recepción compartida, súbalo en `config.cmd` con `set PORTAL_SOLICITUDES_POR_10_MIN=30`.

## 11. Actualizar a una versión nueva

1. Haga un respaldo (`respaldo.cmd`).
2. En la PC de desarrollo: `git pull` y `scripts\windows\empaquetar.cmd`.
3. Detenga el sistema en el servidor.
4. Reemplace `app.jar` y la carpeta `web\` por las nuevas. **No toque `config.cmd`, `datos\` ni `respaldos\`**.
5. Inicie el sistema. Los cambios de la base se aplican solos al arrancar (migraciones).

## 12. Lista de verificación

- [ ] http://localhost:8080 abre en el servidor y http://NOMBRE-PC:8080 en otra PC
- [ ] Se cambió la contraseña inicial del administrador
- [ ] `config.cmd` está guardado en un lugar seguro
- [ ] El sistema arranca solo al reiniciar la PC
- [ ] El respaldo diario está programado y se copia fuera de la PC
- [ ] **Configuración → Respaldos** muestra el último respaldo como completo
- [ ] (Si venían de la v1) Clientes y plantillas importados y plantillas revisadas
- [ ] (Opcional) LibreOffice instalado: **Configuración** muestra “se generan PDF”
