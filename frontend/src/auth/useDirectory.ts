import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { UserSummary } from '../api/types';

/**
 * Lista de personas que se pueden elegir (responsables, invitados...).
 * Intenta GET /users (requiere gestión de usuarios); si no hay permiso,
 * usa los miembros de los proyectos visibles.
 */
export function useDirectory(): UserSummary[] {
  const [list, setList] = useState<UserSummary[]>([]);
  useEffect(() => {
    let alive = true;
    (async () => {
      let result: UserSummary[] = [];
      try {
        const p = await api.users({ size: 100, active: true });
        result = p.content.map((u) => ({ id: u.id, fullName: u.fullName, email: u.email }));
      } catch {
        try {
          const projects = await api.projects({ size: 100 });
          const lists = await Promise.all(projects.content.map((p) => api.members(p.id).catch(() => [])));
          const map = new Map<number, UserSummary>();
          lists.flat().forEach((u) => map.set(u.id, u));
          projects.content.forEach((p) => map.set(p.owner.id, p.owner));
          result = [...map.values()];
        } catch { /* sin datos */ }
      }
      if (alive) setList(result.sort((a, b) => a.fullName.localeCompare(b.fullName)));
    })();
    return () => { alive = false; };
  }, []);
  return list;
}
