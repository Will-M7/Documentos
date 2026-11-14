# Guía de desarrollo

Cómo levantar el proyecto en tu PC y cómo trabajar en él día a día. Para el *qué* y el *por qué* de cada decisión, ver [ARQUITECTURA.md](ARQUITECTURA.md).

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21 | Backend |
| Node.js | 24 | Frontend |
| Docker Desktop | reciente | PostgreSQL local y pruebas de integración |
| Git | reciente | Control de versiones |

Maven no hace falta instalarlo: el backend trae `mvnw`.

Editor sugerido: IntelliJ IDEA (backend) y VS Code (frontend).

## 2. Primera vez

```bash
git clone https://github.com/Will-M7/Documentos.git
cd Documentos
cp infra/.env.example infra/.env        # en Windows: copy infra\.env.example infra\.env
```

Para desarrollo, `infra/.env` puede quedar con los valores del ejemplo, pero en `DB_CLAVE` pon `estudio_local`, que es la que espera el perfil `local` del backend.

```bash
cd frontend
npm install
```

## 3. Levantar en modo desarrollo

Se usan tres terminales:

**1. Base de datos** (Docker):

```bash
cd infra
docker compose --env-file .env up -d db
```

**2. Backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En Windows con `cmd`: `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`.

El perfil `local` usa valores de prueba (`application-local.yml`) y, si la base está vacía, crea el usuario **admin** con la contraseña **admin-local-123**. Solo sirve en tu PC.

**3. Frontend** (http://localhost:5173):

```bash
cd frontend
npm run dev
```

Abre http://localhost:5173 e ingresa con `admin` / `admin-local-123`. Vite redirige las llamadas `/api` al backend.

**Documentación de la API:** http://localhost:8080/api/docs (botón *Authorize* → pegar el token del login).

## 4. Probar el sistema completo como en la oficina

```bash
cd infra
docker compose --env-file .env up -d --build
```

Abre http://localhost:8080. Para apagarlo: `docker compose down` (agrega `-v` solo si quieres **borrar** la base de datos).

## 5. Comandos frecuentes

| Qué | Backend (`backend/`) | Frontend (`frontend/`) |
|---|---|---|
| Pruebas | `./mvnw verify` | `npm test` |
| Formatear código | `./mvnw spotless:apply` | `npm run format` |
| Revisar calidad | `./mvnw spotless:check` | `npm run lint` |
| Compilar | `./mvnw package` | `npm run build` |

Las pruebas del backend levantan un PostgreSQL real con Docker (Testcontainers): Docker Desktop debe estar abierto.

## 6. Cómo agregar algo nuevo

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

### Un cambio en la base de datos

1. Crea `backend/src/main/resources/db/migration/V00N__descripcion.sql` con el número siguiente.
2. **Nunca** modifiques una migración que ya está en `main`: crea una nueva.
3. Hibernate solo valida (`ddl-auto: validate`): si la entidad no coincide con la tabla, el backend no arranca. Así se detectan los errores antes de llegar a la oficina.

### Una pantalla en el frontend

1. Crea `src/features/<modulo>/pages/MiPantallaPage.tsx`.
2. Las llamadas al backend van en `src/features/<modulo>/api.ts`, usando `api()` de `src/api/cliente.ts`.
3. Agrega la ruta en `src/app/App.tsx` y, si va en el menú, en `src/app/LayoutPrincipal.tsx`.

## 7. Flujo de trabajo con Git

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

## 8. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El backend no arranca: `Connection refused` | La base no está levantada | `docker compose --env-file .env up -d db` en `infra/` |
| El backend no arranca: `password authentication failed` | La clave de `infra/.env` no coincide con la del perfil `local` | Usa `DB_CLAVE=estudio_local`, o borra la base con `docker compose down -v` y vuelve a levantarla |
| Las pruebas del backend fallan con `Could not find a valid Docker environment` | Docker Desktop está cerrado | Ábrelo y repite |
| `Schema-validation: missing column` | La entidad no coincide con las migraciones | Agrega una migración nueva con el cambio |
| El login responde 401 con la clave correcta | Ya existía un usuario y el admin inicial no se volvió a crear | Usa ese usuario, o borra la base de desarrollo (`down -v`) |
