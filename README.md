# ESFX TaskFlow

Sistema de gestión de tareas (tipo ERP interno) de ESFX Technologies. Es un proyecto **independiente del bot de trading**: tiene su propio repositorio, base de datos y despliegue.

> Estado: **planificación (semana 7, diseño técnico)**. Todavía no hay código; esta carpeta contiene el diseño que se construirá en las semanas 8 a 10.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java con Spring Boot, arquitectura MVC (los controladores REST hacen de vista), monolito modular |
| Frontend | React + TypeScript + Bootstrap (Vite), estilo inspirado en esforexvps.com |
| Base de datos | PostgreSQL 16 en Docker, puerto **5433** (no el 5432) |
| Colas | Redis 7 en Docker, puerto **6380** |
| Seguridad | JWT; contraseñas con BCrypt, nunca se devuelven ni se escriben en logs |
| Correo | Gmail detrás de una interfaz `EmailProvider`; Calendar y Contactos en la fase 3 |
| Documentación | OpenAPI (`docs/api/openapi.yaml`) |

## Estructura de carpetas

```
esfx-taskflow/
├── backend/                 proyecto Java 21 + Spring Boot (Maven)
├── frontend/                proyecto React + TypeScript (Vite)
├── docker/
│   ├── docker-compose.yml   PostgreSQL (5433), Redis (6380) y, con el perfil "app", backend + frontend
│   └── .env.example         copiar como .env
├── docs/
│   ├── api/openapi.yaml     contrato de la API (Fase 1)
│   ├── database/V1__init.sql  migración inicial (Flyway)
│   └── diagramas/
│       ├── er-base-de-datos.png / .mmd
│       └── arquitectura-modulos.png / .mmd
├── .gitignore
└── README.md
```

Los dos proyectos (`backend` y `frontend`) viven en la misma carpeta, cada uno con su propio `package.json` o `pom.xml`.

## Cómo levantar la base de datos

```bash
cd docker
cp .env.example .env        # cambia las contraseñas
docker compose up -d
docker compose ps
```

Flyway aplica solas las migraciones de `backend/src/main/resources/db/migration/` (V1 = esquema, V2 = plantilla de recuperación de contraseña) al arrancar el backend. El usuario administrador lo crea la aplicación con `APP_ADMIN_EMAIL` y `APP_ADMIN_PASSWORD`; no se guarda ninguna contraseña en el repositorio.

## Reglas de arquitectura del backend

Paquete base propuesto: `com.esfx.taskflow`. Un paquete por módulo:

```
com.esfx.taskflow
├── common/            seguridad JWT, manejo de errores, auditoría, utilidades
├── access/            usuarios, roles, permisos, auditoría
├── projects/          proyectos, tareas, Kanban, comentarios, adjuntos
├── calendar/          reuniones e invitados
├── notifications/     plantillas, cola, envío (EmailProvider)
├── reports/           dashboard (solo consulta)
└── timetracking/      horas trabajadas
```

Dentro de cada módulo: `controller` (la vista: recibe y devuelve JSON), `service` (reglas de negocio), `repository`, `model` (entidades) y `dto`.

Reglas para poder pasar a microservicios si el sistema crece:

1. Un módulo **nunca** usa el repositorio ni las entidades de otro; solo llama a su `service` mediante una interfaz.
2. Un esquema de PostgreSQL por módulo y **sin claves foráneas entre esquemas**; las referencias (`user_id`, `task_id`) las valida el servicio.
3. Las reacciones entre módulos se hacen con eventos (por ejemplo, "tarea asignada" → Notificaciones). Hoy son eventos internos de Spring; mañana pueden pasar por Redis.
4. `reports` no tiene tablas: consulta a los demás módulos a través de sus servicios.
5. Las integraciones externas (Gmail, Google Calendar) van detrás de una interfaz con un adaptador por proveedor.
6. Primer módulo candidato a separarse: `notifications`.

## Notificaciones (flujo)

1. Un módulo publica un evento (tarea asignada, tarea por vencer, invitación a reunión).
2. `notifications` crea el registro `PENDING` con su plantilla (canal `IN_APP` y/o `EMAIL`) y una `idempotency_key` para no duplicar avisos.
3. Una cola en Redis entrega el registro al envío; el correo sale por Gmail.
4. El registro pasa a `SENT` o `FAILED`; los fallos se reintentan y se cuentan en `attempts`.

## Roles y permisos (propuesta, a confirmar con el supervisor)

