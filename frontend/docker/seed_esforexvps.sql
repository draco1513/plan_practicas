-- Seed data for EsForexVPS
--
-- This script assumes the database has been migrated using V1__init.sql and V2__password_reset_template.sql.
-- It creates users, projects, project members, tasks, meetings, meeting invitees, and notifications.

BEGIN;

-- 1. Create additional users (copy password_hash from admin)
INSERT INTO access.users (email,password_hash,full_name,role_id,active,must_change_password)
SELECT 'carlos.mendoza@esfx.local',
       p.password_hash,
       'Carlos Mendoza',
       r.id,
       true,
       false
FROM (
  SELECT password_hash FROM access.users WHERE email='admin@esfx.local'
) p
JOIN access.roles r ON r.code='MANAGER';

INSERT INTO access.users (email,password_hash,full_name,role_id,active,must_change_password)
SELECT 'laura.silva@esfx.local',
       p.password_hash,
       'Laura Silva',
       r.id,
       true,
       false
FROM (
  SELECT password_hash FROM access.users WHERE email='admin@esfx.local'
) p
JOIN access.roles r ON r.code='PROJECT_LEAD';

INSERT INTO access.users (email,password_hash,full_name,role_id,active,must_change_password)
SELECT 'diego.rojas@esfx.local',
       p.password_hash,
       'Diego Rojas',
       r.id,
       true,
       false
FROM (
  SELECT password_hash FROM access.users WHERE email='admin@esfx.local'
) p
JOIN access.roles r ON r.code='COLLABORATOR';

INSERT INTO access.users (email,password_hash,full_name,role_id,active,must_change_password)
SELECT 'valeria.torres@esfx.local',
       p.password_hash,
       'Valeria Torres',
       r.id,
       true,
       false
FROM (
  SELECT password_hash FROM access.users WHERE email='admin@esfx.local'
) p
JOIN access.roles r ON r.code='COLLABORATOR';

-- 2. Create projects
INSERT INTO projects.projects (code,name,description,owner_id,status)
VALUES
  ('LD4','Infraestructura VPS Londres LD4', 'Proyecto de infraestructura en Londres.',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'ACTIVE'),
  ('PRT','Portal Clientes EsForexVPS v2', 'Actualización del portal de clientes.',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'ACTIVE'),
  ('MON','Monitor Latencia Brokers & Backups', 'Monitoreo continuo de latencias y backups.',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'ACTIVE');

-- 3. Add project members (all five users to each project)
INSERT INTO projects.project_members (project_id,user_id)
SELECT p.id,u.id
FROM projects.projects p
JOIN access.users u ON u.email IN (
  'admin@esfx.local',
  'carlos.mendoza@esfx.local',
  'laura.silva@esfx.local',
  'diego.rojas@esfx.local',
  'valeria.torres@esfx.local'
)
WHERE p.code IN ('LD4','PRT','MON');

