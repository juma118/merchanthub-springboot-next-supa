'use client';

import { useEffect, useState } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
type Size = 'sm' | 'md';

const variantClasses: Record<Variant, string> = {
  primary:
    'bg-gradient-to-r from-indigo-500 to-violet-500 text-white shadow-lg shadow-indigo-500/20 hover:from-indigo-400 hover:to-violet-400 focus-visible:ring-indigo-400 disabled:from-indigo-500/50 disabled:to-violet-500/50',
  secondary:
    'bg-white/5 text-slate-200 border border-white/10 hover:bg-white/10 hover:text-white focus-visible:ring-indigo-400',
  danger:
    'bg-rose-600 text-white hover:bg-rose-500 focus-visible:ring-rose-400 disabled:bg-rose-600/50',
  ghost:
    'bg-transparent text-slate-400 hover:bg-white/5 hover:text-slate-100 focus-visible:ring-indigo-400',
};

const sizeClasses: Record<Size, string> = {
  sm: 'px-2.5 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
};

export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
}) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-950 disabled:cursor-not-allowed disabled:opacity-70 ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function Card({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`rounded-xl border border-white/10 bg-slate-900/60 shadow-lg shadow-black/30 backdrop-blur ${className}`}
    >
      {children}
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    paid: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    fulfilled: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
    created: 'bg-slate-500/15 text-slate-300 border-white/10',
    cancelled: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    abandoned: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    success: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    running: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
    failed: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    ok: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    low: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    critical: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  };
  const cls = map[status] ?? 'bg-slate-500/15 text-slate-300 border-white/10';
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium capitalize ${cls}`}
    >
      {status}
    </span>
  );
}

export function Spinner({ className = '' }: { className?: string }) {
  return (
    <span
      className={`inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
      aria-label="Loading"
    />
  );
}

export function EmptyState({
  title,
  hint,
}: {
  title: string;
  hint?: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-1 px-6 py-12 text-center">
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

/**
 * useTransitionMount drives an enter/leave animation for an overlay.
 * `mounted` controls presence in the DOM; `visible` toggles the "shown"
 * classes. On open we mount, then flip visible on the next frame to play the
 * enter transition. On close we flip visible off (playing the leave
 * transition) and unmount after `duration` ms.
 */
function useTransitionMount(open: boolean, duration: number) {
  const [mounted, setMounted] = useState(open);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let raf = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    if (open) {
      setMounted(true);
      // Next frame: apply the shown classes to trigger the enter transition.
      raf = requestAnimationFrame(() => setVisible(true));
    } else {
      setVisible(false);
      timer = setTimeout(() => setMounted(false), duration);
    }
    return () => {
      cancelAnimationFrame(raf);
      if (timer) clearTimeout(timer);
    };
  }, [open, duration]);

  return { mounted, visible };
}

export function Modal({
  open,
  onClose,
  title,
  children,
  wide = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  wide?: boolean;
}) {
  const { mounted, visible } = useTransitionMount(open, 200);
  if (!mounted) return null;
  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm transition-opacity duration-200 ${
        visible ? 'opacity-100' : 'opacity-0'
      }`}
      onClick={onClose}
    >
      <div
        className={`max-h-[90vh] w-full overflow-y-auto rounded-xl border border-white/10 bg-slate-900/90 shadow-2xl shadow-black/50 backdrop-blur transition-all duration-200 ${
          visible
            ? 'opacity-100 scale-100 translate-y-0'
            : 'opacity-0 scale-95 translate-y-1'
        } ${wide ? 'max-w-2xl' : 'max-w-md'}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-100">{title}</h2>
          <button
            onClick={onClose}
            className="text-slate-400 transition-colors hover:text-slate-100"
            aria-label="Close"
          >
            ×
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

export function Drawer({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}) {
  const { mounted, visible } = useTransitionMount(open, 250);
  if (!mounted) return null;
  return (
    <div
      className={`fixed inset-0 z-50 flex justify-end bg-slate-950/70 backdrop-blur-sm transition-opacity duration-200 ${
        visible ? 'opacity-100' : 'opacity-0'
      }`}
      onClick={onClose}
    >
      <div
        className={`flex h-full w-full max-w-md flex-col border-l border-white/10 bg-slate-900/90 shadow-2xl shadow-black/50 backdrop-blur transition-transform duration-[250ms] ease-[cubic-bezier(0.16,1,0.3,1)] ${
          visible ? 'translate-x-0' : 'translate-x-full'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
          <h2 className="text-base font-semibold text-slate-100">{title}</h2>
          <button
            onClick={onClose}
            className="text-slate-400 transition-colors hover:text-slate-100"
            aria-label="Close"
          >
            ×
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
      </div>
    </div>
  );
}