| Permiso | Administrador | Gerente | Jefe de proyecto | Colaborador |
|---|:-:|:-:|:-:|:-:|
| Gestionar usuarios y roles (USER_MANAGE) | ✔ | | | |
| Ver auditoría (AUDIT_VIEW) | ✔ | ✔ | | |
| Crear proyectos (PROJECT_CREATE) | ✔ | ✔ | ✔ | |
| Editar proyectos y miembros (PROJECT_EDIT) | ✔ | ✔ | ✔ | |
| Crear tareas (TASK_CREATE) | ✔ | | ✔ | |
| Asignar tareas (TASK_ASSIGN) | ✔ | | ✔ | |
| Editar cualquier tarea (TASK_UPDATE_ANY) | ✔ | | ✔ | |
| Editar tareas propias (TASK_UPDATE_OWN) | ✔ | | ✔ | ✔ |
| Comentar y adjuntar (COMMENT_CREATE) | ✔ | ✔ | ✔ | ✔ |
| Programar reuniones (MEETING_CREATE) | ✔ | ✔ | ✔ | |
| Responder invitaciones (MEETING_RESPOND) | ✔ | ✔ | ✔ | ✔ |
| Registrar horas (TIME_LOG) | ✔ | ✔ | ✔ | ✔ |
| Dashboard de todos los proyectos (REPORT_VIEW_ALL) | ✔ | ✔ | | |
| Dashboard de proyectos propios (REPORT_VIEW_OWN) | ✔ | | ✔ | ✔ |

## Orden de construcción

| Semana | Trabajo |
|---|---|
| 7 | Diseño técnico: este material, wireframes y guía de estilo |
| 8 | Backend: `access` → `projects` → `calendar` → `notifications` → `reports` → `timetracking` |
| 9 | Frontend: mismas pantallas, en el mismo orden |
| 10 | Integración y pruebas del MVP |
| 11–12 | Flujo Git y CI/CD (Jenkins + Docker) |

Fase 2: pizarra de ideas, Planning Poker y costos. Fase 3: sincronización con Google Calendar y Contactos.

## Estilo visual (aproximado, tomado de esforexvps.com)

Negro (~#0F0F0F) para menú y cabecera, verde neón (~#ADFF2F) para acciones destacadas, azul (~#3D5AFE) para acciones primarias, fondo claro azulado (~#F5F7FF) y tipografía tipo Inter. Se aplica sobrescribiendo las variables de Bootstrap.


## Cómo ejecutarlo (desarrollo)

Necesitas: JDK 21, Node 20+, Docker. Editores: IntelliJ IDEA Community (backend) y VS Code (frontend).

1. **Base de datos y Redis**: `cd docker && cp .env.example .env` (cambia las claves) y `docker compose up -d`.
2. **Backend** (IntelliJ: *File > Open* sobre `backend/`; IntelliJ detecta Maven y descarga las dependencias):
   - Crea una configuración de ejecución para `TaskflowApplication` y define las variables de entorno (ver `backend/README.md`), o ejecuta en terminal con las variables ya exportadas: `mvn spring-boot:run`.
   - Swagger: http://localhost:8085/swagger-ui.html
3. **Frontend** (VS Code: abrir la carpeta `frontend/`): `npm install` y `npm start` → http://localhost:5173. Vite redirige `/api` al backend.
4. Entra con `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`; la primera vez pedirá cambiar la contraseña.

Todo en contenedores: `cd docker && docker compose --profile app up -d --build` (frontend en http://localhost:5173).

## Decisiones de implementación

- El backend usa **Spring JDBC (`JdbcClient`) con SQL explícito** en lugar de JPA: más fácil de leer, de depurar contra el esquema y de separar por módulos.
- El módulo `reports` es de solo lectura y es el único que consulta tablas de otro esquema (`projects`), porque un reporte agrega datos.
- Los módulos se hablan por interfaces de `common` (`UserDirectory`, `ProjectsApi`, `CalendarApi`, `AuditPort`) y por eventos (`DomainEvents`).

## Datos de prueba (solo desarrollo)

`docker/.env` ya trae valores de prueba y `FLYWAY_DEV_LOCATION=,classpath:db/dev`, así que al arrancar el backend se cargan solos 5 usuarios, 2 proyectos, 10 tareas, comentarios, reuniones y horas. También se pueden cargar a mano con `docker/seed-dev.sh`.

| Correo | Rol | Clave |
|---|---|---|
| admin@esfx.local | Administrador | Esfx2026! |
| gerente@esfx.local | Gerente | Esfx2026! |
| jefe@esfx.local | Jefe de proyecto | Esfx2026! |
| ana@esfx.local | Colaborador | Esfx2026! |
| luis@esfx.local | Colaborador | Esfx2026! |

Las contraseñas están guardadas como hash BCrypt. En el servidor Ubuntu: cambiar todas las claves de `.env` y dejar `FLYWAY_DEV_LOCATION` vacío.
