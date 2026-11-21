import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Toast, ToastContainer } from 'react-bootstrap';

type Kind = 'success' | 'danger' | 'info';
interface Item { id: number; kind: Kind; text: string }
const Ctx = createContext<(kind: Kind, text: string) => void>(() => {});

/** Avisos flotantes (éxito / error) en la esquina superior derecha. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Item[]>([]);
  const notify = useCallback((kind: Kind, text: string) => {
    setItems((l) => [...l, { id: Date.now() + Math.random(), kind, text }]);
  }, []);
  return (
    <Ctx.Provider value={notify}>
      {children}
      <ToastContainer position="top-end" className="p-3" style={{ zIndex: 2000 }}>
        {items.map((i) => (
          <Toast key={i.id} bg={i.kind} autohide delay={5000} onClose={() => setItems((l) => l.filter((x) => x.id !== i.id))}>
            <Toast.Body className={i.kind === 'info' ? '' : 'text-white'}>{i.text}</Toast.Body>
          </Toast>
        ))}
      </ToastContainer>
    </Ctx.Provider>
  );
}
export const useToast = () => useContext(Ctx);
