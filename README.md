# ESFX TaskFlow

Sistema interno de gestión de proyectos, tableros Kanban, calendario, notificaciones y control de horas (ERP operativo) desarrollado para **ESFX Technologies / EsForexVPS.com**.

## Estado actual
MVP funcional implementado con tema oscuro/claro inspirado en `esforexvps.com`.  
El backend está construido sobre Java 21 + Spring Boot 3.3, el frontend sobre React 18 + TypeScript + Vite y Bootstrap 5, la base de datos PostgreSQL 16 y Redis 7 corren en contenedores Docker.

## Stack Tecnológico

| Capa | Tecnología | Detalles |
|------|------------|----------|
| **Backend** | Java 21 + Spring Boot 3.3 | Monolito modular MVC con `JdbcClient` (consultas SQL explícitas), puerto `8085`. Cada módulo usa un esquema PostgreSQL propio y no comparte claves foráneas entre ellos. |
| **Frontend** | React 18 + TypeScript + Vite + Bootstrap 5 | Tema oscuro/claro con paleta basada en `esforexvps.com` (`#0F0F0F`, `#ADFF2F`, `#3D5AFE`). Puerto `5173`. |
| **Base de datos** | PostgreSQL 16 (Docker) | Múltiples migraciones versionadas por Flyway; `FLYWAY_DEV_LOCATION=classpath:db/dev` habilita la carga automática de datos de prueba. |
| **Colas / Caché** | Redis 7 (Docker) | Puertos `6380 → 6379`; cola de notificaciones y recordatorios. |
| **Seguridad** | Spring Security + JWT (JJWT) | Contraseñas cifradas con BCrypt, cambio obligatorio en primer inicio. |
| **Documentación** | OpenAPI 3 / Swagger UI | Especificación en `docs/api/openapi.yaml`, UI accesible desde `/swagger-ui.html`. |

## Estructura del Repositorio

```text
plan_practicas/
├── backend/                 # API REST Java 21 + Spring Boot (Maven)
│   ├── src/main/java/      # Código fuente de los módulos (no modificar aquí)
│   └── src/main/resources/
│       ├── db/migration/   # Migraciones Flyway
│       └── db/dev/         # Seed SQL de desarrollo (usuarios, proyectos, tareas...)
├── frontend/                # SPA React + TypeScript + Vite + Bootstrap 5
│   └── src/styles/theme.css   # Sistema de diseño EsForexVPS (Dark/Light)
├── docker/
│   ├── docker-compose.yml   # PostgreSQL y Redis (5433 / 6380) + perfil “app” para backend+frontend
│   ├── .env.example         # Plantilla de variables de entorno
│   └── seed-dev.sh          # Script manual para cargar datos de prueba
├── docs/
│   ├── api/openapi.yaml     # Especificación OpenAPI Fase 1
│   ├── database/V1__init.sql
│   └── diagramas/           # Diagramas ER y arquitectura modular (.png / .mmd)
├── PRODUCT.md               # Contexto de producto y objetivos
├── DESIGN.md                # Tokens y reglas visuales del sistema
└── README.md
```

## Arquitectura Modular (Preparada para Microservicios)

El paquete base `com.esfx.taskflow` se divide en:

| Módulo | Responsabilidad | Esquema PostgreSQL |
|--------|-----------------|---------------------|
| **common** | Seguridad JWT, manejo global de excepciones, auditoría y contratos entre módulos (`UserDirectory`, `ProjectsApi`, `CalendarApi`, etc.) | `public` |
| **access** | Gestión de usuarios, roles y permisos, log de auditoría | `access` |
| **projects** | Proyectos, miembros y tablero Kanban (TODO/IN_PROGRESS/DONE), comentarios y adjuntos | `projects` |
| **calendar** | Reuniones, invitaciones, vista unificada con fechas límite | `calendar` |
| **notifications** | Plantillas IN_APP / EMAIL, idempotencia, cola Redis | `notifications` |
| **timetracking** | Registro de horas por tarea y proyecto | `timetracking` |
| **reports** | Dashboard ejecutivo de solo lectura que agrega métricas entre módulos | `public` |

**Principios de desacoplamiento**

1. Cada módulo posee su propio esquema sin claves foráneas externas.
2. La comunicación entre módulos se realiza únicamente a través de interfaces definidas en `common` o mediante eventos de dominio.

## Roles y Permisos

