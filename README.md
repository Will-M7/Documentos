# Estudio Documental

Sistema para negocios tipo notaría: genera documentos (declaraciones juradas, cartas poder, contratos…) a partir de plantillas Word, completando los datos de las personas por DNI/RUC, con historial y registro de clientes.

> **Estado:** fase 0 (esqueleto). Funcionan el inicio de sesión, la base de datos y la instalación con Docker. Los módulos de clientes, plantillas y documentos llegan en la fase 1. La versión anterior (escritorio JavaFX) está en la rama `v1`.

## Arquitectura en una línea

**Web de oficina (React)** → **API REST (Spring Boot)** → **PostgreSQL**, todo en una PC de la oficina y accesible desde las demás por el navegador. El mismo backend servirá después al portal de clientes y a la app móvil.

| Parte | Carpeta | Tecnología |
|---|---|---|
| Backend | [`backend/`](backend) | Java 21, Spring Boot 4, Spring Security (JWT), JPA, Flyway |
| Frontend | [`frontend/`](frontend) | React 19, TypeScript, Vite, MUI, TanStack Query |
| Base de datos | [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration) | PostgreSQL 17 (migraciones Flyway) |
| Instalación | [`infra/`](infra) | Docker Compose |
| CI | [`.github/workflows/`](.github/workflows) | GitHub Actions |

## Empezar

Con Docker Desktop abierto:

```bash
cp infra/.env.example infra/.env     # y cambiar las claves
cd infra
docker compose --env-file .env up -d --build
```

Abre http://localhost:8080 e ingresa con `ADMIN_USUARIO` / `ADMIN_CLAVE` de tu `.env`.

Para programar (backend y frontend con recarga automática), sigue [docs/DESARROLLO.md](docs/DESARROLLO.md).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Decisiones, módulos, modelo de datos, seguridad, hoja de ruta |
| [docs/DESARROLLO.md](docs/DESARROLLO.md) | Requisitos, cómo levantar, comandos, cómo agregar módulos, flujo con Git |
| API en vivo | `http://localhost:8080/api/docs` con el backend levantado |
