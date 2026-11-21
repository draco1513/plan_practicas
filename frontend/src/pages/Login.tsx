import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form } from 'react-bootstrap';
import { useAuth } from '../auth/AuthContext';
import { errorText } from '../api/client';
import { Logo } from '../components/Layout';

export function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="tf-auth">
      <Card className="shadow">
        <Card.Body className="p-4">
          <div className="text-center bg-dark rounded-3 py-3 mb-4"><Logo to="/login" /></div>
          <h5 className="mb-3">{title}</h5>
          {children}
        </Card.Body>
      </Card>
    </div>
  );
}

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState(''); // solo vive en este estado local mientras se escribe
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const mustChange = await login(email.trim(), password);
      setPassword('');
      const from = (loc.state as { from?: string } | null)?.from;
      nav(mustChange ? '/change-password' : from && from !== '/login' ? from : '/', { replace: true });
    } catch (err) {
      setPassword('');
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Iniciar sesión">
      {error && <Alert variant="danger">{error}</Alert>}
      <Form onSubmit={submit}>
        <Form.Group className="mb-3">
          <Form.Label>Correo electrónico</Form.Label>
          <Form.Control type="email" required autoFocus autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Form.Group>
        <Form.Group className="mb-3">
          <Form.Label>Contraseña</Form.Label>
          <Form.Control type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Form.Group>
        <Button type="submit" variant="primary" className="w-100" disabled={busy}>{busy ? 'Ingresando…' : 'Ingresar'}</Button>
        <div className="text-center mt-3"><Link to="/forgot-password">¿Olvidaste tu contraseña?</Link></div>
      </Form>
    </AuthCard>
  );
}
