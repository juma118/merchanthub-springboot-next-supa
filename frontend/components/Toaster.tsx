'use client';

import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { useEffect, useRef } from 'react';

export type ToastVariant = 'info' | 'success' | 'error' | 'warning';

export interface Toast {
  id: string;
  title: string;
  description?: string;
  variant: ToastVariant;
}

interface ToastContextValue {
  toast: (t: Omit<Toast, 'id'> | string) => void;
  dismiss: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const variantStyles: Record<ToastVariant, string> = {
  info: 'border-indigo-500/30 bg-slate-900/90 text-slate-100',
  success: 'border-emerald-500/30 bg-slate-900/90 text-slate-100',
  error: 'border-rose-500/30 bg-slate-900/90 text-slate-100',
  warning: 'border-amber-500/30 bg-slate-900/90 text-slate-100',
};

const accent: Record<ToastVariant, string> = {
  info: 'bg-indigo-500',
  success: 'bg-emerald-500',
  error: 'bg-rose-500',
  warning: 'bg-amber-500',
};

export function ToasterProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [mounted, setMounted] = useState(false);
  const timers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  useEffect(() => {
    setMounted(true);
    return () => {
      Object.values(timers.current).forEach(clearTimeout);
    };
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    if (timers.current[id]) {
      clearTimeout(timers.current[id]);
      delete timers.current[id];
    }
  }, []);

  const toast = useCallback(
    (t: Omit<Toast, 'id'> | string) => {
      const id = Math.random().toString(36).slice(2);
      const next: Toast =
        typeof t === 'string'
          ? { id, title: t, variant: 'info' }
          : { id, ...t };
      setToasts((prev) => [...prev, next]);
      timers.current[id] = setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ toast, dismiss }}>
      {children}
      {mounted &&
        createPortal(
          <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-80 flex-col gap-2">
            {toasts.map((t) => (
              <div
                key={t.id}
                className={`pointer-events-auto flex animate-slide-in-right overflow-hidden rounded-lg border shadow-lg shadow-black/40 backdrop-blur ${variantStyles[t.variant]}`}
              >
                <div className={`w-1.5 shrink-0 ${accent[t.variant]}`} />
                <div className="flex-1 px-4 py-3">
                  <div className="text-sm font-semibold">{t.title}</div>
                  {t.description && (
                    <div className="mt-0.5 text-xs text-slate-400">{t.description}</div>
                  )}
                </div>
                <button
                  onClick={() => dismiss(t.id)}
                  className="px-3 text-slate-400 transition-colors hover:text-slate-100"
                  aria-label="Dismiss"
                >
                  ×
                </button>
              </div>
            ))}
          </div>,
          document.body,
        )}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    // Fail soft: a no-op so importing components never crash outside provider.
    return { toast: () => {}, dismiss: () => {} };
  }
  return ctx;
}
