import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Col, Form, ListGroup, Row } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Notification, NotificationPreferences, Paged } from '../api/types';
import { Empty, Loading, Pager } from '../components/Common';
import { useToast } from '../components/Toasts';
import { fmtDateTime } from '../utils/format';

const changed = () => window.dispatchEvent(new Event('tf:notifications-changed'));

function Preferences() {
  const notify = useToast();
  const [p, setP] = useState<NotificationPreferences | null>(null);
  useEffect(() => { api.preferences().then(setP).catch((e) => notify('danger', errorText(e))); }, [notify]);
  if (!p) return <Loading />;
  const save = async (e: FormEvent) => {
    e.preventDefault();
    try { setP(await api.savePreferences(p)); notify('success', 'Preferencias guardadas.'); }
    catch (err) { notify('danger', errorText(err)); }
  };
  return (
    <Card><Card.Body>
      <Card.Title as="h6">Preferencias</Card.Title>
      <Form onSubmit={save}>
        <Form.Check type="switch" id="pref-email" className="mb-3" label="Recibir también por correo" checked={p.emailEnabled} onChange={(e) => setP({ ...p, emailEnabled: e.target.checked })} />
        <Form.Group className="mb-3">
          <Form.Label>Recordar reuniones (minutos antes)</Form.Label>
          <Form.Control type="number" min={0} value={p.meetingReminderMin} onChange={(e) => setP({ ...p, meetingReminderMin: Number(e.target.value) })} />
        </Form.Group>
        <Form.Group className="mb-3">
          <Form.Label>Recordar tareas por vencer (horas antes)</Form.Label>
          <Form.Control type="number" min={0} value={p.taskDueReminderHrs} onChange={(e) => setP({ ...p, taskDueReminderHrs: Number(e.target.value) })} />
        </Form.Group>
        <Button type="submit" size="sm">Guardar</Button>
      </Form>
    </Card.Body></Card>
  );
}

export default function NotificationsPage() {
  const nav = useNavigate();
  const [data, setData] = useState<Paged<Notification> | null>(null);
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.notifications({ page, size: 15, unreadOnly }).then(setData).catch((e) => setError(errorText(e)));
  }, [page, unreadOnly]);
  useEffect(load, [load]);

  const open = async (n: Notification) => {
    if (!n.read) { await api.markRead(n.id).catch(() => {}); changed(); load(); }
    const pid = n.payload?.projectId;
    const meeting = n.payload?.meetingId;
    if (typeof pid === 'number') nav(`/projects/${pid}`);
    else if (typeof meeting === 'number') nav('/calendar');
  };
  const readAll = async () => { await api.markAllRead().catch(() => {}); changed(); load(); };

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h3 className="mb-0">Notificaciones</h3>
        <div className="d-flex align-items-center gap-3">
          <Form.Check type="switch" id="unread" label="Solo sin leer" checked={unreadOnly} onChange={(e) => { setPage(0); setUnreadOnly(e.target.checked); }} />
          <Button size="sm" variant="outline-primary" onClick={readAll}>Marcar todas como leídas</Button>
        </div>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      <Row className="g-3">
        <Col lg={8}>
          {!data ? <Loading /> : (
            <Card><Card.Body>
              {data.content.length === 0 ? <Empty text="No tienes notificaciones." /> : (
                <ListGroup variant="flush">
                  {data.content.map((n) => (
                    <ListGroup.Item key={n.id} action onClick={() => open(n)} className="d-flex gap-3 align-items-start">
                      <span className="rounded-circle mt-2 flex-shrink-0" style={{ width: 10, height: 10, background: n.read ? '#d0d4e6' : '#3D5AFE' }} />
                      <div className="flex-grow-1">
                        <div className={n.read ? '' : 'fw-bold'}>{n.title}</div>
                        <div className="small text-muted">{n.body}</div>
                        <div className="small text-muted">{fmtDateTime(n.createdAt)}</div>
                      </div>
                    </ListGroup.Item>
                  ))}
                </ListGroup>
              )}
              <div className="mt-2"><Pager data={data} onPage={setPage} /></div>
            </Card.Body></Card>
          )}
        </Col>
        <Col lg={4}><Preferences /></Col>
      </Row>
    </>
  );
}
