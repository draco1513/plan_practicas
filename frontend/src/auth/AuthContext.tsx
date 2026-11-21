import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken, UNAUTHORIZED_EVENT } from '../api/client';
import type { CurrentUser } from '../api/types';

const MUST_CHANGE_KEY = 'tf_must_change';
const readFlag = () => {
  try { return localStorage.getItem(MUST_CHANGE_KEY) === '1'; } catch { return false; }
};
const writeFlag = (v: boolean) => {
  try { if (v) localStorage.setItem(MUST_CHANGE_KEY, '1'); else localStorage.removeItem(MUST_CHANGE_KEY); } catch { /* ignorar */ }
};

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  mustChangePassword: boolean;
  login: (email: string, password: string) => Promise<boolean>; // devuelve mustChangePassword
  logout: () => void;
  can: (permission: string) => boolean;
  passwordChanged: () => void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState<boolean>(!!getToken());
  const [mustChange, setMustChange] = useState<boolean>(readFlag());

  const logout = useCallback(() => {
    setToken(null);
    writeFlag(false);
    setMustChange(false);
    setUser(null);
  }, []);

  // Al abrir la app, si hay token se carga el usuario actual y sus permisos
  useEffect(() => {
    if (!getToken()) return;
    api.me().then(setUser).catch(() => setToken(null)).finally(() => setLoading(false));
  }, []);

  // El interceptor de axios avisa cuando el servidor responde 401
  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const t = await api.login(email, password);
    setToken(t.accessToken);
    writeFlag(t.mustChangePassword);
    setMustChange(t.mustChangePassword);
    setUser(await api.me());
    return t.mustChangePassword;
  }, []);

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    mustChangePassword: mustChange,
    login,
    logout,
    can: (p) => !!user?.permissions.includes(p),
    passwordChanged: () => { writeFlag(false); setMustChange(false); },
  }), [user, loading, mustChange, login, logout]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const c = useContext(Ctx);
  if (!c) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return c;
}
