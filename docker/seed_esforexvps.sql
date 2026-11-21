BEGIN;

-- 1. Equipo completo de 5 personas de EsForexVPS (1 Admin ya existe + 4 nuevos con su misma clave)
WITH admin_pwd AS (
    SELECT password_hash FROM access.users WHERE lower(email) = 'admin@esfx.local' LIMIT 1
)
INSERT INTO access.users (email, password_hash, full_name, role_id, active, must_change_password)
SELECT u.email, a.password_hash, u.full_name, r.id, true, false
FROM admin_pwd a
CROSS JOIN (VALUES
    ('carlos.mendoza@esfx.local', 'Carlos Mendoza', 'MANAGER'),
    ('laura.silva@esfx.local',    'Laura Silva',    'PROJECT_LEAD'),
    ('diego.rojas@esfx.local',    'Diego Rojas',    'COLLABORATOR'),
    ('valeria.torres@esfx.local', 'Valeria Torres', 'COLLABORATOR')
) AS u(email, full_name, role_code)
JOIN access.roles r ON r.code = u.role_code
ON CONFLICT DO NOTHING;

-- 2. Tres proyectos activos de infraestructura VPS Forex
INSERT INTO projects.projects (code, name, description, owner_id, status, start_date, end_date, task_seq)
VALUES
    ('LD4', 'Infraestructura VPS Londres LD4',
     'Despliegue y optimización de nodos KVM de baja latencia (<1ms hacia Equinix LD4) para cuentas MT4/MT5.',
     (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
     'ACTIVE', CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE + INTERVAL '30 days', 3),
    ('PRT', 'Portal Clientes EsForexVPS v2',
     'Panel de autogestión de VPS, reinicio remoto, monitoreo de CPU/RAM en vivo y facturación automática.',
     (SELECT id FROM access.users WHERE lower(email) = 'laura.silva@esfx.local'),
     'ACTIVE', CURRENT_DATE - INTERVAL '10 days', CURRENT_DATE + INTERVAL '25 days', 3),
    ('MON', 'Monitor Latencia Brokers & Backups',
     'Alertas 24/7 de ping hacia brokers (IC Markets, Pepperstone, FTMO) y snapshots automáticos NVMe.',
     (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
     'ACTIVE', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '20 days', 2)
ON CONFLICT (code) DO NOTHING;

-- 3. Asignar el equipo de 5 personas a los proyectos
INSERT INTO projects.project_members (project_id, user_id)
SELECT p.id, u.id FROM projects.projects p CROSS JOIN access.users u
WHERE p.code IN ('LD4', 'PRT', 'MON')
ON CONFLICT DO NOTHING;

-- 4. Tareas del tablero Kanban (TODO, IN_PROGRESS, DONE)
INSERT INTO projects.tasks (project_id, task_number, title, description, status, priority, assignee_id, reporter_id, due_date, progress, position)
VALUES
    ((SELECT id FROM projects.projects WHERE code = 'LD4'), 1,
     'Configurar hipervisores NVMe en rack Equinix LD4',
     'Preparar nodos AMD Ryzen 9 con almacenamiento RAID-10 NVMe y aislamiento de núcleos.',
     'DONE', 'HIGH',
     (SELECT id FROM access.users WHERE lower(email) = 'diego.rojas@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
     CURRENT_DATE - INTERVAL '2 days', 100, 1),
    ((SELECT id FROM projects.projects WHERE code = 'LD4'), 2,
     'Optimizar rutas BGP hacia servidores MetaTrader Londres',
     'Reducir jitter y latencia por debajo de 0.8 ms hacia proveedores de liquidez.',
     'IN_PROGRESS', 'HIGH',
     (SELECT id FROM access.users WHERE lower(email) = 'diego.rojas@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
     CURRENT_DATE + INTERVAL '3 days', 65, 1),
    ((SELECT id FROM projects.projects WHERE code = 'LD4'), 3,
     'Plantilla Windows Server 2022 optimizada para EAs',
     'Desactivar servicios innecesarios para ejecución continua de Expert Advisors.',
     'TODO', 'MEDIUM',
     (SELECT id FROM access.users WHERE lower(email) = 'valeria.torres@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'laura.silva@esfx.local'),
     CURRENT_DATE + INTERVAL '6 days', 0, 1),
    ((SELECT id FROM projects.projects WHERE code = 'PRT'), 1,
     'API de reinicio y consola VNC desde panel cliente',
     'Conectar endpoints de control energético KVM con permisos por usuario.',
     'DONE', 'HIGH',
     (SELECT id FROM access.users WHERE lower(email) = 'laura.silva@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
     CURRENT_DATE - INTERVAL '1 day', 100, 1),
    ((SELECT id FROM projects.projects WHERE code = 'PRT'), 2,
     'Gráficas en tiempo real de uso de CPU, RAM y red del VPS',
     'Integrar métricas cada 5 segundos en la vista de detalle del servidor contratado.',
     'IN_PROGRESS', 'HIGH',
     (SELECT id FROM access.users WHERE lower(email) = 'valeria.torres@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'laura.silva@esfx.local'),
     CURRENT_DATE + INTERVAL '2 days', 50, 2),
    ((SELECT id FROM projects.projects WHERE code = 'PRT'), 3,
     'Pasarela de renovación automática de planes Forex Pro',
     'Notificar 72h antes del vencimiento para evitar desconexión de operaciones abiertas.',
     'TODO', 'HIGH',
     (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
     CURRENT_DATE + INTERVAL '5 days', 0, 2),
    ((SELECT id FROM projects.projects WHERE code = 'MON'), 1,
     'Sondas de latencia ICMP/TCP hacia 15 brokers Forex',
     'Medir tiempos de respuesta desde NY4, LD4 y TY3 cada minuto.',
     'IN_PROGRESS', 'MEDIUM',
     (SELECT id FROM access.users WHERE lower(email) = 'diego.rojas@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
     CURRENT_DATE + INTERVAL '4 days', 40, 3),
    ((SELECT id FROM projects.projects WHERE code = 'MON'), 2,
     'Política de snapshots diarios fuera de horario de mercado',
     'Programar copias incrementales los sábados a las 02:00 UTC con retención de 14 días.',
     'TODO', 'LOW',
     (SELECT id FROM access.users WHERE lower(email) = 'valeria.torres@esfx.local'),
     (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
     CURRENT_DATE + INTERVAL '9 days', 0, 3)
ON CONFLICT (project_id, task_number) DO NOTHING;

-- 5. Comentarios en tareas
INSERT INTO projects.task_comments (task_id, author_id, body)
SELECT t.id, u.id, 'Latencia verificada en 0.68 ms promedio hacia LD4. Listo para pasar a validación final.'
FROM projects.tasks t JOIN projects.projects p ON p.id = t.project_id
JOIN access.users u ON lower(u.email) = 'diego.rojas@esfx.local'
WHERE p.code = 'LD4' AND t.task_number = 2
AND NOT EXISTS (SELECT 1 FROM projects.task_comments WHERE task_id = t.id);

-- 6. Reuniones en el Calendario (hoy, mañana y en 3 días)
INSERT INTO calendar.meetings (title, description, start_at, end_at, location, organizer_id, project_id)
SELECT 'Revisión SLA & Latencia Nodos Londres LD4',
       'Evaluación semanal de tiempos de ejecución y carga de nodos KVM.',
       CURRENT_DATE + TIME '10:00:00', CURRENT_DATE + TIME '11:00:00',
       'https://esforexvps.com',
       (SELECT id FROM access.users WHERE lower(email) = 'carlos.mendoza@esfx.local'),
       (SELECT id FROM projects.projects WHERE code = 'LD4')
WHERE NOT EXISTS (SELECT 1 FROM calendar.meetings WHERE title = 'Revisión SLA & Latencia Nodos Londres LD4');

INSERT INTO calendar.meetings (title, description, start_at, end_at, location, organizer_id, project_id)
SELECT 'Demo Sprint Portal Clientes EsForexVPS v2',
       'Presentación de gráficas en vivo y consola remota al equipo completo.',
       CURRENT_DATE + INTERVAL '1 day' + TIME '15:00:00', CURRENT_DATE + INTERVAL '1 day' + TIME '16:00:00',
       'Sala Operaciones / Google Meet',
       (SELECT id FROM access.users WHERE lower(email) = 'laura.silva@esfx.local'),
       (SELECT id FROM projects.projects WHERE code = 'PRT')
WHERE NOT EXISTS (SELECT 1 FROM calendar.meetings WHERE title = 'Demo Sprint Portal Clientes EsForexVPS v2');

INSERT INTO calendar.meetings (title, description, start_at, end_at, location, organizer_id, project_id)
SELECT 'Ventana de Mantenimiento Snapshots NVMe',
       'Coordinación de respaldo semanal fuera de sesión Forex.',
       CURRENT_DATE + INTERVAL '3 days' + TIME '18:00:00', CURRENT_DATE + INTERVAL '3 days' + TIME '19:00:00',
       'NOC EsForexVPS',
       (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
       (SELECT id FROM projects.projects WHERE code = 'MON')
WHERE NOT EXISTS (SELECT 1 FROM calendar.meetings WHERE title = 'Ventana de Mantenimiento Snapshots NVMe');

INSERT INTO calendar.meeting_invitees (meeting_id, user_id, response)
SELECT m.id, u.id, 'ACCEPTED' FROM calendar.meetings m CROSS JOIN access.users u
ON CONFLICT DO NOTHING;

-- 7. Notificaciones IN_APP para el Administrador
INSERT INTO notifications.notifications (user_id, type, channel, title, body, status, idempotency_key, sent_at)
SELECT (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
       'TASK_ASSIGNED', 'IN_APP',
       'Avance en LD4-2: Optimización BGP',
       'Diego Rojas actualizó el progreso de LD4-2 al 65%.',
       'SENT', 'demo-notif-ld4-2', now()
ON CONFLICT (idempotency_key) DO NOTHING;

INSERT INTO notifications.notifications (user_id, type, channel, title, body, status, idempotency_key, sent_at)
SELECT (SELECT id FROM access.users WHERE lower(email) = 'admin@esfx.local'),
       'MEETING_INVITATION', 'IN_APP',
       'Reunión hoy: Revisión SLA Londres LD4',
       'Tienes una reunión programada hoy a las 10:00 con el equipo de infraestructura.',
       'SENT', 'demo-notif-meet-1', now()
ON CONFLICT (idempotency_key) DO NOTHING;

-- 8. Horas registradas en timetracking para reportes
INSERT INTO timetracking.time_entries (task_id, project_id, user_id, work_date, hours, note)
SELECT t.id, t.project_id, t.assignee_id, CURRENT_DATE - INTERVAL '1 day', 4.50,
       'Pruebas de estrés y benchmarks de latencia MT5'
FROM projects.tasks t JOIN projects.projects p ON p.id = t.project_id
WHERE p.code = 'LD4' AND t.task_number = 2
AND NOT EXISTS (SELECT 1 FROM timetracking.time_entries WHERE task_id = t.id);
COMMIT;