-- 4. Create tasks (8 tasks across the projects)
-- Assuming tasks table has columns: project_id, task_number, title, description, assignee_id, reporter_id, due_date, status, priority
INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='LD4'), 1,
       'Configurar VPS',
       'Instalar y configurar la nueva instancia de VPS en Londres.',
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       (SELECT id FROM access.users WHERE email='laura.silva@esfx.local'),
       CURRENT_DATE + INTERVAL '2 days',
       'TODO', 1;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='LD4'), 2,
       'Pruebas de rendimiento',
       'Realizar pruebas de carga y rendimiento en el VPS.',
       (SELECT id FROM access.users WHERE email='diego.rojas@esfx.local'),
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       CURRENT_DATE + INTERVAL '5 days',
       'IN_PROGRESS', 2;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='PRT'), 1,
       'Diseñar nueva UI',
       'Rediseñar la interfaz de usuario del portal de clientes.',
       (SELECT id FROM access.users WHERE email='laura.silva@esfx.local'),
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       CURRENT_DATE + INTERVAL '7 days',
       'TODO', 3;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='PRT'), 2,
       'Implementar autenticación OAuth',
       'Agregar soporte para inicio de sesión con OAuth.',
       (SELECT id FROM access.users WHERE email='diego.rojas@esfx.local'),
       (SELECT id FROM access.users WHERE email='laura.silva@esfx.local'),
       CURRENT_DATE + INTERVAL '10 days',
       'IN_PROGRESS', 2;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='MON'), 1,
       'Instalar agente de monitoreo',
       'Instalar y configurar el agente que envía métricas a la nube.',
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       (SELECT id FROM access.users WHERE email='valeria.torres@esfx.local'),
       CURRENT_DATE + INTERVAL '3 days',
       'TODO', 2;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='MON'), 2,
       'Alertas de latencia alta',
       'Configurar alertas cuando la latencia supere el umbral.',
       (SELECT id FROM access.users WHERE email='diego.rojas@esfx.local'),
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       CURRENT_DATE + INTERVAL '6 days',
       'IN_PROGRESS', 1;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='MON'), 3,
       'Backup periódico',
       'Verificar backups diarios y realizar pruebas de restauración.',
       (SELECT id FROM access.users WHERE email='valeria.torres@esfx.local'),
       (SELECT id FROM access.users WHERE email='diego.rojas@esfx.local'),
       CURRENT_DATE + INTERVAL '8 days',
       'DONE', 3;

INSERT INTO projects.tasks (project_id,task_number,title,description,assignee_id,reporter_id,due_date,status,priority)
SELECT (SELECT id FROM projects.projects WHERE code='PRT'), 3,
       'Pruebas de integración',
       'Realizar pruebas integradas entre front-end y back-end.',
       (SELECT id FROM access.users WHERE email='carlos.mendoza@esfx.local'),
       (SELECT id FROM access.users WHERE email='laura.silva@esfx.local'),
       CURRENT_DATE + INTERVAL '12 days',
       'TODO', 2;

-- 5. Create meetings (3 meetings)
INSERT INTO calendar.meetings (title,description,start_at,end_at,organizer_id,status)
VALUES
  ('Reunión de inicio LD4','Kickoff del proyecto LD4',
   CURRENT_DATE + INTERVAL '0 days' + INTERVAL '10 hours', -- today 10:00
   CURRENT_DATE + INTERVAL '0 days' + INTERVAL '11 hours',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'SCHEDULED'),
  ('Reunión de revisión PRT','Revisión semanal del portal',
   CURRENT_DATE + INTERVAL '1 day' + INTERVAL '14 hours', -- tomorrow 14:00
   CURRENT_DATE + INTERVAL '1 day' + INTERVAL '15 hours',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'SCHEDULED'),
  ('Reunión de monitorización','Actualizaciones sobre latencia y backups',
   CURRENT_DATE + INTERVAL '3 days' + INTERVAL '9 hours', -- in 3 days 09:00
   CURRENT_DATE + INTERVAL '3 days' + INTERVAL '10 hours 30 minutes',
   (SELECT id FROM access.users WHERE email='admin@esfx.local'), 'SCHEDULED');

-- Invite all users to each meeting
INSERT INTO calendar.meeting_invitees (meeting_id,user_id,status)
SELECT m.id, u.id, 'INVITED'
FROM calendar.meetings m
JOIN access.users u ON u.email IN (
  'admin@esfx.local',
  'carlos.mendoza@esfx.local',
  'laura.silva@esfx.local',
  'diego.rojas@esfx.local',
  'valeria.torres@esfx.local'
);

-- 6. Notifications for admin (IN_APP)
INSERT INTO notifications.notifications (recipient_user_id,type,content,status)
SELECT (SELECT id FROM access.users WHERE email='admin@esfx.local'),
       'IN_APP',
       'Has nuevas reuniones programadas para hoy y los próximos días.',
       'UNREAD';

COMMIT;
-- End of seed script.
