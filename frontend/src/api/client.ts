import axios, { AxiosError } from 'axios';
import type {
  ApiError, Attachment, Board, CalendarEvent, Comment, CurrentUser, Dashboard, Meeting, MeetingCreate,
  MeetingResponse, Notification, NotificationPreferences, Paged, Project, ProjectCreate, ProjectUpdate,
  Role, Task, TaskCreate, TaskStatus, TaskUpdate, TimeEntry, TimeEntryCreate, TimeSummary, TokenResponse,
  User, UserCreate, UserSummary, UserUpdate, AuditLog, ProjectStatus,
} from './types';

export const TOKEN_KEY = 'tf_token';
export const UNAUTHORIZED_EVENT = 'tf:unauthorized';

export const getToken = () => {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
};
export const setToken = (t: string | null) => {
  try { if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY); } catch { /* ignorar */ }
};

// VITE_API_URL vacío => rutas relativas (/api) que resuelven el proxy de Vite o de nginx
const base = ((import.meta.env.VITE_API_URL as string | undefined) ?? '').replace(/\/$/, '');
export const http = axios.create({ baseURL: `${base}/api` });

// Agrega el JWT a cada petición
http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Si el token vence o es inválido, se cierra la sesión (AuthContext escucha este evento)
http.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    const isLogin = error.config?.url?.includes('/auth/login');
    if (error.response?.status === 401 && !isLogin) {
      setToken(null);
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  },
);

/** Convierte cualquier error en un texto legible y la lista de errores por campo. */
export function parseError(e: unknown): { message: string; fieldErrors: { field: string; message: string }[] } {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as ApiError | undefined;
    if (data && typeof data === 'object' && (data.message || data.fieldErrors)) {
      return { message: data.message ?? 'Datos no válidos', fieldErrors: data.fieldErrors ?? [] };
    }
    if (!e.response) return { message: 'No se pudo conectar con el servidor.', fieldErrors: [] };
    if (e.response.status === 403) return { message: 'No tienes permiso para esta acción.', fieldErrors: [] };
    return { message: `Error ${e.response.status}`, fieldErrors: [] };
  }
  return { message: 'Ocurrió un error inesperado.', fieldErrors: [] };
}

export const errorText = (e: unknown) => {
  const { message, fieldErrors } = parseError(e);
  return fieldErrors.length ? `${message}: ${fieldErrors.map((f) => `${f.field} - ${f.message}`).join('; ')}` : message;
};

const d = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

