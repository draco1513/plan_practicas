import type { ProjectStatus, TaskPriority, TaskStatus } from '../api/types';

export const STATUS_LABEL: Record<TaskStatus, string> = { TODO: 'Por hacer', IN_PROGRESS: 'En curso', DONE: 'Hecha' };
export const PRIORITY_LABEL: Record<TaskPriority, string> = { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta' };
export const PROJECT_STATUS_LABEL: Record<ProjectStatus, string> = {
  PLANNED: 'Planificado', ACTIVE: 'Activo', ON_HOLD: 'En pausa', COMPLETED: 'Completado', CANCELLED: 'Cancelado',
};
export const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Administrador', MANAGER: 'Gerente', PROJECT_LEAD: 'Jefe de proyecto', COLLABORATOR: 'Colaborador',
};

const pad = (n: number) => String(n).padStart(2, '0');
/** Fecha local en formato yyyy-MM-dd (para inputs y para la API). */
export const toISODate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
/** Valor para <input type="datetime-local">. */
export const toLocalInput = (iso: string | Date) => {
  const d = typeof iso === 'string' ? new Date(iso) : iso;
  return `${toISODate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

export const fmtDate = (s?: string | null) => {
  if (!s) return '—';
  const [y, m, d] = s.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
};
export const fmtDateTime = (s?: string | null) => {
  if (!s) return '—';
  return new Date(s).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
};
export const fmtTime = (s: string) => new Date(s).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
export const fmtSize = (b: number) => (b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : `${(b / 1048576).toFixed(1)} MB`);
export const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
