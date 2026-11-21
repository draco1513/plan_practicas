import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Form } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import { AuthCard } from './Login';

/** Solicita el enlace de recuperación. La API responde 202 sin revelar si el correo existe. */
export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try { await api.forgotPassword(email.trim()); setSent(true); }
    catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  return (
    <AuthCard title="Recuperar contraseña">
      {sent ? (
        <Alert variant="success">Si el correo está registrado, recibirás un enlace para crear una nueva contraseña.</Alert>
      ) : (
        <Form onSubmit={submit}>
          {error && <Alert variant="danger">{error}</Alert>}
          <p className="text-muted">Escribe tu correo y te enviaremos un enlace de recuperación.</p>
          <Form.Group className="mb-3">
            <Form.Label>Correo electrónico</Form.Label>
            <Form.Control type="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </Form.Group>
          <Button type="submit" className="w-100" disabled={busy}>{busy ? 'Enviando…' : 'Enviar enlace'}</Button>
        </Form>
      )}
      <div className="text-center mt-3"><Link to="/login">Volver a iniciar sesión</Link></div>
    </AuthCard>
  );
}

/** Pantalla a la que llega el enlace del correo: /reset-password?token=... */
export function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const nav = useNavigate();
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (pw !== pw2) { setError('Las contraseñas no coinciden.'); return; }
    setBusy(true);
    setError('');
    try {
      await api.resetPassword(token, pw);
      setDone(true);
      setTimeout(() => nav('/login'), 2500);
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); setPw(''); setPw2(''); }
  };

  if (!token) return <AuthCard title="Enlace no válido"><Alert variant="warning">Falta el token de recuperación. Solicita un nuevo enlace.</Alert><Link to="/forgot-password">Solicitar enlace</Link></AuthCard>;

  return (
    <AuthCard title="Nueva contraseña">
      {done ? (
        <Alert variant="success">Contraseña actualizada. Te llevamos al inicio de sesión…</Alert>
      ) : (
        <Form onSubmit={submit}>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group className="mb-3">
            <Form.Label>Nueva contraseña (mínimo 8 caracteres)</Form.Label>
            <Form.Control type="password" required minLength={8} autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
          </Form.Group>
          <Form.Group className="mb-3">
            <Form.Label>Repite la contraseña</Form.Label>
            <Form.Control type="password" required minLength={8} autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} />
          </Form.Group>
          <Button type="submit" className="w-100" disabled={busy}>{busy ? 'Guardando…' : 'Guardar contraseña'}</Button>
        </Form>
      )}
    </AuthCard>
  );
}