| Permiso | Administrador | Gerente | Jefe de proyecto | Colaborador |
|---------|:------------:|:-------:|:----------------:|:-----------:|
| Gestionar usuarios y roles (`USER_MANAGE`) | ✔ | | | |
| Ver auditoría (`AUDIT_VIEW`) | ✔ | ✔ | | |
| Crear y editar proyectos (`PROJECT_CREATE`, `PROJECT_EDIT`) | ✔ | ✔ | ✔ | |
| Crear y asignar tareas (`TASK_CREATE`, `TASK_ASSIGN`) | ✔ | | ✔ | |
| Editar cualquier tarea (`TASK_UPDATE_ANY`) | ✔ | | ✔ | |
| Editar tareas propias (`TASK_UPDATE_OWN`) | ✔ | | ✔ | ✔ |
| Comentar y adjuntar archivos (`COMMENT_CREATE`) | ✔ | ✔ | ✔ | ✔ |
| Programar reuniones (`MEETING_CREATE`) | ✔ | ✔ | ✔ | |
| Responder invitaciones (`MEETING_RESPOND`) | ✔ | ✔ | ✔ | ✔ |
| Registrar horas (`TIME_LOG`) | ✔ | ✔ | ✔ | ✔ |
| Dashboard global (`REPORT_VIEW_ALL`) | ✔ | ✔ | | |
| Dashboard de proyectos propios (`REPORT_VIEW_OWN`) | ✔ | | ✔ | ✔ |

## Credenciales de Prueba (Entorno de Desarrollo)

Para habilitar la carga automática de datos de prueba, activa `FLYWAY_DEV_LOCATION=classpath:db/dev` en `docker/.env` **o ejecuta** `./seed-dev.sh`. Se crean 5 usuarios, 2 proyectos y demás datos de ejemplo.

| Correo | Rol | Contraseña |
|--------|-----|------------|
| `admin@esfx.local` | Administrador | `Esfx2026!` |
| `gerente@esfx.local` | Gerente | `Esfx2026!` |
| `jefe@esfx.local` | Jefe de proyecto | `Esfx2026!` |
| `ana@esfx.local` | Colaborador | `Esfx2026!` |
| `luis@esfx.local` | Colaborador | `Esfx2026!` |

> **Advertencia:** el archivo `.env` contiene contraseñas y no debe ser comprometido; está en `.gitignore`.

## Puesta en Marcha (Desarrollo Local)

### Requisitos previos

* Docker Desktop (o Docker Engine + Compose) – Windows, macOS o Linux.
* JDK 21 y Maven 3.9+ – para compilar el backend.
* Node.js 20+ – para instalar dependencias del frontend.

> En Windows puedes usar PowerShell o Git Bash; los comandos son los mismos que se muestran a continuación.

### 1. Levantar PostgreSQL, Redis y (opcional) backend

```bash
cd docker
cp .env.example .env            # crea tu archivo de configuración local
# Para cargar datos de prueba automáticamente al iniciar el backend,
# descomenta la siguiente línea en .env:
FLYWAY_DEV_LOCATION=classpath:db/dev
docker compose up -d
docker compose ps
```

> Si prefieres cargar los datos manualmente, ejecuta `./seed-dev.sh` dentro del directorio `docker`.

### 2. Iniciar el Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

* API base: `http://localhost:8085/api`
* Swagger UI: `http://localhost:8085/swagger-ui.html`

### 3. Iniciar el Frontend (React + Vite)

```bash
cd frontend
npm install          # instala dependencias Node
npm run dev          # inicia el servidor de desarrollo en http://localhost:5173
```

> El script Vite redirige automáticamente las peticiones `/api` al backend.

## Despliegue en Servidor (Ubuntu Server + Docker)

1. Copia `docker/.env.example` a `docker/.env`, configura contraseñas seguras (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` ≥ 32 caracteres, `APP_ADMIN_PASSWORD`) y deja **`FLYWAY_DEV_LOCATION=` vacío** (para no cargar datos de prueba en producción).

2. Construye y levanta el stack completo con el perfil `app`:

```bash
cd docker
docker compose --profile app up -d --build
```

3. Accede desde tu red local o VPN (Tailscale) a `http://<IP_SERVIDOR>:5173`.

---

> **Tip:** La carpeta `.gitignore` ya descarta `node_modules/`, `dist/`, `target/` y `docker/.env`. No añadas estas carpetas al control de versiones.