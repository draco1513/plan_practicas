import { Badge, Pagination, Spinner } from 'react-bootstrap';
import type { Paged, TaskPriority, TaskStatus, UserSummary } from '../api/types';
import { initials, PRIORITY_LABEL, STATUS_LABEL } from '../utils/format';

export const Loading = () => <div className="d-flex justify-content-center py-5"><Spinner animation="border" /></div>;

export const StatusBadge = ({ s }: { s: TaskStatus }) => {
  const bg = s === 'DONE' ? 'success' : s === 'IN_PROGRESS' ? 'primary' : 'secondary';
  return <Badge bg={bg}>{STATUS_LABEL[s]}</Badge>;
};

export const PriorityBadge = ({ p }: { p: TaskPriority }) => {
  const bg = p === 'HIGH' ? 'danger' : p === 'MEDIUM' ? 'warning' : 'secondary';
  return <Badge bg={bg} text={p === 'MEDIUM' ? 'dark' : undefined}>{PRIORITY_LABEL[p]}</Badge>;
};

export const Avatar = ({ user, size = 26 }: { user?: UserSummary | null; size?: number }) =>
  user ? (
    <span className="tf-avatar" style={{ width: size, height: size, fontSize: size * 0.4 }} title={user.fullName}>
      {initials(user.fullName)}
    </span>
  ) : null;

export const Empty = ({ text }: { text: string }) => <div className="text-center text-muted py-4">{text}</div>;

/** Paginador. La API cuenta las páginas desde 0. */
export function Pager<T>({ data, onPage }: { data: Paged<T>; onPage: (p: number) => void }) {
  if (data.totalPages <= 1) return null;
  const items = [];
  for (let i = 0; i < data.totalPages; i++) {
    if (i === 0 || i === data.totalPages - 1 || Math.abs(i - data.page) <= 2) {
      items.push(<Pagination.Item key={i} active={i === data.page} onClick={() => onPage(i)}>{i + 1}</Pagination.Item>);
    } else if (Math.abs(i - data.page) === 3) {
      items.push(<Pagination.Ellipsis key={i} disabled />);
    }
  }
  return (
    <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
      <small className="text-muted">{data.totalElements} registros</small>
      <Pagination className="mb-0" size="sm">
        <Pagination.Prev disabled={data.page === 0} onClick={() => onPage(data.page - 1)} />
        {items}
        <Pagination.Next disabled={data.page >= data.totalPages - 1} onClick={() => onPage(data.page + 1)} />
      </Pagination>
    </div>
  );
}
