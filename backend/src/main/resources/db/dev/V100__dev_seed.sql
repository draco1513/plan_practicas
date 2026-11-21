-- =====================================================================
-- DATOS DE PRUEBA (solo desarrollo). NO usar en producción.
-- Se aplica automáticamente cuando FLYWAY_DEV_LOCATION=,classpath:db/dev (ver docker/.env).
-- También se puede ejecutar a mano: docker/seed-dev.sh
-- Contraseña de TODOS los usuarios de prueba: Esfx2026!   (guardada como hash BCrypt)
-- Las fechas son relativas a hoy, así que los datos siempre se ven "frescos".
-- =====================================================================

-- ---- 5 usuarios: uno por rol + un segundo colaborador
INSERT INTO access.users (email, password_hash, full_name, role_id, must_change_password)
SELECT v.email, '$2a$10$FnSuVgDL1uhiULTI7FgjCu8Ww4vQviV8Vt0VSBYu0CxCeLcVQ8A2K', v.full_name, r.id, false
FROM (VALUES
    ('admin@esfx.local',   'Administrador ESFX',  'ADMIN'),
    ('gerente@esfx.local', 'María Gerente',       'MANAGER'),
    ('jefe@esfx.local',    'Carlos Jefe',         'PROJECT_LEAD'),
    ('ana@esfx.local',     'Ana Colaboradora',    'COLLABORATOR'),
    ('luis@esfx.local',    'Luis Colaborador',    'COLLABORATOR')
) AS v(email, full_name, role_code)
JOIN access.roles r ON r.code = v.role_code
WHERE NOT EXISTS (SELECT 1 FROM access.users u WHERE lower(u.email) = lower(v.email));

-- ---- 2 proyectos
INSERT INTO projects.projects (code, name, owner_id, status, start_date, end_date)
SELECT v.code, v.name, u.id, v.status, current_date + v.s, current_date + v.e
FROM (VALUES
    ('TF',  'ESFX TaskFlow interno',  'jefe@esfx.local', 'ACTIVE',  -14, 30),
    ('WEB', 'Rediseño del sitio web', 'jefe@esfx.local', 'PLANNED',   3, 60)
) AS v(code, name, owner, status, s, e)
JOIN access.users u ON u.email = v.owner
ON CONFLICT (code) DO NOTHING;

INSERT INTO projects.project_members (project_id, user_id)
SELECT p.id, u.id
FROM (VALUES
    ('TF', 'jefe@esfx.local'), ('TF', 'ana@esfx.local'), ('TF', 'luis@esfx.local'),
    ('WEB', 'jefe@esfx.local'), ('WEB', 'ana@esfx.local')
) AS v(code, email)
JOIN projects.projects p ON p.code = v.code
JOIN access.users u ON u.email = v.email
ON CONFLICT DO NOTHING;

-- ---- Tareas (varios estados, prioridades y fechas; una vencida)
INSERT INTO projects.tasks (project_id, task_number, title, status, priority, assignee_id, reporter_id, due_date, progress, position)
SELECT p.id, t.n, t.title, t.status, t.priority, a.id, r.id,
       CASE WHEN t.due IS NULL THEN NULL ELSE current_date + t.due END, t.progress, t.pos
FROM (VALUES
    ('TF', 1, 'Definir alcance y roles del sistema',     'DONE',        'HIGH',   'ana@esfx.local',  -10, 100, 0),
    ('TF', 2, 'Diseñar la base de datos',                'DONE',        'HIGH',   'luis@esfx.local',  -7, 100, 1),
    ('TF', 3, 'Implementar login con JWT',               'IN_PROGRESS', 'HIGH',   'luis@esfx.local',   2,  60, 0),
    ('TF', 4, 'Tablero Kanban de tareas',                'IN_PROGRESS', 'MEDIUM', 'ana@esfx.local',    4,  35, 1),
    ('TF', 5, 'Corregir filtro de tareas vencidas',      'TODO',        'HIGH',   'ana@esfx.local',   -2,   0, 0),
    ('TF', 6, 'Notificaciones por correo',               'TODO',        'MEDIUM', 'luis@esfx.local',   9,   0, 1),
    ('TF', 7, 'Reporte de horas por proyecto',           'TODO',        'LOW',    NULL,               20,   0, 2),
    ('WEB', 1, 'Levantar requisitos con el cliente',     'TODO',        'HIGH',   'ana@esfx.local',    5,   0, 0),
    ('WEB', 2, 'Propuesta de diseño (3 pantallas)',      'TODO',        'MEDIUM', NULL,               12,   0, 1),
    ('WEB', 3, 'Selección de hosting',                   'TODO',        'LOW',    'jefe@esfx.local',  15,   0, 2)
) AS t(code, n, title, status, priority, assignee, due, progress, pos)
JOIN projects.projects p ON p.code = t.code
LEFT JOIN access.users a ON a.email = t.assignee
JOIN access.users r ON r.email = 'jefe@esfx.local'
ON CONFLICT (project_id, task_number) DO NOTHING;

