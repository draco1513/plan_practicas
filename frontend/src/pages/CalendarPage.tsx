import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, ButtonGroup, Form, Modal } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { CalendarEvent, Meeting, MeetingResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useDirectory } from '../auth/useDirectory';
import { Loading } from '../components/Common';
import { useToast } from '../components/Toasts';
import { fmtDateTime, fmtTime, toISODate, toLocalInput } from '../utils/format';

const WEEKDAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

export default function CalendarPage() {
  const { can } = useAuth();
  const nav = useNavigate();
  const [cursor, setCursor] = useState(() => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1); });
  const [scope, setScope] = useState<'me' | 'team'>('me');
  const [view, setView] = useState<'month' | 'agenda'>('month');
  const [events, setEvents] = useState<CalendarEvent[] | null>(null);
  const [error, setError] = useState('');
  const [meetingId, setMeetingId] = useState<number | 'new' | null>(null);
  const [newStart, setNewStart] = useState<Date | undefined>();

  // Cuadrícula: de lunes a domingo, 6 semanas
  const days = useMemo(() => {
    const first = new Date(cursor);
    const offset = (first.getDay() + 6) % 7;
    const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);
    return Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
  }, [cursor]);

  const load = useCallback(() => {
    setEvents(null);
    const from = new Date(days[0]); const to = new Date(days[41]); to.setHours(23, 59, 59);
    api.calendar(from.toISOString(), to.toISOString(), scope).then(setEvents).catch((e) => { setError(errorText(e)); setEvents([]); });
  }, [days, scope]);
  useEffect(load, [load]);

  const byDay = useMemo(() => {
    const m = new Map<string, CalendarEvent[]>();
    (events ?? []).forEach((ev) => {
      const k = toISODate(new Date(ev.startAt));
      m.set(k, [...(m.get(k) ?? []), ev]);
    });
    m.forEach((l) => l.sort((a, b) => a.startAt.localeCompare(b.startAt)));
    return m;
  }, [events]);

  const open = (ev: CalendarEvent, e?: { stopPropagation: () => void }) => {
    e?.stopPropagation();
    if (ev.type === 'MEETING') setMeetingId(ev.refId);
    else api.task(ev.refId).then((t) => nav(`/projects/${t.projectId}`)).catch((er) => setError(errorText(er)));
  };

  const title = cursor.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });
  const move = (n: number) => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + n, 1));
  const today = toISODate(new Date());

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h3 className="mb-0 text-capitalize">{title}</h3>
        <div className="d-flex flex-wrap gap-2">
          <ButtonGroup>
            <Button variant="outline-primary" onClick={() => move(-1)}>‹</Button>
            <Button variant="outline-primary" onClick={() => setCursor(new Date(new Date().getFullYear(), new Date().getMonth(), 1))}>Hoy</Button>
            <Button variant="outline-primary" onClick={() => move(1)}>›</Button>
          </ButtonGroup>
          <ButtonGroup>
            <Button variant={view === 'month' ? 'primary' : 'outline-primary'} onClick={() => setView('month')}>Mes</Button>
            <Button variant={view === 'agenda' ? 'primary' : 'outline-primary'} onClick={() => setView('agenda')}>Agenda</Button>
          </ButtonGroup>
          <Form.Select style={{ width: 'auto' }} value={scope} onChange={(e) => setScope(e.target.value as 'me' | 'team')}>
            <option value="me">Mi calendario</option><option value="team">Equipo</option>
          </Form.Select>
          {can('MEETING_CREATE') && <Button className="btn-accent" onClick={() => { setNewStart(undefined); setMeetingId('new'); }}>+ Reunión</Button>}
        </div>
      </div>
      {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}
      {!events ? <Loading /> : view === 'month' ? (
        <div className="cal-grid">
          {WEEKDAYS.map((w) => <div key={w} className="cal-head">{w}</div>)}
          {days.map((d) => {
            const k = toISODate(d);
            const list = byDay.get(k) ?? [];
            return (
              <div key={k} className={`cal-day ${d.getMonth() !== cursor.getMonth() ? 'other' : ''} ${k === today ? 'today' : ''}`}
                onClick={() => { if (can('MEETING_CREATE')) { const s = new Date(d); s.setHours(9, 0, 0, 0); setNewStart(s); setMeetingId('new'); } }}>
                <span className="num">{d.getDate()}</span>
                {list.slice(0, 3).map((ev) => (
                  <button key={`${ev.type}${ev.refId}`} className={`cal-ev ${ev.type === 'TASK_DUE' ? 'task' : ''}`} onClick={(e) => open(ev, e)}
                    title={ev.title}>{ev.allDay ? '' : fmtTime(ev.startAt) + ' '}{ev.title}</button>
                ))}
                {list.length > 3 && <div className="cal-more">+{list.length - 3} más</div>}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="card card-body">
          {[...byDay.entries()].filter(([k]) => k.slice(0, 7) === toISODate(cursor).slice(0, 7)).sort(([a], [b]) => a.localeCompare(b)).map(([k, list]) => (
            <div key={k} className="mb-3">
              <div className="agenda-day">{new Date(`${k}T12:00:00`).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' })}</div>
              {list.map((ev) => (
                <button key={`${ev.type}${ev.refId}`} className="btn btn-link d-block text-start p-0 py-1" onClick={() => open(ev)}>
                  {ev.type === 'TASK_DUE' ? '📌 Vence: ' : '🗓 '}{ev.allDay ? '' : fmtTime(ev.startAt) + ' · '}{ev.title}
                </button>
              ))}
            </div>
          ))}
          {byDay.size === 0 && <div className="text-muted text-center py-4">Sin eventos este mes.</div>}
        </div>
      )}
      {meetingId !== null && <MeetingModal id={meetingId} start={newStart} onClose={() => setMeetingId(null)} onChanged={() => { setMeetingId(null); load(); }} />}
    </>
  );
}

function MeetingModal({ id, start, onClose, onChanged }: { id: number | 'new'; start?: Date; onClose: () => void; onChanged: () => void }) {
  const { user, can } = useAuth();
  const people = useDirectory();
  const notify = useToast();
  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [loading, setLoading] = useState(id !== 'new');
  const [title, setTitle] = useState('');
  const [startAt, setStartAt] = useState(toLocalInput(start ?? new Date(new Date().setMinutes(0, 0, 0))));
  const [endAt, setEndAt] = useState(toLocalInput(new Date((start ?? new Date(new Date().setMinutes(0, 0, 0))).getTime() + 3600000)));
  const [location, setLocation] = useState('');
  const [invitees, setInvitees] = useState<number[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(id === 'new');

  useEffect(() => {
    if (id === 'new') return;
    api.meeting(id).then((m) => {
      setMeeting(m); setTitle(m.title); setStartAt(toLocalInput(m.startAt)); setEndAt(toLocalInput(m.endAt));
      setLocation(m.location ?? ''); setInvitees(m.invitees.map((i) => i.user.id));
    }).catch((e) => setError(errorText(e))).finally(() => setLoading(false));
  }, [id]);

  const isOrganizer = !meeting || meeting.organizer.id === user?.id;
  const myInvite = meeting?.invitees.find((i) => i.user.id === user?.id);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setBusy(true); setError('');
    const body = { title, startAt: new Date(startAt).toISOString(), endAt: new Date(endAt).toISOString(), location: location || undefined, inviteeIds: invitees };
    try {
      if (id === 'new') await api.createMeeting(body); else await api.updateMeeting(id, body);
      notify('success', 'Reunión guardada. Se notificó a los invitados.');
      onChanged();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };
  const respond = async (r: Exclude<MeetingResponse, 'PENDING'>) => {
    if (id === 'new') return;
    try { setMeeting(await api.respondMeeting(id, r)); notify('success', r === 'ACCEPTED' ? 'Asistencia confirmada.' : 'Has rechazado la reunión.'); }
    catch (err) { setError(errorText(err)); }
  };
  const cancel = async () => {
    if (id === 'new' || !window.confirm('¿Cancelar esta reunión?')) return;
    try { await api.cancelMeeting(id); notify('success', 'Reunión cancelada.'); onChanged(); } catch (err) { setError(errorText(err)); }
  };

  return (
    <Modal show onHide={onClose} centered scrollable>
      <Modal.Header closeButton><Modal.Title>{id === 'new' ? 'Nueva reunión' : meeting?.title ?? 'Reunión'}</Modal.Title></Modal.Header>
      <Modal.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        {loading ? <Loading /> : !editing && meeting ? (
          <>
            <p className="mb-1">🗓 {fmtDateTime(meeting.startAt)} → {fmtDateTime(meeting.endAt)}</p>
            {meeting.location && <p className="mb-1">📍 {meeting.location}</p>}
            <p className="text-muted small">Organiza: {meeting.organizer.fullName}</p>
            <strong>Invitados</strong>
            <ul className="mb-3">{meeting.invitees.map((i) => <li key={i.user.id}>{i.user.fullName} — <em>{{ PENDING: 'pendiente', ACCEPTED: 'aceptó', DECLINED: 'rechazó' }[i.response]}</em></li>)}</ul>
            {myInvite && can('MEETING_RESPOND') && (
              <div className="d-flex gap-2">
                <Button variant={myInvite.response === 'ACCEPTED' ? 'success' : 'outline-success'} onClick={() => respond('ACCEPTED')}>Aceptar</Button>
                <Button variant={myInvite.response === 'DECLINED' ? 'danger' : 'outline-danger'} onClick={() => respond('DECLINED')}>Rechazar</Button>
              </div>
            )}
          </>
        ) : (
          <Form id="meeting-form" onSubmit={submit}>
            <Form.Group className="mb-3"><Form.Label>Título</Form.Label><Form.Control required maxLength={150} value={title} onChange={(e) => setTitle(e.target.value)} /></Form.Group>
            <div className="row">
              <Form.Group className="col-6 mb-3"><Form.Label>Inicio</Form.Label><Form.Control type="datetime-local" required value={startAt} onChange={(e) => setStartAt(e.target.value)} /></Form.Group>
              <Form.Group className="col-6 mb-3"><Form.Label>Fin</Form.Label><Form.Control type="datetime-local" required min={startAt} value={endAt} onChange={(e) => setEndAt(e.target.value)} /></Form.Group>
            </div>
            <Form.Group className="mb-3"><Form.Label>Lugar o enlace</Form.Label><Form.Control maxLength={200} value={location} onChange={(e) => setLocation(e.target.value)} /></Form.Group>
            <Form.Label>Invitados</Form.Label>
            <div style={{ maxHeight: 170, overflowY: 'auto' }} className="border rounded p-2">
              {people.filter((p) => p.id !== user?.id).map((p) => (
                <Form.Check key={p.id} id={`i${p.id}`} label={p.fullName} checked={invitees.includes(p.id)}
                  onChange={(e) => setInvitees((s) => e.target.checked ? [...s, p.id] : s.filter((x) => x !== p.id))} />
              ))}
            </div>
          </Form>
        )}
      </Modal.Body>
      <Modal.Footer>
        {meeting && isOrganizer && !editing && <><Button variant="outline-danger" onClick={cancel}>Cancelar reunión</Button><Button variant="outline-primary" onClick={() => setEditing(true)}>Editar</Button></>}
        {editing && <Button type="submit" form="meeting-form" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</Button>}
        <Button variant="light" onClick={onClose}>Cerrar</Button>
      </Modal.Footer>
    </Modal>
  );
}
