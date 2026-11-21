import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Alert, Badge, Button, Card, Form, Modal, Table } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { Paged, Role, User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Empty, Loading, Pager } from '../components/Common';
import { useToast } from '../components/Toasts';
import { fmtDateTime, ROLE_LABEL } from '../utils/format';

function UserForm({ user, roles, onClose, onSaved }: { user?: User; roles: Role[]; onClose: () => void; onSaved: () => void }) {
  const notify = useToast();
  const [email, setEmail] = useState(user?.email ?? '');
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [roleId, setRoleId] = useState<number | ''>(user?.role.id ?? roles[roles.length - 1]?.id ?? '');
  const [tmp, setTmp] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (roleId === '') return;
    setBusy(true);
    setError('');
    try {
      if (user) await api.updateUser(user.id, { fullName, roleId });
      else await api.createUser({ email: email.trim(), fullName, roleId, temporaryPassword: tmp });
      notify('success', user ? 'Usuario actualizado.' : 'Usuario creado. Deberá cambiar su contraseña al ingresar.');
      onSaved();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); setTmp(''); }
  };

  return (
    <Modal show onHide={onClose} centered>
      <Form onSubmit={submit}>
        <Modal.Header closeButton><Modal.Title>{user ? 'Editar usuario' : 'Nuevo usuario'}</Modal.Title></Modal.Header>
        <Modal.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group className="mb-3">
            <Form.Label>Correo electrónico</Form.Label>
            <Form.Control type="email" required disabled={!!user} value={email} onChange={(e) => setEmail(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Nombre completo</Form.Label>
            <Form.Control required maxLength={120} value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Rol</Form.Label>
            <Form.Select required value={roleId} onChange={(e) => setRoleId(Number(e.target.value))}>
              {roles.map((r) => <option key={r.id} value={r.id}>{ROLE_LABEL[r.code] ?? r.name}</option>)}
            </Form.Select>
          </Form.Group>
          {!user && (
            <Form.Group className="mb-3">
              <Form.Label>Contraseña temporal (mínimo 8)</Form.Label>
              <Form.Control type="password" required minLength={8} autoComplete="new-password" value={tmp} onChange={(e) => setTmp(e.target.value)} />
              <Form.Text>El usuario deberá cambiarla en su primer ingreso.</Form.Text>
            </Form.Group>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="light" onClick={onClose}>Cancelar</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}

export default function UsersPage() {
  const { user: me } = useAuth();
  const notify = useToast();
  const [data, setData] = useState<Paged<User> | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [q, setQ] = useState('');
  const [active, setActive] = useState<'' | 'true' | 'false'>('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<User | 'new' | null>(null);

  useEffect(() => { api.roles().then(setRoles).catch((e) => setError(errorText(e))); }, []);
  const load = useCallback(() => {
    api.users({ page, size: 15, q: q || undefined, active: active === '' ? undefined : active === 'true' })
      .then(setData).catch((e) => setError(errorText(e)));
  }, [page, q, active]);
  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t); }, [load]);

  const toggle = async (u: User) => {
    try { await api.setUserStatus(u.id, !u.active); notify('success', u.active ? 'Usuario desactivado.' : 'Usuario activado.'); load(); }
    catch (e) { notify('danger', errorText(e)); }
  };

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h3 className="mb-0">Usuarios</h3>
        <Button className="btn-accent" variant="accent" onClick={() => setEditing('new')}>+ Nuevo usuario</Button>
      </div>
      <div className="d-flex flex-wrap gap-2 mb-3">
        <Form.Control style={{ maxWidth: 320 }} placeholder="Buscar por nombre o correo…" value={q} onChange={(e) => { setPage(0); setQ(e.target.value); }} />
        <Form.Select style={{ maxWidth: 200 }} value={active} onChange={(e) => { setPage(0); setActive(e.target.value as '' | 'true' | 'false'); }}>
          <option value="">Todos</option><option value="true">Activos</option><option value="false">Inactivos</option>
        </Form.Select>
      </div>
      {error && <Alert variant="danger">{error}</Alert>}
      {!data ? <Loading /> : (
        <Card><Card.Body>
          {data.content.length === 0 ? <Empty text="Sin resultados." /> : (
            <div className="table-responsive">
              <Table hover>
                <thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Último ingreso</th><th></th></tr></thead>
                <tbody>
                  {data.content.map((u) => (
                    <tr key={u.id}>
                      <td>{u.fullName}</td>
                      <td>{u.email}</td>
                      <td>{ROLE_LABEL[u.role.code] ?? u.role.name}</td>
                      <td><Badge bg={u.active ? 'success' : 'secondary'}>{u.active ? 'Activo' : 'Inactivo'}</Badge></td>
                      <td className="small">{fmtDateTime(u.lastLoginAt)}</td>
                      <td className="text-end text-nowrap">
                        <Button size="sm" variant="outline-primary" className="me-1" onClick={() => setEditing(u)}>Editar</Button>
                        <Button size="sm" variant={u.active ? 'outline-danger' : 'outline-success'} disabled={u.id === me?.id} onClick={() => toggle(u)}>
                          {u.active ? 'Desactivar' : 'Activar'}
                        </Button>
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
      {editing && <UserForm user={editing === 'new' ? undefined : editing} roles={roles} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
    </>
  );
}