UPDATE projects.projects p
SET task_seq = (SELECT COALESCE(max(t.task_number), 0) FROM projects.tasks t WHERE t.project_id = p.id)
WHERE p.code IN ('TF', 'WEB');

-- ---- Comentarios
INSERT INTO projects.task_comments (task_id, author_id, body)
SELECT t.id, u.id, v.body
FROM (VALUES
    ('TF', 3, 'luis@esfx.local', 'Ya está el endpoint de login, falta probar el vencimiento del token.'),
    ('TF', 3, 'jefe@esfx.local', 'Perfecto. Revisa también el cambio de contraseña obligatorio.'),
    ('TF', 5, 'jefe@esfx.local', 'Prioridad alta: el dashboard no está contando bien las vencidas.')
) AS v(code, n, email, body)
JOIN projects.projects p ON p.code = v.code
JOIN projects.tasks t ON t.project_id = p.id AND t.task_number = v.n
JOIN access.users u ON u.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM projects.task_comments);

-- ---- Reuniones (hora de Lima) con invitados
INSERT INTO calendar.meetings (title, description, start_at, end_at, location, organizer_id, project_id)
SELECT v.title, v.descr,
       ((current_date + v.d)::timestamp + make_interval(hours => v.h)) AT TIME ZONE 'America/Lima',
       ((current_date + v.d)::timestamp + make_interval(hours => v.h, mins => v.dur)) AT TIME ZONE 'America/Lima',
       v.loc, u.id, p.id
FROM (VALUES
    ('Reunión de avance semanal', 'Revisión del tablero y bloqueos', 1, 10, 45, 'Sala 1 / Google Meet', 'jefe@esfx.local', 'TF'),
    ('Kickoff rediseño web',      'Presentación del alcance',        4, 15, 60, 'Google Meet',          'jefe@esfx.local', 'WEB')
) AS v(title, descr, d, h, dur, loc, organizer, code)
JOIN access.users u ON u.email = v.organizer
JOIN projects.projects p ON p.code = v.code
WHERE NOT EXISTS (SELECT 1 FROM calendar.meetings m WHERE m.title = v.title);

INSERT INTO calendar.meeting_invitees (meeting_id, user_id, response)
SELECT m.id, u.id, v.resp
FROM (VALUES
    ('Reunión de avance semanal', 'ana@esfx.local',    'ACCEPTED'),
    ('Reunión de avance semanal', 'luis@esfx.local',   'PENDING'),
    ('Reunión de avance semanal', 'gerente@esfx.local','PENDING'),
    ('Kickoff rediseño web',      'ana@esfx.local',    'PENDING'),
    ('Kickoff rediseño web',      'gerente@esfx.local','ACCEPTED')
) AS v(title, email, resp)
JOIN calendar.meetings m ON m.title = v.title
JOIN access.users u ON u.email = v.email
ON CONFLICT DO NOTHING;

-- ---- Horas trabajadas
INSERT INTO timetracking.time_entries (task_id, project_id, user_id, work_date, hours, note)
SELECT t.id, p.id, u.id, current_date + v.d, v.hours, v.note
FROM (VALUES
    ('TF', 1, 'ana@esfx.local',  -9, 3.00, 'Taller de alcance'),
    ('TF', 2, 'luis@esfx.local', -6, 5.50, 'Modelo entidad-relación'),
    ('TF', 3, 'luis@esfx.local', -1, 4.00, 'Login y filtro JWT'),
    ('TF', 4, 'ana@esfx.local',  -1, 2.50, 'Columnas del Kanban'),
    ('TF', 4, 'ana@esfx.local',   0, 1.75, 'Arrastrar y soltar')
) AS v(code, n, email, d, hours, note)
JOIN projects.projects p ON p.code = v.code
JOIN projects.tasks t ON t.project_id = p.id AND t.task_number = v.n
JOIN access.users u ON u.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM timetracking.time_entries);

-- ---- Algunos avisos en la campana de Ana
INSERT INTO notifications.notifications (user_id, type, channel, title, body, payload, status, idempotency_key, sent_at)
SELECT u.id, v.type, 'IN_APP', v.title, v.body, CAST(v.payload AS jsonb), 'SENT', v.key, now()
FROM (VALUES
    ('TASK_ASSIGNED', 'Nueva tarea asignada', 'Se te asignó TF-4: Tablero Kanban de tareas', '{"projectId": 1}', 'seed:ana:1'),
    ('TASK_OVERDUE',  'Tarea vencida',        'TF-5 venció hace 2 días',                      '{"projectId": 1}', 'seed:ana:2')
) AS v(type, title, body, payload, key)
JOIN access.users u ON u.email = 'ana@esfx.local'
ON CONFLICT (idempotency_key) DO NOTHING;
