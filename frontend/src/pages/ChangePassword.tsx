import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Form } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/Toasts';
import { AuthCard } from './Login';

/** Cambio de contraseña: obligatorio en el primer ingreso (mustChangePassword) o voluntario. */
export default function ChangePassword() {
  const { mustChangePassword, passwordChanged, logout } = useAuth();
  const notify = useToast();
  const nav = useNavigate();
  const [cur, setCur] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (pw !== pw2) { setError('Las contraseñas nuevas no coinciden.'); return; }
    setBusy(true);
    setError('');
    try {
      await api.changePassword(cur, pw);
      passwordChanged();
      notify('success', 'Contraseña actualizada.');
      nav('/', { replace: true });
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); setCur(''); setPw(''); setPw2(''); }
  };

  return (
    <AuthCard title={mustChangePassword ? 'Debes cambiar tu contraseña' : 'Cambiar contraseña'}>
      {mustChangePassword && <Alert variant="info">Por seguridad, cambia la contraseña temporal antes de continuar.</Alert>}
      {error && <Alert variant="danger">{error}</Alert>}
      <Form onSubmit={submit}>
        <Form.Group className="mb-3">
          <Form.Label>Contraseña actual</Form.Label>
          <Form.Control type="password" required autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} />
        </Form.Group>
        <Form.Group className="mb-3">
          <Form.Label>Nueva contraseña (mínimo 8 caracteres)</Form.Label>
          <Form.Control type="password" required minLength={8} autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
        </Form.Group>
        <Form.Group className="mb-3">
          <Form.Label>Repite la nueva contraseña</Form.Label>
          <Form.Control type="password" required minLength={8} autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} />
        </Form.Group>
        <Button type="submit" className="w-100" disabled={busy}>{busy ? 'Guardando…' : 'Cambiar contraseña'}</Button>
        <div className="text-center mt-3">
          {mustChangePassword
            ? <Button variant="link" onClick={() => { logout(); nav('/login'); }}>Cerrar sesión</Button>
            : <Button variant="link" onClick={() => nav(-1)}>Cancelar</Button>}
        </div>
      </Form>
    </AuthCard>
  );
}
