// Tipos escritos a mano a partir de docs/api/openapi.yaml.
// Si el contrato cambia, se pueden regenerar con: npx openapi-typescript ../docs/api/openapi.yaml -o src/api/schema.d.ts

export type RoleCode = 'ADMIN' | 'MANAGER' | 'PROJECT_LEAD' | 'COLLABORATOR';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type MeetingResponse = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface ApiError {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Paged<T> {
  page: number; // empieza en 0
  size: number;
  totalElements: number;
  totalPages: number;
  content: T[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  mustChangePassword: boolean;
}

export interface Role { id: number; code: RoleCode; name: string }
export interface UserSummary { id: number; fullName: string; email: string }
export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  lastLoginAt?: string | null;
}
export interface CurrentUser extends User { permissions: string[] }
export interface UserCreate { email: string; fullName: string; roleId: number; temporaryPassword: string }
export interface UserUpdate { fullName: string; roleId: number }

export interface AuditLog {
  id: number;
  actor: UserSummary;
  action: string;
  entityType: string;
  entityId: number;
  detail?: Record<string, unknown>;
  createdAt: string;
}

export interface Project {
  id: number;
  code: string;
  name: string;
  owner: UserSummary;
  status: ProjectStatus;
  startDate?: string | null;
  endDate?: string | null;
  progress: number;
  taskCount: number;
}
export interface ProjectCreate { code: string; name: string; ownerId: number; startDate?: string; endDate?: string }
export interface ProjectUpdate { name: string; ownerId: number; status: ProjectStatus; startDate?: string; endDate?: string }

export interface Task {
  id: number;
  key: string;
  projectId: number;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  assignee?: UserSummary | null;
  reporter: UserSummary;
  dueDate?: string | null;
  overdue: boolean;
  progress: number;
  position: number;
  createdAt: string;
  updatedAt: string;
}
export interface TaskCreate { title: string; priority?: TaskPriority; assigneeId?: number | null; dueDate?: string }
export interface TaskUpdate { title: string; priority: TaskPriority; dueDate?: string | null }
export interface Board { projectId: number; columns: { status: TaskStatus; tasks: Task[] }[] }

export interface Comment { id: number; author: UserSummary; body: string; createdAt: string }
export interface Attachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: UserSummary;
  createdAt: string;
}

export interface CalendarEvent {
  type: 'MEETING' | 'TASK_DUE';
  refId: number;
  title: string;
  startAt: string;
  endAt?: string | null;
  allDay: boolean;
}
export interface Invitee { user: UserSummary; response: MeetingResponse }
export interface Meeting {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  location?: string | null;
  organizer: UserSummary;
  projectId?: number | null;
  invitees: Invitee[];
}
export interface MeetingCreate {
  title: string;
  startAt: string;
  endAt: string;
  location?: string;
  projectId?: number | null;
  inviteeIds: number[];
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  body: string;
  payload?: Record<string, unknown>;
  read: boolean;
  createdAt: string;
}
export interface NotificationPreferences {
  emailEnabled: boolean;
  meetingReminderMin: number;
  taskDueReminderHrs: number;
}

export interface Dashboard {
  generatedAt: string;
  tasksByStatus: { status: TaskStatus; count: number }[];
  tasksByAssignee: { user: UserSummary; todo: number; inProgress: number; done: number }[];
  tasksByProject: { projectId: number; projectName: string; total: number; done: number; progress: number }[];
  overdueTasks: Task[];
}

export interface TimeEntry {
  id: number;
  taskId: number;
  taskKey: string;
  projectId: number;
  user: UserSummary;
  workDate: string;
  hours: number;
  note?: string | null;
}
export interface TimeEntryCreate { taskId: number; workDate: string; hours: number; note?: string }
export interface TimeSummary {
  projectId: number;
  totalHours: number;
  byUser: { user: UserSummary; hours: number }[];
  byTask: { taskKey: string; title: string; hours: number }[];
}
