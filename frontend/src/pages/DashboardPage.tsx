import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Card, Col, Form, ProgressBar, Row, Table } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Dashboard, Project } from '../api/types';
import { Empty, Loading, PriorityBadge } from '../components/Common';
import { fmtDate, STATUS_LABEL } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

const COLORS = { TODO: '#8a90ad', IN_PROGRESS: '#3D5AFE', DONE: '#7ccf00' } as const;

export default function DashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectId, setProjectId] = useState<number | undefined>();
  const [error, setError] = useState('');

  useEffect(() => { api.projects({ size: 100 }).then((p) => setProjects(p.content)).catch(() => {}); }, []);
  useEffect(() => {
    setData(null);
    api.dashboard(projectId).then(setData).catch((e) => setError(errorText(e)));
  }, [projectId]);

  if (error) return <Alert variant="danger">{error}</Alert>;
  if (!data) return <Loading />;

  const total = data.tasksByStatus.reduce((s, x) => s + x.count, 0);

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <div>
          <h3 className="mb-0">Hola, {user?.fullName.split(' ')[0]}</h3>
          <small className="text-muted">Resumen de tareas {projectId ? 'del proyecto' : 'de tus proyectos'}</small>
        </div>
        <Form.Select style={{ maxWidth: 280 }} value={projectId ?? ''} onChange={(e) => setProjectId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Todos los proyectos</option>
          {projects.map((p) => <option key={p.id} value={p.id}>{p.code} · {p.name}</option>)}
        </Form.Select>
      </div>

      <Row className="g-3 mb-3">
        {data.tasksByStatus.map((s) => (
          <Col xs={6} md={3} key={s.status}>
            <Card><Card.Body>
              <div className="text-muted small text-uppercase">{STATUS_LABEL[s.status]}</div>
              <div className="fs-2 fw-bold">{s.count}</div>
              <ProgressBar className="stat-bar"><ProgressBar now={total ? (s.count / total) * 100 : 0} style={{ backgroundColor: COLORS[s.status] }} /></ProgressBar>
            </Card.Body></Card>
          </Col>
        ))}
        <Col xs={6} md={3}>
          <Card className="border-danger"><Card.Body>
            <div className="text-muted small text-uppercase">Vencidas</div>
            <div className="fs-2 fw-bold text-danger">{data.overdueTasks.length}</div>
            <small className="text-muted">requieren atención</small>
          </Card.Body></Card>
        </Col>
      </Row>

      <Row className="g-3">
        <Col lg={6}>
          <Card className="h-100"><Card.Body>
            <Card.Title as="h6">Avance por proyecto</Card.Title>
            {data.tasksByProject.length === 0 ? <Empty text="Sin proyectos" /> : data.tasksByProject.map((p) => (
              <div key={p.projectId} className="mb-3">
                <div className="d-flex justify-content-between small">
                  <Link to={`/projects/${p.projectId}`} className="fw-semibold text-decoration-none">{p.projectName}</Link>
                  <span>{p.done}/{p.total} tareas · {p.progress}%</span>
                </div>
                <ProgressBar className="stat-bar" now={p.progress} />
              </div>
            ))}
          </Card.Body></Card>
        </Col>
        <Col lg={6}>
          <Card className="h-100"><Card.Body>
            <Card.Title as="h6">Tareas por responsable</Card.Title>
            {data.tasksByAssignee.length === 0 ? <Empty text="Sin datos" /> : data.tasksByAssignee.map((a) => {
              const t = a.todo + a.inProgress + a.done;
              return (
                <div key={a.user.id} className="mb-3">
                  <div className="d-flex justify-content-between small"><span className="fw-semibold">{a.user.fullName}</span><span>{t} tareas</span></div>
                  <ProgressBar className="stat-bar">
                    <ProgressBar now={t ? (a.todo / t) * 100 : 0} style={{ backgroundColor: COLORS.TODO }} title="Por hacer" />
                    <ProgressBar now={t ? (a.inProgress / t) * 100 : 0} style={{ backgroundColor: COLORS.IN_PROGRESS }} title="En curso" />
                    <ProgressBar now={t ? (a.done / t) * 100 : 0} style={{ backgroundColor: COLORS.DONE }} title="Hechas" />
                  </ProgressBar>
                </div>
              );
            })}
            <div className="small d-flex gap-3 text-muted">
              {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map((s) => (
                <span key={s}><span className="d-inline-block rounded-circle me-1" style={{ width: 10, height: 10, background: COLORS[s] }} />{STATUS_LABEL[s]}</span>
              ))}
            </div>
          </Card.Body></Card>
        </Col>
        <Col xs={12}>
          <Card><Card.Body>
            <Card.Title as="h6">Tareas vencidas</Card.Title>
            {data.overdueTasks.length === 0 ? <Empty text="Nada vencido 🎉" /> : (
              <div className="table-responsive">
                <Table size="sm" hover className="mb-0">
                  <thead><tr><th>Clave</th><th>Título</th><th>Responsable</th><th>Prioridad</th><th>Vence</th></tr></thead>
                  <tbody>
                    {data.overdueTasks.map((t) => (
                      <tr key={t.id}>
                        <td><Link to={`/projects/${t.projectId}`}>{t.key}</Link></td>
                        <td>{t.title}</td>
                        <td>{t.assignee?.fullName ?? '—'}</td>
                        <td><PriorityBadge p={t.priority} /></td>
                        <td className="text-danger">{fmtDate(t.dueDate)}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            )}
          </Card.Body></Card>
        </Col>
      </Row>
    </>
  );
}
