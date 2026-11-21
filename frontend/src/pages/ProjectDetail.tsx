import { useCallback, useEffect, useState, type DragEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, Card, Form, Modal, ProgressBar, Tab, Table, Tabs } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Board, Paged, Project, Task, TaskStatus, TimeSummary, UserSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useDirectory } from '../auth/useDirectory';
import { Avatar, Empty, Loading, Pager, PriorityBadge, StatusBadge } from '../components/Common';
import TaskModal, { canEditTask } from '../components/TaskModal';
import { useToast } from '../components/Toasts';
import { ProjectForm } from './ProjectsPage';
import { fmtDate, PROJECT_STATUS_LABEL, STATUS_LABEL } from '../utils/format';

export default function ProjectDetail() {
  const { id } = useParams();
  const projectId = Number(id);
  const { can, user } = useAuth();
  const people = useDirectory();
  const [project, setProject] = useState<Project | null>(null);
  const [board, setBoard] = useState<Board | null>(null);
  const [error, setError] = useState('');
  const [taskModal, setTaskModal] = useState<Task | 'new' | null>(null);
  const [editing, setEditing] = useState(false);
  const [version, setVersion] = useState(0); // fuerza recarga de la pestaña "Tabla"

  const load = useCallback(() => {
    api.project(projectId).then(setProject).catch((e) => setError(errorText(e)));
    api.board(projectId).then(setBoard).catch((e) => setError(errorText(e)));
    setVersion((v) => v + 1);
  }, [projectId]);
  useEffect(load, [load]);

  if (error && !project) return <Alert variant="danger">{error}</Alert>;
  if (!project || !board) return <Loading />;

  return (
    <>
      <div className="mb-3">
        <Link to="/projects" className="small">← Proyectos</Link>
        <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
          <div>
            <h3 className="mb-0"><span className="text-muted">{project.code}</span> · {project.name}</h3>
            <div className="text-muted small">
              {PROJECT_STATUS_LABEL[project.status]} · Responsable: {project.owner.fullName} · {fmtDate(project.startDate)} – {fmtDate(project.endDate)}
            </div>
          </div>
          <div className="d-flex gap-2">
            {can('PROJECT_EDIT') && <Button variant="outline-primary" onClick={() => setEditing(true)}>Editar proyecto</Button>}
            {can('TASK_CREATE') && <Button className="btn-accent" onClick={() => setTaskModal('new')}>+ Nueva tarea</Button>}
          </div>
        </div>
        <ProgressBar className="mt-2" now={project.progress} label={`${project.progress}%`} />
      </div>
      {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}

      <Tabs defaultActiveKey="board" className="mb-3">
        <Tab eventKey="board" title="Tablero">
          <KanbanBoard board={board} setBoard={setBoard} onOpen={setTaskModal} onError={setError} reload={load}
            canMove={(t) => canEditTask(can, user?.id, t)} />
        </Tab>
        <Tab eventKey="table" title="Tabla"><TaskTable projectId={projectId} people={people} version={version} onOpen={setTaskModal} /></Tab>
        <Tab eventKey="members" title="Miembros"><Members projectId={projectId} people={people} canEdit={can('PROJECT_EDIT')} /></Tab>
        {can('TIME_LOG') && <Tab eventKey="hours" title="Horas"><Hours projectId={projectId} /></Tab>}
      </Tabs>

      {taskModal && (
        <TaskModal projectId={projectId} task={taskModal === 'new' ? undefined : taskModal} people={people}
          onClose={() => setTaskModal(null)} onChanged={load} />
      )}
      {editing && <ProjectForm project={project} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); load(); }} />}
    </>
  );
}

