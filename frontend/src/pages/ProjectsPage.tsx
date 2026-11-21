import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Modal, ProgressBar, Table } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Paged, Project, ProjectStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useDirectory } from '../auth/useDirectory';
import { Empty, Loading, Pager } from '../components/Common';
import { useToast } from '../components/Toasts';
import { fmtDate, PROJECT_STATUS_LABEL } from '../utils/format';

/** Formulario para crear o editar un proyecto (en un modal). */
export function ProjectForm({ project, onClose, onSaved }: { project?: Project; onClose: () => void; onSaved: () => void }) {
  const people = useDirectory();
  const notify = useToast();
  const { user } = useAuth();
  const [code, setCode] = useState(project?.code ?? '');
  const [name, setName] = useState(project?.name ?? '');
  const [ownerId, setOwnerId] = useState<number | ''>(project?.owner.id ?? user?.id ?? '');
  const [status, setStatus] = useState<ProjectStatus>(project?.status ?? 'PLANNED');
  const [start, setStart] = useState(project?.startDate ?? '');
  const [end, setEnd] = useState(project?.endDate ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (ownerId === '') return;
    setBusy(true);
    setError('');
    try {
      if (project) {
        await api.updateProject(project.id, { name, ownerId, status, startDate: start || undefined, endDate: end || undefined });
      } else {
        await api.createProject({ code: code.toUpperCase(), name, ownerId, startDate: start || undefined, endDate: end || undefined });
      }
      notify('success', project ? 'Proyecto actualizado.' : 'Proyecto creado.');
      onSaved();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  return (
    <Modal show onHide={onClose} centered>
      <Form onSubmit={submit}>
        <Modal.Header closeButton><Modal.Title>{project ? 'Editar proyecto' : 'Nuevo proyecto'}</Modal.Title></Modal.Header>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          {!project && (
            <Form.Group className="mb-3">
              <Form.Label>Código (2 a 10 letras mayúsculas)</Form.Label>
              <Form.Control required pattern="[A-Za-z]{2,10}" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="TF" />
            </Form.Group>
          )}
          <Form.Group className="mb-3">
            <Form.Label>Nombre</Form.Label>
            <Form.Control required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Responsable</Form.Label>
            <Form.Select required value={ownerId} onChange={(e) => setOwnerId(Number(e.target.value))}>
              <option value="" disabled>Selecciona…</option>
              {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
              {project && !people.some((p) => p.id === project.owner.id) && <option value={project.owner.id}>{project.owner.fullName}</option>}
            </Form.Select>
          </Form.Group>
          {project && (
            <Form.Group className="mb-3">
              <Form.Label>Estado</Form.Label>
              <Form.Select value={status} onChange={(e) => setStatus(e.target.value as ProjectStatus)}>
                {(Object.keys(PROJECT_STATUS_LABEL) as ProjectStatus[]).map((s) => <option key={s} value={s}>{PROJECT_STATUS_LABEL[s]}</option>)}
              </Form.Select>
            </Form.Group>
          )}
          <div className="row">
            <Form.Group className="col-6 mb-3"><Form.Label>Inicio</Form.Label><Form.Control type="date" value={start} onChange={(e) => setStart(e.target.value)} /></Form.Group>
            <Form.Group className="col-6 mb-3"><Form.Label>Fin</Form.Label><Form.Control type="date" value={end} min={start} onChange={(e) => setEnd(e.target.value)} /></Form.Group>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="light" onClick={onClose}>Cancelar</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

export default function ProjectsPage() {
  const { can } = useAuth();
  const nav = useNavigate();
  const [data, setData] = useState<Paged<Project> | null>(null);
  const [status, setStatus] = useState<ProjectStatus | ''>('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<Project | 'new' | null>(null);

  const load = useCallback(() => {
    api.projects({ page, size: 12, status: status || undefined }).then(setData).catch((e) => setError(errorText(e)));
  }, [page, status]);
  useEffect(load, [load]);

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h3 className="mb-0">Proyectos</h3>
        <div className="d-flex gap-2">
          <Form.Select value={status} onChange={(e) => { setPage(0); setStatus(e.target.value as ProjectStatus | ''); }}>
            <option value="">Todos los estados</option>
            {(Object.keys(PROJECT_STATUS_LABEL) as ProjectStatus[]).map((s) => <option key={s} value={s}>{PROJECT_STATUS_LABEL[s]}</option>)}
          </Form.Select>
          {can('PROJECT_CREATE') && <Button variant="accent" className="btn-accent text-nowrap" onClick={() => setEditing('new')}>+ Nuevo proyecto</Button>}
        </div>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {!data ? <Loading /> : (
        <Card><Card.Body>
          {data.content.length === 0 ? <Empty text="No hay proyectos todavía." /> : (
            <div className="table-responsive">
              <Table hover className="table-clickable mb-2">
                <thead><tr><th>Código</th><th>Nombre</th><th>Responsable</th><th>Estado</th><th>Fechas</th><th style={{ minWidth: 150 }}>Avance</th><th></th></tr></thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id} onClick={() => nav(`/projects/${p.id}`)}>
                      <td><strong>{p.code}</strong></td>
                      <td>{p.name}<div className="small text-muted">{p.taskCount} tareas</div></td>
                      <td>{p.owner.fullName}</td>
                      <td>{PROJECT_STATUS_LABEL[p.status]}</td>
                      <td className="small">{fmtDate(p.startDate)} – {fmtDate(p.endDate)}</td>
                      <td><ProgressBar now={p.progress} label={`${p.progress}%`} /></td>
                      <td className="text-end">
                        {can('PROJECT_EDIT') && <Button size="sm" variant="outline-primary" onClick={(e) => { e.stopPropagation(); setEditing(p); }}>Editar</Button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
          <Pager data={data} onPage={setPage} />
        </Card.Body></Card>
      )}
      {editing && <ProjectForm project={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
    </>
  );
}
