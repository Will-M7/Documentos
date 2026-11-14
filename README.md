# Estudio Documental

Sistema para negocios tipo notaría: genera documentos (declaraciones juradas, cartas poder, contratos…) a partir de plantillas Word, completando los datos de las personas por DNI/RUC, con historial y registro de clientes.

> **Estado:** fase 0 (esqueleto). Funcionan el inicio de sesión y la base de datos. Los módulos de clientes, plantillas y documentos llegan en la fase 1. La versión anterior (escritorio JavaFX) está en la rama `v1`.

## Arquitectura en una línea

**Web de oficina (React)** → **API REST (Spring Boot)** → **PostgreSQL**, todo en una PC de la oficina y accesible desde las demás por el navegador. El mismo backend servirá después al portal de clientes y a la app móvil.

| Parte | Carpeta | Tecnología |
|---|---|---|
| Backend | [`backend/`](backend) | Java 21, Spring Boot 4, Spring Security (JWT), JPA, Flyway |
| Frontend | [`frontend/`](frontend) | React 19, TypeScript, Vite, MUI, TanStack Query |
| Base de datos | [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration) | PostgreSQL 17 (migraciones Flyway) |
| Instalación en servidor | [`infra/`](infra) | Docker Compose (opcional) |
| CI | [`.github/workflows/`](.github/workflows) | GitHub Actions |

## Empezar

Se desarrolla con PostgreSQL instalado en Windows, sin Docker. Los pasos completos (instalar PostgreSQL, crear la base, levantar backend y web) están en [docs/DESARROLLO.md](docs/DESARROLLO.md).

Resumen, con PostgreSQL ya instalado y la base creada:

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev
```

Abre http://localhost:5173 e ingresa con `admin` / `admin-local-123` (solo en desarrollo).

Para instalar en un servidor también hay una opción con Docker Compose en [`infra/`](infra) (opcional).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Decisiones, módulos, modelo de datos, seguridad, hoja de ruta |
| [docs/DESARROLLO.md](docs/DESARROLLO.md) | Requisitos, cómo levantar, comandos, cómo agregar módulos, flujo con Git |
| API en vivo | `http://localhost:8080/api/docs` con el backend levantado |