/* ---------------- Kanban con arrastrar y soltar (HTML5 nativo) ---------------- */
function KanbanBoard({ board, setBoard, onOpen, onError, reload, canMove }: {
  board: Board; setBoard: (b: Board) => void; onOpen: (t: Task) => void; onError: (m: string) => void;
  reload: () => void; canMove: (t: Task) => boolean;
}) {
  const [dragId, setDragId] = useState<number | null>(null);
  const [overCol, setOverCol] = useState<TaskStatus | null>(null);
  const [overTask, setOverTask] = useState<number | null>(null);

  const drop = async (status: TaskStatus, beforeId: number | null) => {
    const id = dragId;
    setDragId(null); setOverCol(null); setOverTask(null);
    if (id == null) return;
    const all = board.columns.flatMap((c) => c.tasks);
    const moving = all.find((t) => t.id === id);
    if (!moving || moving.id === beforeId) return;
    // Cálculo local de la nueva posición (índice dentro de la columna destino)
    const target = board.columns.find((c) => c.status === status)!.tasks.filter((t) => t.id !== id);
    const idx = beforeId == null ? target.length : Math.max(0, target.findIndex((t) => t.id === beforeId));
    const previous = board;
    target.splice(idx, 0, { ...moving, status });
    setBoard({
      ...board,
      columns: board.columns.map((c) => c.status === status ? { ...c, tasks: target }
        : { ...c, tasks: c.tasks.filter((t) => t.id !== id) }),
    });
    try { await api.moveTask(id, status, idx); reload(); }
    catch (e) { setBoard(previous); onError(errorText(e)); }
  };

  const start = (e: DragEvent, t: Task) => { setDragId(t.id); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', String(t.id)); };

  return (
    <div className="kb-board">
      {board.columns.map((col) => (
        <div key={col.status} className={`kb-col ${overCol === col.status ? 'over' : ''}`}
          onDragOver={(e) => { if (dragId != null) { e.preventDefault(); setOverCol(col.status); } }}
          onDragLeave={() => setOverCol((c) => (c === col.status ? null : c))}
          onDrop={(e) => { e.preventDefault(); drop(col.status, null); }}>
          <h6><span>{STATUS_LABEL[col.status]}</span><span>{col.tasks.length}</span></h6>
          {col.tasks.map((t) => {
            const movable = canMove(t);
            return (
              <div key={t.id} className={`kb-card ${movable ? '' : 'locked'} ${dragId === t.id ? 'dragging' : ''} ${overTask === t.id ? 'drop-before' : ''}`}
                draggable={movable} onDragStart={(e) => start(e, t)} onDragEnd={() => { setDragId(null); setOverCol(null); setOverTask(null); }}
                onDragOver={(e) => { if (dragId != null) { e.preventDefault(); e.stopPropagation(); setOverTask(t.id); setOverCol(col.status); } }}
                onDrop={(e) => { e.preventDefault(); e.stopPropagation(); drop(col.status, t.id); }}
                onClick={() => onOpen(t)}>
                <div className="d-flex justify-content-between"><span className="kb-key">{t.key}</span><PriorityBadge p={t.priority} /></div>
                <div className="my-1">{t.title}</div>
                <div className="d-flex justify-content-between align-items-center">
                  <small className={t.overdue ? 'text-danger fw-bold' : 'text-muted'}>{t.dueDate ? fmtDate(t.dueDate) : ''}</small>
                  <Avatar user={t.assignee} />
                </div>
                {t.progress > 0 && <ProgressBar className="mt-2" style={{ height: 5 }} now={t.progress} />}
              </div>
            );
          })}
          {col.tasks.length === 0 && <div className="text-muted small text-center py-3">Arrastra tareas aquí</div>}
        </div>
      ))}
    </div>
  );
}

/* ---------------- Tabla filtrable ---------------- */
function TaskTable({ projectId, people, version, onOpen }: { projectId: number; people: UserSummary[]; version: number; onOpen: (t: Task) => void }) {
  const [data, setData] = useState<Paged<Task> | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [assignee, setAssignee] = useState<number | ''>('');
  const [overdue, setOverdue] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    api.tasks(projectId, { page, size: 15, status: status || undefined, assigneeId: assignee === '' ? undefined : assignee, overdue: overdue || undefined })
      .then(setData).catch((e) => setError(errorText(e)));
  }, [projectId, page, status, assignee, overdue, version]);
  return (
    <Card><Card.Body>
      <div className="d-flex flex-wrap gap-2 mb-3">
        <Form.Select style={{ maxWidth: 180 }} value={status} onChange={(e) => { setPage(0); setStatus(e.target.value as TaskStatus | ''); }}>
          <option value="">Todos los estados</option>
          {(Object.keys(STATUS_LABEL) as TaskStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </Form.Select>
        <Form.Select style={{ maxWidth: 220 }} value={assignee} onChange={(e) => { setPage(0); setAssignee(e.target.value === '' ? '' : Number(e.target.value)); }}>
          <option value="">Todos los responsables</option>
          {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
        </Form.Select>
        <Form.Check className="align-self-center" type="switch" label="Solo vencidas" checked={overdue} onChange={(e) => { setPage(0); setOverdue(e.target.checked); }} />
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {!data ? <Loading /> : data.content.length === 0 ? <Empty text="No hay tareas con esos filtros." /> : (
        <div className="table-responsive">
          <Table hover className="table-clickable mb-2">
            <thead><tr><th>Clave</th><th>Título</th><th>Estado</th><th>Prioridad</th><th>Responsable</th><th>Límite</th><th>Avance</th></tr></thead>
            <tbody>
              {data.content.map((t) => (
                <tr key={t.id} onClick={() => onOpen(t)}>
                  <td><strong>{t.key}</strong></td><td>{t.title}</td><td><StatusBadge s={t.status} /></td><td><PriorityBadge p={t.priority} /></td>
                  <td>{t.assignee ? <span className="d-flex align-items-center gap-2"><Avatar user={t.assignee} />{t.assignee.fullName}</span> : '—'}</td>
                  <td className={t.overdue ? 'text-danger fw-bold' : ''}>{fmtDate(t.dueDate)}</td><td style={{ minWidth: 110 }}><ProgressBar now={t.progress} /></td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      )}
      {data && <Pager data={data} onPage={setPage} />}
    </Card.Body></Card>
  );
}

/* ---------------- Miembros ---------------- */
function Members({ projectId, people, canEdit }: { projectId: number; people: UserSummary[]; canEdit: boolean }) {
  const notify = useToast();
  const [members, setMembers] = useState<UserSummary[] | null>(null);
  const [show, setShow] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState('');
  const load = useCallback(() => { api.members(projectId).then(setMembers).catch((e) => setError(errorText(e))); }, [projectId]);
  useEffect(load, [load]);
  const open = () => { setSelected((members ?? []).map((m) => m.id)); setShow(true); };
  const save = async () => {
    try { await api.setMembers(projectId, selected); notify('success', 'Miembros actualizados.'); setShow(false); load(); }
    catch (e) { setError(errorText(e)); }
  };
  return (
    <Card><Card.Body>
      {error && <Alert variant="danger">{error}</Alert>}
      <div className="d-flex justify-content-between mb-2">
        <strong>{members?.length ?? 0} miembros</strong>
        {canEdit && <Button size="sm" variant="outline-primary" onClick={open}>Gestionar miembros</Button>}
      </div>
      {!members ? <Loading /> : members.length === 0 ? <Empty text="Sin miembros." /> : (
        <ul className="list-unstyled mb-0">
          {members.map((m) => <li key={m.id} className="d-flex align-items-center gap-2 py-1"><Avatar user={m} /> {m.fullName} <small className="text-muted">{m.email}</small></li>)}
        </ul>
      )}
      <Modal show={show} onHide={() => setShow(false)} centered scrollable>
        <Modal.Header closeButton><Modal.Title>Miembros del proyecto</Modal.Title></Modal.Header>
        <Modal.Body>
          {people.map((p) => (
            <Form.Check key={p.id} id={`m${p.id}`} label={`${p.fullName} (${p.email})`} checked={selected.includes(p.id)}
              onChange={(e) => setSelected((s) => e.target.checked ? [...s, p.id] : s.filter((x) => x !== p.id))} />
          ))}
        </Modal.Body>
        <Modal.Footer><Button variant="light" onClick={() => setShow(false)}>Cancelar</Button><Button onClick={save}>Guardar</Button></Modal.Footer>
      </Modal>
    </Card.Body></Card>
  );
}

/* ---------------- Horas ---------------- */
function Hours({ projectId }: { projectId: number }) {
  const [s, setS] = useState<TimeSummary | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { api.timeSummary(projectId).then(setS).catch((e) => setError(errorText(e))); }, [projectId]);
  if (error) return <Alert variant="danger">{error}</Alert>;
  if (!s) return <Loading />;
  return (
    <div className="row g-3">
      <div className="col-12"><Card body><div className="text-muted small">Horas totales</div><div className="fs-3 fw-bold">{s.totalHours} h</div></Card></div>
      <div className="col-md-6"><Card body><h6>Por persona</h6>
        {s.byUser.length === 0 ? <Empty text="Sin datos." /> : <Table size="sm"><tbody>{s.byUser.map((u) => <tr key={u.user.id}><td>{u.user.fullName}</td><td className="text-end">{u.hours} h</td></tr>)}</tbody></Table>}
      </Card></div>
      <div className="col-md-6"><Card body><h6>Por tarea</h6>
        {s.byTask.length === 0 ? <Empty text="Sin datos." /> : <Table size="sm"><tbody>{s.byTask.map((t) => <tr key={t.taskKey}><td><strong>{t.taskKey}</strong> {t.title}</td><td className="text-end">{t.hours} h</td></tr>)}</tbody></Table>}
      </Card></div>
    </div>
  );
}
