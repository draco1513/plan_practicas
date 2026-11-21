import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, Col, Form, Row, Table } from 'react-bootstrap';
import { api, errorText } from '../api/client';
import type { AuditLog, Paged } from '../api/types';
import { useDirectory } from '../auth/useDirectory';
import { Empty, Loading, Pager } from '../components/Common';
import { fmtDateTime } from '../utils/format';

const ENTITIES = ['USER', 'PROJECT', 'TASK', 'MEETING', 'COMMENT', 'ATTACHMENT', 'TIME_ENTRY'];

export default function AuditPage() {
  const people = useDirectory();
  const [data, setData] = useState<Paged<AuditLog> | null>(null);
  const [page, setPage] = useState(0);
  const [entityType, setEntityType] = useState('');
  const [actor, setActor] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(() => {
    api.auditLogs({ page, size: 20, entityType: entityType || undefined, actorUserId: actor ? Number(actor) : undefined, from: from || undefined, to: to || undefined })
      .then(setData).catch((e) => setError(errorText(e)));
  }, [page, entityType, actor, from, to]);
  useEffect(load, [load]);

  const reset = (fn: () => void) => { setPage(0); fn(); };

  return (
    <>
      <h3 className="mb-3">Auditoría</h3>
      <Row className="g-2 mb-3">
        <Col sm={6} md={3}>
          <Form.Select value={entityType} onChange={(e) => reset(() => setEntityType(e.target.value))}>
            <option value="">Todas las entidades</option>
            {ENTITIES.map((x) => <option key={x}>{x}</option>)}
          </Form.Select>
        </Col>
        <Col sm={6} md={3}>
          <Form.Select value={actor} onChange={(e) => reset(() => setActor(e.target.value))}>
            <option value="">Todos los usuarios</option>
            {people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
          </Form.Select>
        </Col>
        <Col xs={6} md={3}><Form.Control type="date" aria-label="Desde" value={from} onChange={(e) => reset(() => setFrom(e.target.value))} /></Col>
        <Col xs={6} md={3}><Form.Control type="date" aria-label="Hasta" value={to} min={from} onChange={(e) => reset(() => setTo(e.target.value))} /></Col>
      </Row>
      {error && <Alert variant="danger">{error}</Alert>}
      {!data ? <Loading /> : (
        <Card><Card.Body>
          {data.content.length === 0 ? <Empty text="Sin registros para esos filtros." /> : (
            <div className="table-responsive">
              <Table size="sm" hover>
                <thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>Detalle</th></tr></thead>
                <tbody>
                  {data.content.map((a) => (
                    <tr key={a.id}>
                      <td className="text-nowrap small">{fmtDateTime(a.createdAt)}</td>
                      <td>{a.actor?.fullName ?? '—'}</td>
                      <td><code>{a.action}</code></td>
                      <td>{a.entityType} #{a.entityId}</td>
                      <td>{a.detail && Object.keys(a.detail).length > 0 ? <pre className="pre-json">{JSON.stringify(a.detail)}</pre> : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
          <Pager data={data} onPage={setPage} />
        </Card.Body></Card>
      )}
    </>
  );
}
