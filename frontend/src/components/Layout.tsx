import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Dropdown } from 'react-bootstrap';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';
import { ROLE_LABEL } from '../utils/format';
import { Avatar } from './Common';

export const Logo = ({ to = '/' }: { to?: string }) => (
  <Link to={to} className="tf-logo">ESFX<span>TASKFLOW</span></Link>
);

/** Campana: consulta cuántas notificaciones hay sin leer cada 30 segundos. */
function Bell() {
  const [count, setCount] = useState(0);
  useEffect(() => {
    let alive = true;
    const load = () => api.unreadCount().then((r) => alive && setCount(r.count)).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    window.addEventListener('tf:notifications-changed', load);
    return () => { alive = false; clearInterval(t); window.removeEventListener('tf:notifications-changed', load); };
  }, []);
  return (
    <Link to="/notifications" className="btn btn-link tf-bell" aria-label="Notificaciones" title="Notificaciones">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
      </svg>
      {count > 0 && <span className="tf-count">{count > 99 ? '99+' : count}</span>}
    </Link>
  );
}

export default function Layout() {
  const { user, can, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => setOpen(false), [loc.pathname]);

  const links = [
    { to: '/', label: 'Dashboard', end: true, show: true },
    { to: '/projects', label: 'Proyectos', show: true },
    { to: '/calendar', label: 'Calendario', show: true },
    { to: '/notifications', label: 'Notificaciones', show: true },
    { to: '/users', label: 'Usuarios', show: can('USER_MANAGE') },
    { to: '/audit', label: 'Auditoría', show: can('AUDIT_VIEW') },
  ].filter((l) => l.show);

  return (
    <div className="tf-shell">
      <aside className={`tf-sidebar ${open ? 'open' : ''}`}>
        <div className="tf-brand"><Logo /></div>
        <nav>
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => `nav-item-link ${isActive ? 'active' : ''}`}>
              {l.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className={`tf-backdrop ${open ? 'open' : ''}`} onClick={() => setOpen(false)} />

      <div className="tf-main">
        <header className="tf-header">
          <button className="btn btn-link d-lg-none" aria-label="Menú" onClick={() => setOpen(true)}>
            <svg width="24" height="24" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2" fill="none"><path d="M3 6h18M3 12h18M3 18h18" /></svg>
          </button>
          <span className="d-none d-lg-block" />
          <div className="d-flex align-items-center gap-2">
            <Bell />
            <Dropdown align="end">
              <Dropdown.Toggle variant="link" className="d-flex align-items-center gap-2 text-white text-decoration-none">
                <Avatar user={user} size={30} />
                <span className="d-none d-md-inline">{user?.fullName}</span>
              </Dropdown.Toggle>
              <Dropdown.Menu>
                <Dropdown.ItemText>
                  <div className="fw-semibold">{user?.fullName}</div>
                  <small className="text-muted">{user ? ROLE_LABEL[user.role.code] ?? user.role.name : ''}</small>
                </Dropdown.ItemText>
                <Dropdown.Divider />
                <Dropdown.Item onClick={() => nav('/change-password')}>Cambiar contraseña</Dropdown.Item>
                <Dropdown.Item onClick={() => { logout(); nav('/login'); }}>Cerrar sesión</Dropdown.Item>
              </Dropdown.Menu>
            </Dropdown>
          </div>
        </header>
        <main className="tf-content"><Outlet /></main>
      </div>
    </div>
  );
}
