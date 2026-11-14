# Guía de desarrollo

Cómo levantar el proyecto en tu PC y cómo trabajar en él día a día. Para el *qué* y el *por qué* de cada decisión, ver [ARQUITECTURA.md](ARQUITECTURA.md).

Se desarrolla **sin Docker**: PostgreSQL instalado en Windows, el backend con `mvnw` y la web con `npm`. Docker es opcional y solo sirve para probar la instalación completa (sección 5).

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21 | Backend |
| Node.js | 24 | Frontend |
| PostgreSQL | 16 o superior (servidor e instalación: 17) | Base de datos |
| pgAdmin 4 | viene con PostgreSQL | Ver y consultar la base de datos |
| Git | reciente | Control de versiones |
| LibreOffice | opcional | Generar PDF idénticos al Word. Sin él, los documentos salen solo en Word |

Maven no hace falta instalarlo: el backend trae `mvnw`.

## 2. Instalar PostgreSQL (una sola vez)

1. Descarga el instalador de **PostgreSQL para Windows** (16 o superior) desde https://www.postgresql.org/download/windows/ (enlace *Download the installer*, de EDB).
2. Ejecútalo con las opciones por defecto:
   - Componentes: deja marcados **PostgreSQL Server**, **pgAdmin 4** y **Command Line Tools**. *Stack Builder* no hace falta.
   - **Contraseña del superusuario `postgres`:** elige una y guárdala. Es la del administrador de PostgreSQL, no la del proyecto.
   - **Puerto:** `5432`.
   - *Locale:* el predeterminado.
3. Al final el instalador ofrece abrir **Stack Builder**: no hace falta, ciérralo con *Cancel*.
4. PostgreSQL queda como **servicio de Windows** (`postgresql-x64-16` o `-17`) y arranca solo con la PC.

### Crear el usuario y las bases del proyecto

1. Abre **pgAdmin 4** (menú Inicio) y escribe la contraseña de `postgres` cuando la pida.
2. En el panel izquierdo: *Servers* → *PostgreSQL 16* → clic derecho en *Databases* → *Query Tool* (o selecciona *postgres* y pulsa el ícono de consulta).
3. Ejecuta **una línea a la vez**: selecciónala con el mouse y presiona **F5**. Si ejecutas las tres juntas, pgAdmin muestra *CREATE DATABASE no puede ser ejecutado dentro de un bloque de transacción* y no crea nada.

```sql
CREATE ROLE estudio WITH LOGIN PASSWORD 'estudio_local';
CREATE DATABASE estudio_documental      OWNER estudio;   -- desarrollo: tus datos de prueba
CREATE DATABASE estudio_documental_test OWNER estudio;   -- pruebas automáticas: se vacía en cada ejecución
```

`estudio` / `estudio_local` son credenciales **solo de desarrollo**, las mismas que espera el perfil `local` del backend. En la instalación real de la oficina se usan otras.

### Ver la base con pgAdmin

En el panel izquierdo: *Servers* → *PostgreSQL 16* → *Databases* → **estudio_documental** → *Schemas* → *public* → **Tables** (clic derecho → *Refresh* si no aparecen).

- Ver los datos de una tabla: clic derecho → *View/Edit Data* → *All Rows*.
- Ver su estructura (columnas, tipos, restricciones): clic derecho → *Properties*, o despliega *Columns* y *Constraints*.
- Diagrama de todas las tablas: clic derecho en **estudio_documental** → *ERD For Database*.
- Escribir consultas: selecciona la base y abre *Tools* → *Query Tool*.

## 3. Primera vez con el código

```bash
git clone https://github.com/Will-M7/Documentos.git
cd Documentos/frontend
npm install
```

## 4. Levantar en modo desarrollo

Con dos terminales:

**1. Backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En Windows con `cmd`: `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`.

Al arrancar, el backend **crea o actualiza las tablas solo** (migraciones Flyway). Si la base está vacía, crea el usuario **admin** con la contraseña **admin-local-123**. Solo sirve en tu PC.

