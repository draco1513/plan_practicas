-- =====================================================================
-- ESFX TaskFlow - Migración Flyway V1 (Fase 1 / MVP)
-- Base de datos: PostgreSQL 16
-- Regla de diseño: un esquema por módulo y SIN claves foráneas entre
-- esquemas. Las referencias entre módulos (user_id, task_id, etc.) son
-- columnas simples que valida la capa de servicio. Así cada módulo puede
-- separarse como microservicio sin tocar su modelo de datos.
-- Identificadores en inglés; los textos visibles al usuario van en español.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS access;
CREATE SCHEMA IF NOT EXISTS projects;
CREATE SCHEMA IF NOT EXISTS calendar;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE SCHEMA IF NOT EXISTS timetracking;

-- Mantiene updated_at al día en las tablas que lo tienen
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- MÓDULO: access (Acceso y usuarios)  RF-01..RF-04
-- =====================================================================
CREATE TABLE access.roles (
    id    smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code  varchar(30) NOT NULL UNIQUE,
    name  varchar(60) NOT NULL
);

CREATE TABLE access.permissions (
    id          smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        varchar(60) NOT NULL UNIQUE,
    description varchar(200)
);

CREATE TABLE access.role_permissions (
    role_id       smallint NOT NULL REFERENCES access.roles (id) ON DELETE CASCADE,
    permission_id smallint NOT NULL REFERENCES access.permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE access.users (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                varchar(255) NOT NULL,
    password_hash        varchar(100) NOT NULL,          -- BCrypt; nunca se devuelve por la API
    full_name            varchar(120) NOT NULL,
    role_id              smallint NOT NULL REFERENCES access.roles (id),
    active               boolean NOT NULL DEFAULT true,  -- los usuarios se desactivan, no se borran
    must_change_password boolean NOT NULL DEFAULT false,
    last_login_at        timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON access.users (lower(email));
CREATE INDEX ix_users_role ON access.users (role_id);
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON access.users
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Recuperación de contraseña (RF-03): se guarda el hash del token, no el token
CREATE TABLE access.password_reset_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint NOT NULL REFERENCES access.users (id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL UNIQUE,                 -- SHA-256 en hexadecimal
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reset_tokens_user ON access.password_reset_tokens (user_id);

-- Auditoría: quién cambió qué y cuándo (RF-04, RNF-09)
CREATE TABLE access.audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id bigint REFERENCES access.users (id) ON DELETE SET NULL,
    action        varchar(40) NOT NULL,                  -- CREATE, UPDATE, DELETE, LOGIN, ...
    entity_type   varchar(60) NOT NULL,                  -- USER, PROJECT, TASK, MEETING, ...
    entity_id     bigint,
    detail        jsonb,                                 -- valores antes/después, sin datos sensibles
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON access.audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_actor  ON access.audit_log (actor_user_id, created_at DESC);

-- =====================================================================
-- MÓDULO: projects (Proyectos y tareas)  RF-05..RF-10
-- =====================================================================
CREATE TABLE projects.projects (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        varchar(10) NOT NULL UNIQUE,             -- prefijo de las tareas: TF -> TF-101
    name        varchar(120) NOT NULL,
    description text,
    owner_id    bigint NOT NULL,                         -- ref. lógica a access.users
    status      varchar(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    start_date  date,
    end_date    date,
    task_seq    integer NOT NULL DEFAULT 0,              -- último número de tarea usado
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_projects_owner ON projects.projects (owner_id);
CREATE TRIGGER trg_projects_updated BEFORE UPDATE ON projects.projects
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE projects.project_members (
    project_id bigint NOT NULL REFERENCES projects.projects (id) ON DELETE CASCADE,
    user_id    bigint NOT NULL,                          -- ref. lógica a access.users
    joined_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);
CREATE INDEX ix_members_user ON projects.project_members (user_id);

CREATE TABLE projects.tasks (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id   bigint NOT NULL REFERENCES projects.projects (id) ON DELETE CASCADE,
    task_number  integer NOT NULL,                       -- se asigna con projects.task_seq (con bloqueo de fila)
    title        varchar(200) NOT NULL,
    description  text,
    status       varchar(20) NOT NULL DEFAULT 'TODO'
                 CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    priority     varchar(10) NOT NULL DEFAULT 'MEDIUM'
                 CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    assignee_id  bigint,                                 -- ref. lógica a access.users
    reporter_id  bigint NOT NULL,                        -- ref. lógica a access.users
    due_date     date,
    progress     smallint NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    position     integer NOT NULL DEFAULT 0,             -- orden dentro de la columna del Kanban
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (project_id, task_number)
);
CREATE INDEX ix_tasks_board    ON projects.tasks (project_id, status, position);
CREATE INDEX ix_tasks_assignee ON projects.tasks (assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX ix_tasks_due      ON projects.tasks (due_date) WHERE due_date IS NOT NULL;
CREATE TRIGGER trg_tasks_updated BEFORE UPDATE ON projects.tasks
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE projects.task_comments (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    bigint NOT NULL REFERENCES projects.tasks (id) ON DELETE CASCADE,
    author_id  bigint NOT NULL,                          -- ref. lógica a access.users
    body       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_comments_task ON projects.task_comments (task_id, created_at);

CREATE TABLE projects.task_attachments (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      bigint NOT NULL REFERENCES projects.tasks (id) ON DELETE CASCADE,
    uploaded_by  bigint NOT NULL,                        -- ref. lógica a access.users
    file_name    varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    size_bytes   bigint NOT NULL CHECK (size_bytes >= 0),
    storage_path varchar(500) NOT NULL,                  -- ruta en disco/volumen; el archivo no va en la BD
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_attachments_task ON projects.task_attachments (task_id);

-- =====================================================================
-- MÓDULO: calendar (Calendario y reuniones)  RF-14..RF-16
-- El calendario de la pantalla une estas reuniones con las fechas límite
-- de las tareas; esa unión se arma en el servicio, no en la BD.
-- =====================================================================
CREATE TABLE calendar.meetings (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        varchar(200) NOT NULL,
    description  text,
    start_at     timestamptz NOT NULL,
    end_at       timestamptz NOT NULL,
    location     varchar(255),                           -- lugar físico o enlace de videollamada
    organizer_id bigint NOT NULL,                        -- ref. lógica a access.users
    project_id   bigint,                                 -- ref. lógica a projects.projects (opcional)
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);
CREATE INDEX ix_meetings_start ON calendar.meetings (start_at);
CREATE INDEX ix_meetings_organizer ON calendar.meetings (organizer_id);
CREATE TRIGGER trg_meetings_updated BEFORE UPDATE ON calendar.meetings
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE calendar.meeting_invitees (
    meeting_id   bigint NOT NULL REFERENCES calendar.meetings (id) ON DELETE CASCADE,
    user_id      bigint NOT NULL,                        -- ref. lógica a access.users
    response     varchar(10) NOT NULL DEFAULT 'PENDING'
                 CHECK (response IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    responded_at timestamptz,
    PRIMARY KEY (meeting_id, user_id)
);
CREATE INDEX ix_invitees_user ON calendar.meeting_invitees (user_id);

-- =====================================================================
-- MÓDULO: notifications (Notificaciones)  RF-19..RF-21, RNF-15
-- Flujo: evento -> registro PENDING (con plantilla) -> cola Redis ->
-- envío por el proveedor (Gmail) -> SENT o FAILED. La idempotency_key
-- evita avisos duplicados; los reintentos se cuentan en attempts.
-- =====================================================================
CREATE TABLE notifications.notification_templates (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             varchar(40) NOT NULL,
    channel          varchar(10) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    subject_template varchar(200) NOT NULL,
    body_template    text NOT NULL,                      -- marcadores {{nombre}}
    UNIQUE (code, channel)
);

CREATE TABLE notifications.notifications (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL,                     -- ref. lógica a access.users
    recipient_email varchar(255),                        -- copia del correo al momento de enviar (canal EMAIL)
    type            varchar(40) NOT NULL,                -- TASK_ASSIGNED, TASK_DUE_SOON, ...
    channel         varchar(10) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL')),
    title           varchar(200) NOT NULL,
    body            text NOT NULL,
    payload         jsonb,                               -- ids para enlazar a la tarea o reunión
    status          varchar(10) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    idempotency_key varchar(150) NOT NULL UNIQUE,
    attempts        smallint NOT NULL DEFAULT 0,
    last_error      varchar(500),
    scheduled_at    timestamptz NOT NULL DEFAULT now(),  -- permite recordatorios futuros
    sent_at         timestamptz,
    read_at         timestamptz,                         -- solo canal IN_APP
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notif_user   ON notifications.notifications (user_id, created_at DESC);
CREATE INDEX ix_notif_unread ON notifications.notifications (user_id) WHERE channel = 'IN_APP' AND read_at IS NULL;
CREATE INDEX ix_notif_queue  ON notifications.notifications (scheduled_at) WHERE status = 'PENDING';

CREATE TABLE notifications.notification_preferences (
    user_id                bigint PRIMARY KEY,           -- ref. lógica a access.users
    email_enabled          boolean NOT NULL DEFAULT true,
    meeting_reminder_min   integer NOT NULL DEFAULT 30 CHECK (meeting_reminder_min >= 0),
    task_due_reminder_hrs  integer NOT NULL DEFAULT 24 CHECK (task_due_reminder_hrs >= 0),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_notif_pref_updated BEFORE UPDATE ON notifications.notification_preferences
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- =====================================================================
-- MÓDULO: timetracking (Horas y costos, parte de fase 1)  RF-36
-- El presupuesto y el costo por proyecto llegan en la fase 2.
-- =====================================================================
CREATE TABLE timetracking.time_entries (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    bigint NOT NULL,                          -- ref. lógica a projects.tasks
    project_id bigint NOT NULL,                          -- copia para sumar por proyecto sin cruzar módulos
    user_id    bigint NOT NULL,                          -- ref. lógica a access.users
    work_date  date NOT NULL,
    hours      numeric(4,2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    note       varchar(300),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_time_user_date ON timetracking.time_entries (user_id, work_date);
CREATE INDEX ix_time_task      ON timetracking.time_entries (task_id);
CREATE INDEX ix_time_project   ON timetracking.time_entries (project_id);

-- =====================================================================
-- DATOS INICIALES
-- El usuario administrador NO se crea aquí (evita guardar una contraseña
-- en el repositorio): la aplicación lo crea al arrancar por primera vez
-- con las variables APP_ADMIN_EMAIL y APP_ADMIN_PASSWORD del archivo .env.
-- =====================================================================
INSERT INTO access.roles (code, name) VALUES
    ('ADMIN',        'Administrador'),
    ('MANAGER',      'Gerente'),
    ('PROJECT_LEAD', 'Jefe de proyecto'),
    ('COLLABORATOR', 'Colaborador');

INSERT INTO access.permissions (code, description) VALUES
    ('USER_MANAGE',      'Crear, editar y desactivar usuarios y asignar roles'),
    ('AUDIT_VIEW',       'Consultar el registro de auditoría'),
    ('PROJECT_CREATE',   'Crear proyectos'),
    ('PROJECT_EDIT',     'Editar proyectos y sus miembros'),
    ('TASK_CREATE',      'Crear tareas'),
    ('TASK_ASSIGN',      'Asignar y reasignar tareas'),
    ('TASK_UPDATE_ANY',  'Editar cualquier tarea del proyecto'),
    ('TASK_UPDATE_OWN',  'Editar el estado y el avance de las tareas propias'),
    ('COMMENT_CREATE',   'Comentar y adjuntar archivos en tareas'),
    ('MEETING_CREATE',   'Programar reuniones e invitar usuarios'),
    ('MEETING_RESPOND',  'Aceptar o rechazar invitaciones'),
    ('TIME_LOG',         'Registrar horas trabajadas'),
    ('REPORT_VIEW_ALL',  'Ver el dashboard de todos los proyectos'),
    ('REPORT_VIEW_OWN',  'Ver el dashboard de los proyectos propios');

-- Matriz de permisos propuesta (se confirma con el supervisor)
INSERT INTO access.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM access.roles r CROSS JOIN access.permissions p
WHERE r.code = 'ADMIN';

INSERT INTO access.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM access.roles r JOIN access.permissions p ON p.code IN
    ('AUDIT_VIEW', 'PROJECT_CREATE', 'PROJECT_EDIT', 'COMMENT_CREATE',
     'MEETING_CREATE', 'MEETING_RESPOND', 'TIME_LOG', 'REPORT_VIEW_ALL')
WHERE r.code = 'MANAGER';

INSERT INTO access.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM access.roles r JOIN access.permissions p ON p.code IN
    ('PROJECT_CREATE', 'PROJECT_EDIT', 'TASK_CREATE', 'TASK_ASSIGN', 'TASK_UPDATE_ANY',
     'TASK_UPDATE_OWN', 'COMMENT_CREATE', 'MEETING_CREATE', 'MEETING_RESPOND',
     'TIME_LOG', 'REPORT_VIEW_OWN')
WHERE r.code = 'PROJECT_LEAD';

INSERT INTO access.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM access.roles r JOIN access.permissions p ON p.code IN
    ('TASK_UPDATE_OWN', 'COMMENT_CREATE', 'MEETING_RESPOND', 'TIME_LOG', 'REPORT_VIEW_OWN')
WHERE r.code = 'COLLABORATOR';

-- Plantillas de aviso (marcadores {{...}} que rellena el servicio)
INSERT INTO notifications.notification_templates (code, channel, subject_template, body_template) VALUES
    ('TASK_ASSIGNED',     'EMAIL', 'Nueva tarea asignada: {{taskKey}}',
        'Hola {{userName}}, se te asignó la tarea {{taskKey}} "{{taskTitle}}" en el proyecto {{projectName}}. Fecha límite: {{dueDate}}.'),
    ('TASK_ASSIGNED',     'IN_APP', 'Nueva tarea asignada',
        'Se te asignó {{taskKey}}: {{taskTitle}}'),
    ('TASK_DUE_SOON',     'EMAIL', 'La tarea {{taskKey}} vence pronto',
        'Hola {{userName}}, la tarea {{taskKey}} "{{taskTitle}}" vence el {{dueDate}}.'),
    ('TASK_DUE_SOON',     'IN_APP', 'Tarea por vencer',
        '{{taskKey}} vence el {{dueDate}}'),
    ('TASK_OVERDUE',      'EMAIL', 'La tarea {{taskKey}} está vencida',
        'Hola {{userName}}, la tarea {{taskKey}} "{{taskTitle}}" venció el {{dueDate}}.'),
    ('TASK_OVERDUE',      'IN_APP', 'Tarea vencida',
        '{{taskKey}} venció el {{dueDate}}'),
    ('MEETING_INVITATION','EMAIL', 'Invitación: {{meetingTitle}}',
        'Hola {{userName}}, fuiste invitado a "{{meetingTitle}}" el {{startAt}}. Lugar o enlace: {{location}}.'),
    ('MEETING_INVITATION','IN_APP', 'Nueva invitación a reunión',
        'Te invitaron a "{{meetingTitle}}" el {{startAt}}'),
    ('MEETING_REMINDER',  'EMAIL', 'Recordatorio: {{meetingTitle}}',
        'Hola {{userName}}, la reunión "{{meetingTitle}}" empieza el {{startAt}}.'),
    ('MEETING_REMINDER',  'IN_APP', 'Reunión próxima',
        '"{{meetingTitle}}" empieza el {{startAt}}');