export const api = {
  // ---- autenticación
  login: (email: string, password: string) => d<TokenResponse>(http.post('/auth/login', { email, password })),
  me: () => d<CurrentUser>(http.get('/auth/me')),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post('/auth/change-password', { currentPassword, newPassword }),
  forgotPassword: (email: string) => http.post('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) => http.post('/auth/reset-password', { token, newPassword }),

  // ---- usuarios
  users: (params: { page?: number; size?: number; q?: string; active?: boolean }) =>
    d<Paged<User>>(http.get('/users', { params })),
  createUser: (b: UserCreate) => d<User>(http.post('/users', b)),
  updateUser: (id: number, b: UserUpdate) => d<User>(http.put(`/users/${id}`, b)),
  setUserStatus: (id: number, active: boolean) => d<User>(http.patch(`/users/${id}/status`, { active })),
  roles: () => d<Role[]>(http.get('/roles')),
  auditLogs: (params: { page?: number; size?: number; entityType?: string; actorUserId?: number; from?: string; to?: string }) =>
    d<Paged<AuditLog>>(http.get('/audit-logs', { params })),

  // ---- proyectos
  projects: (params: { page?: number; size?: number; status?: ProjectStatus }) =>
    d<Paged<Project>>(http.get('/projects', { params })),
  project: (id: number) => d<Project>(http.get(`/projects/${id}`)),
  createProject: (b: ProjectCreate) => d<Project>(http.post('/projects', b)),
  updateProject: (id: number, b: ProjectUpdate) => d<Project>(http.put(`/projects/${id}`, b)),
  members: (id: number) => d<UserSummary[]>(http.get(`/projects/${id}/members`)),
  setMembers: (id: number, userIds: number[]) => d<UserSummary[]>(http.put(`/projects/${id}/members`, { userIds })),

  // ---- tareas
  tasks: (projectId: number, params: { page?: number; size?: number; status?: TaskStatus; assigneeId?: number; overdue?: boolean }) =>
    d<Paged<Task>>(http.get(`/projects/${projectId}/tasks`, { params })),
  board: (projectId: number) => d<Board>(http.get(`/projects/${projectId}/board`)),
  createTask: (projectId: number, b: TaskCreate) => d<Task>(http.post(`/projects/${projectId}/tasks`, b)),
  task: (id: number) => d<Task>(http.get(`/tasks/${id}`)),
  updateTask: (id: number, b: TaskUpdate) => d<Task>(http.put(`/tasks/${id}`, b)),
  assignTask: (id: number, assigneeId: number | null) => d<Task>(http.patch(`/tasks/${id}/assignee`, { assigneeId })),
  moveTask: (id: number, status: TaskStatus, position: number) =>
    d<Task>(http.patch(`/tasks/${id}/move`, { status, position })),
  setProgress: (id: number, progress: number) => d<Task>(http.patch(`/tasks/${id}/progress`, { progress })),
  comments: (id: number) => d<Comment[]>(http.get(`/tasks/${id}/comments`)),
  addComment: (id: number, body: string) => d<Comment>(http.post(`/tasks/${id}/comments`, { body })),
  attachments: (id: number) => d<Attachment[]>(http.get(`/tasks/${id}/attachments`)),
  uploadAttachment: (id: number, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return d<Attachment>(http.post(`/tasks/${id}/attachments`, fd));
  },
  /** Descarga con el token (un <a href> normal no enviaría el encabezado Authorization). */
  downloadAttachment: async (a: Attachment) => {
    const r = await http.get(`/attachments/${a.id}/download`, { responseType: 'blob' });
    const url = URL.createObjectURL(r.data as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = a.fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  },

  // ---- calendario
  calendar: (from: string, to: string, scope: 'me' | 'team') =>
    d<CalendarEvent[]>(http.get('/calendar', { params: { from, to, scope } })),
  meeting: (id: number) => d<Meeting>(http.get(`/meetings/${id}`)),
  createMeeting: (b: MeetingCreate) => d<Meeting>(http.post('/meetings', b)),
  updateMeeting: (id: number, b: MeetingCreate) => d<Meeting>(http.put(`/meetings/${id}`, b)),
  cancelMeeting: (id: number) => http.delete(`/meetings/${id}`),
  respondMeeting: (id: number, response: Exclude<MeetingResponse, 'PENDING'>) =>
    d<Meeting>(http.put(`/meetings/${id}/response`, { response })),

  // ---- notificaciones
  notifications: (params: { page?: number; size?: number; unreadOnly?: boolean }) =>
    d<Paged<Notification>>(http.get('/notifications', { params })),
  unreadCount: () => d<{ count: number }>(http.get('/notifications/unread-count')),
  markRead: (id: number) => http.post(`/notifications/${id}/read`),
  markAllRead: () => http.post('/notifications/read-all'),
  preferences: () => d<NotificationPreferences>(http.get('/notifications/preferences')),
  savePreferences: (b: NotificationPreferences) => d<NotificationPreferences>(http.put('/notifications/preferences', b)),

  // ---- reportes y horas
  dashboard: (projectId?: number) => d<Dashboard>(http.get('/reports/dashboard', { params: { projectId } })),
  timeEntries: (params: { page?: number; size?: number; userId?: number; projectId?: number; taskId?: number; from?: string; to?: string }) =>
    d<Paged<TimeEntry>>(http.get('/time-entries', { params })),
  logTime: (b: TimeEntryCreate) => d<TimeEntry>(http.post('/time-entries', b)),
  deleteTime: (id: number) => http.delete(`/time-entries/${id}`),
  timeSummary: (projectId: number) => d<TimeSummary>(http.get(`/projects/${projectId}/time-summary`)),
};