**2. Frontend** (http://localhost:5173):

```bash
cd frontend
npm run dev
```

Abre http://localhost:5173 e ingresa con `admin` / `admin-local-123`. Vite redirige las llamadas `/api` al backend.

**Documentación de la API:** http://localhost:8080/api/docs (botón *Authorize* → pegar el token del login).

**Archivos generados:** las plantillas y documentos se guardan en `backend/datos/archivos/` (ignorada por Git). Si vacía la base de desarrollo, borre también esa carpeta.

**Qué probar:** la lista paso a paso de cada pantalla está en [GUIA-PRUEBAS.md](GUIA-PRUEBAS.md). Hay una plantilla de ejemplo en `docs/ejemplos/carta-poder-simple.docx`.

## 5. (Opcional) Instalación completa con Docker

Para probar el sistema tal como se instalaría en un servidor, con Docker Desktop:

```bash
copy infra\.env.example infra\.env      # y cambiar las claves
cd infra
docker compose --env-file .env up -d --build
```

Abre http://localhost:8080. Para apagarlo: `docker compose down`. Este modo usa su propio PostgreSQL dentro de Docker, separado del que instalaste. Si tu PostgreSQL de Windows ya ocupa el puerto 5432, cambia esa línea en `docker-compose.yml` o detén el servicio mientras lo pruebas.

## 6. Comandos frecuentes

| Qué | Backend (`backend/`) | Frontend (`frontend/`) |
|---|---|---|
| Pruebas | `./mvnw verify` | `npm test` |
| Formatear código | `./mvnw spotless:apply` | `npm run format` |
| Revisar calidad | `./mvnw spotless:check` | `npm run lint` |
| Compilar | `./mvnw package` | `npm run build` |

Las pruebas del backend usan la base `estudio_documental_test`: la vacían y aplican todas las migraciones desde cero en cada ejecución. Nunca tocan tu base de desarrollo.

## 7. Cómo agregar algo nuevo

### Un cambio en la base de datos

Las tablas **no se modifican a mano** desde pgAdmin: esas herramientas son para ver datos y hacer consultas. La estructura se cambia con migraciones:

1. Crea `backend/src/main/resources/db/migration/V00N__descripcion.sql` con el número siguiente.
2. Reinicia el backend: aplica la migración solo y la anota en la tabla `flyway_schema_history`.
3. **Nunca** modifiques una migración que ya está en `main`: crea una nueva.
4. Hibernate solo valida (`ddl-auto: validate`): si la entidad Java no coincide con la tabla, el backend no arranca y avisa qué columna falta.

Así tu base, la de Willian y la de la oficina siempre tienen la misma estructura.

### Un módulo o funcionalidad en el backend

Usa `identidad/` como ejemplo. Dentro del paquete del módulo:

```
clientes/
├── api/              ClienteController + dto/ (records de entrada y salida)
├── aplicacion/       ClienteService (casos de uso, @Transactional)
├── dominio/          Cliente (entidad) y reglas de negocio
└── infraestructura/  ClienteRepository, integraciones externas
```

Reglas clave (detalle en ARQUITECTURA.md, sección 6.2):

1. El controlador no tiene lógica; las entidades nunca salen por la API.
2. Otro módulo solo usa tu servicio, nunca tu repositorio.
3. Errores: lanza `RecursoNoEncontradoException` (404), `ReglaNegocioException` (422) o deja que la validación responda 400. El formato lo arma `ManejadorErrores`.

### Una pantalla en el frontend

1. Crea `src/features/<modulo>/pages/MiPantallaPage.tsx`.
2. Las llamadas al backend van en `src/features/<modulo>/api.ts`, usando `api()` de `src/api/cliente.ts`.
3. Agrega la ruta en `src/app/App.tsx` y, si va en el menú, en `src/app/LayoutPrincipal.tsx`.

## 8. Flujo de trabajo con Git

```bash
git switch main
git pull
git switch -c feat/clientes-busqueda      # una rama por cambio
# ... trabajar, probar ...
git add .
git commit -m "feat(clientes): buscar por número de documento"
git push -u origin feat/clientes-busqueda
```

Luego abre un pull request en GitHub. La CI tiene que pasar en verde y la otra persona del equipo lo revisa antes de integrarlo.

Prefijos de commit: `feat` (funcionalidad), `fix` (corrección), `docs`, `refactor`, `test`, `chore` (mantenimiento).

## 9. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El backend no arranca: `Connection refused` | El servicio de PostgreSQL está detenido | Windows → *Servicios* → `postgresql-x64-16` (o `-17`) → Iniciar |
| `password authentication failed for user "estudio"` | No se creó el usuario o la clave no coincide | Ejecuta el SQL de la sección 2 |
| `database "estudio_documental" does not exist` | Falta crear la base | Ejecuta el SQL de la sección 2 |
| `Schema-validation: missing column` | La entidad no coincide con las migraciones | Agrega una migración nueva con el cambio |
| El login responde 401 con la clave correcta | Ya existía un usuario y el admin inicial no se volvió a crear | Usa ese usuario, o vacía la base de desarrollo |
| `Port 8080 was already in use` | Otro programa usa el puerto | Ciérralo, o arranca con `-Dspring-boot.run.arguments=--server.port=8081` y cambia el puerto en `frontend/vite.config.ts` |
