import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Alert, Button, Form, Modal, Tab, Tabs } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Attachment, Comment, Task, TaskPriority, TimeEntry, UserSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Empty, PriorityBadge, StatusBadge } from './Common';
import { useToast } from './Toasts';
import { fmtDate, fmtDateTime, fmtSize, PRIORITY_LABEL, toISODate } from '../utils/format';

/** Puede editar la tarea quien tenga TASK_UPDATE_ANY, o TASK_UPDATE_OWN si es su responsable. */
export const canEditTask = (can: (p: string) => boolean, userId: number | undefined, t: Task) =>
  can('TASK_UPDATE_ANY') || (can('TASK_UPDATE_OWN') && t.assignee?.id === userId);

/** Crear tarea nueva (task undefined) o ver/editar una existente, con comentarios, adjuntos y horas. */
export default function TaskModal({ projectId, task, people, onClose, onChanged }: {
  projectId: number; task?: Task; people: UserSummary[]; onClose: () => void; onChanged: () => void;
}) {
  const { can, user } = useAuth();
  const notify = useToast();
  const [current, setCurrent] = useState<Task | undefined>(task);
  const [title, setTitle] = useState(task?.title ?? '');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM');
  const [due, setDue] = useState(task?.dueDate ?? '');
  const [assigneeId, setAssigneeId] = useState<number | ''>(task?.assignee?.id ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const editable = !current || canEditTask(can, user?.id, current);
  const canAssign = can('TASK_ASSIGN');

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      if (!current) {
        await api.createTask(projectId, { title, priority, dueDate: due || undefined, assigneeId: assigneeId === '' ? null : assigneeId });
        notify('success', 'Tarea creada.');
      } else {
        let t = await api.updateTask(current.id, { title, priority, dueDate: due || null });
        if (canAssign && (t.assignee?.id ?? '') !== assigneeId) t = await api.assignTask(t.id, assigneeId === '' ? null : assigneeId);
        setCurrent(t);
        notify('success', 'Tarea actualizada.');
      }
      onChanged();
      if (!current) onClose();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  const setProgress = async (value: number) => {
    if (!current) return;
    try { setCurrent(await api.setProgress(current.id, value)); onChanged(); }
    catch (err) { setError(errorText(err)); }
  };

  return (
    <Modal show onHide={onClose} size="lg" centered scrollable>
      <Modal.Header closeButton>
        <Modal.Title>
          {current ? <><span className="text-muted me-2">{current.key}</span>{current.title}</> : 'Nueva tarea'}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}
        <Tabs defaultActiveKey="info" className="mb-3">
          <Tab eventKey="info" title="Detalle">
            <Form onSubmit={save}>
              <Form.Group className="mb-3">
                <Form.Label>Título</Form.Label>
                <Form.Control required maxLength={200} disabled={!editable} value={title} onChange={(e) => setTitle(e.target.value)} />
              </Form.Group>
              <div className="row">
                <Form.Group className="col-md-4 mb-3">
                  <Form.Label>Prioridad</Form.Label>
                  <Form.Select disabled={!editable} value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
                    {(Object.keys(PRIORITY_LABEL) as TaskPriority[]).map((p) => <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>)}
                  </Form.Select>
                </Form.Group>
                <Form.Group className="col-md-4 mb-3">
                  <Form.Label>Fecha límite</Form.Label>
                  <Form.Control type="date" disabled={!editable} value={due} onChange={(e) => setDue(e.target.value)} />
                </Form.Group>
                <Form.Group className="col-md-4 mb-3">
                  <Form.Label>Responsable</Form.Label>
                  <Form.Select disabled={!canAssign} value={assigneeId} onChange={(e) => setAssigneeId(e.target.value === '' ? '' : Number(e.target.value))}>
                    <option value="">Sin asignar</option>
                    {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                  </Form.Select>
                </Form.Group>
              </div>
              {current && (
                <div className="mb-3">
                  <div className="d-flex gap-2 mb-2 align-items-center">
                    <StatusBadge s={current.status} /> <PriorityBadge p={current.priority} />
                    {current.overdue && <span className="badge bg-danger">Vencida</span>}
                    <small className="text-muted ms-auto">Creada por {current.reporter.fullName} · {fmtDateTime(current.createdAt)}</small>
                  </div>
                  <Form.Label>Avance: {current.progress}%</Form.Label>
                  <Form.Range min={0} max={100} step={5} disabled={!editable} value={current.progress}
                    onChange={(e) => setCurrent({ ...current, progress: Number(e.target.value) })}
                    onMouseUp={(e) => setProgress(Number((e.target as HTMLInputElement).value))}
                    onTouchEnd={(e) => setProgress(Number((e.target as HTMLInputElement).value))}
                    onKeyUp={(e) => setProgress(Number((e.target as HTMLInputElement).value))} />
                </div>
              )}
              {(editable || canAssign) && <Button type="submit" disabled={busy}>{busy ? 'Guardando…' : current ? 'Guardar cambios' : 'Crear tarea'}</Button>}
            </Form>
          </Tab>
          {current && <Tab eventKey="comments" title="Comentarios"><Comments taskId={current.id} /></Tab>}
          {current && <Tab eventKey="files" title="Adjuntos"><Attachments taskId={current.id} /></Tab>}
          {current && can('TIME_LOG') && <Tab eventKey="time" title="Horas"><TimeLog task={current} /></Tab>}
        </Tabs>
      </Modal.Body>
    </Modal>
  );
}

function Comments({ taskId }: { taskId: number }) {
  const { can } = useAuth();
  const [list, setList] = useState<Comment[] | null>(null);
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const load = useCallback(() => { api.comments(taskId).then(setList).catch((e) => setError(errorText(e))); }, [taskId]);
  useEffect(load, [load]);
  const send = async (e: FormEvent) => {
    e.preventDefault();
    try { await api.addComment(taskId, body); setBody(''); load(); } catch (err) { setError(errorText(err)); }
  };
  return (
    <>
      {error && <Alert variant="danger">{error}</Alert>}
      {list && list.length === 0 && <Empty text="Sin comentarios." />}
      {list?.map((c) => (
        <div className="comment" key={c.id}>
          <div className="small"><strong>{c.author.fullName}</strong> <span className="text-muted">· {fmtDateTime(c.createdAt)}</span></div>
          <div style={{ whiteSpace: 'pre-wrap' }}>{c.body}</div>
        </div>
      ))}
      {can('COMMENT_CREATE') && (
        <Form onSubmit={send} className="d-flex gap-2 mt-2">
          <Form.Control required maxLength={2000} placeholder="Escribe un comentario…" value={body} onChange={(e) => setBody(e.target.value)} />
          <Button type="submit">Enviar</Button>
        </Form>
      )}
    </>
  );
}

function Attachments({ taskId }: { taskId: number }) {
  const [list, setList] = useState<Attachment[] | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(() => { api.attachments(taskId).then(setList).catch((e) => setError(errorText(e))); }, [taskId]);
  useEffect(load, [load]);
  const upload = async (file?: File) => {
    if (!file) return;
    try { await api.uploadAttachment(taskId, file); load(); } catch (err) { setError(errorText(err)); }
  };
  return (
    <>
      {error && <Alert variant="danger">{error}</Alert>}
      {list && list.length === 0 && <Empty text="Sin adjuntos." />}
      <ul className="list-unstyled">
        {list?.map((a) => (
          <li key={a.id} className="d-flex justify-content-between border-bottom py-2">
            <span>
              <Button variant="link" className="p-0" onClick={() => api.downloadAttachment(a).catch((e) => setError(errorText(e)))}>{a.fileName}</Button>
              <small className="text-muted ms-2">{fmtSize(a.sizeBytes)} · {a.uploadedBy.fullName}</small>
            </span>
            <small className="text-muted">{fmtDateTime(a.createdAt)}</small>
          </li>
        ))}
      </ul>
      <Form.Control type="file" onChange={(e) => { upload((e.target as HTMLInputElement).files?.[0]); (e.target as HTMLInputElement).value = ''; }} />
      <Form.Text>Tamaño máximo según la configuración del servidor (por defecto 10 MB).</Form.Text>
    </>
  );
}

function TimeLog({ task }: { task: Task }) {
  const { user } = useAuth();
  const [rows, setRows] = useState<TimeEntry[]>([]);
  const [date, setDate] = useState(toISODate(new Date()));
  const [hours, setHours] = useState('1');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const load = useCallback(() => {
    api.timeEntries({ taskId: task.id, size: 50 }).then((p) => setRows(p.content)).catch((e) => setError(errorText(e)));
  }, [task.id]);
  useEffect(load, [load]);
  const add = async (e: FormEvent) => {
    e.preventDefault(); setError('');
    try { await api.logTime({ taskId: task.id, workDate: date, hours: Number(hours), note: note || undefined }); setNote(''); load(); }
    catch (err) { setError(errorText(err)); }
  };
  const total = rows.reduce((s, r) => s + r.hours, 0);
  return (
    <>
      {error && <Alert variant="danger">{error}</Alert>}
      <Form onSubmit={add} className="row g-2 mb-3">
        <div className="col-md-3"><Form.Control type="date" required value={date} onChange={(e) => setDate(e.target.value)} /></div>
        <div className="col-md-2"><Form.Control type="number" required min={0.25} max={24} step={0.25} value={hours} onChange={(e) => setHours(e.target.value)} /></div>
        <div className="col-md-5"><Form.Control placeholder="Nota (opcional)" maxLength={300} value={note} onChange={(e) => setNote(e.target.value)} /></div>
        <div className="col-md-2"><Button type="submit" className="w-100">Registrar</Button></div>
      </Form>
      {rows.length === 0 ? <Empty text="Sin horas registradas." /> : (
        <table className="table table-sm">
          <thead><tr><th>Fecha</th><th>Persona</th><th>Horas</th><th>Nota</th><th></th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{fmtDate(r.workDate)}</td><td>{r.user.fullName}</td><td>{r.hours}</td><td>{r.note}</td>
                <td className="text-end">{r.user.id === user?.id && <Button size="sm" variant="outline-danger" onClick={() => api.deleteTime(r.id).then(load).catch((e) => setError(errorText(e)))}>×</Button>}</td>
              </tr>
            ))}
          </tbody>
          <tfoot><tr><th colSpan={2}>Total</th><th colSpan={3}>{total} h</th></tr></tfoot>
        </table>
      )}
    </>
  );
}

