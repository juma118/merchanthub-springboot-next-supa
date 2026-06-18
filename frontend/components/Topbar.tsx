'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/useAuth';
import { useAlerts } from '@/lib/realtime';
import { Button } from '@/components/ui';

export function Topbar() {
  const { merchant, logout } = useAuth();
  const { unreadCount } = useAlerts();

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center justify-between border-b border-white/10 bg-slate-950/70 px-4 backdrop-blur md:px-6">
      <div className="flex items-center gap-2">
        <span className="text-sm font-semibold text-slate-100 md:hidden">MerchantHub</span>
      </div>
      <div className="flex items-center gap-3">
        <Link
          href="/alerts"
          className="relative flex h-9 w-9 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-white/5 hover:text-slate-100"
          aria-label="Alerts"
        >
          <span className="text-lg">🔔</span>
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-gradient-to-r from-indigo-500 to-violet-500 px-1 text-[10px] font-bold text-white shadow-lg shadow-indigo-500/30">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </Link>
        <div className="hidden text-right sm:block">
          <div className="text-sm font-medium text-slate-100">
            {merchant?.name ?? '—'}
          </div>
          <div className="text-xs text-slate-500">{merchant?.email ?? ''}</div>
        </div>
        <Button variant="secondary" size="sm" onClick={logout}>
          Log out
        </Button>
      </div>
    </header>
  );
}
