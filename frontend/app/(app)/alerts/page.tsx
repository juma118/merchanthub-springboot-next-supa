'use client';

import { useEffect } from 'react';
import { useAlerts } from '@/lib/realtime';
import { Card, Button, Spinner, EmptyState } from '@/components/ui';
import { relativeTime } from '@/lib/format';
import type { Alert, AlertType } from '@/lib/types';

const ICONS: Record<string, string> = {
  new_order: '🛒',
  low_stock: '📉',
  sync_complete: '✅',
  sync_failed: '⚠️',
};

const TITLES: Record<string, string> = {
  new_order: 'New order',
  low_stock: 'Low stock',
  sync_complete: 'Sync complete',
  sync_failed: 'Sync failed',
};

function describe(alert: Alert): string {
  const p = alert.payload ?? {};
  switch (alert.type) {
    case 'new_order':
      return p.externalId
        ? `Order ${String(p.externalId)} placed`
        : 'A new order was placed';
    case 'low_stock':
      return p.sku
        ? `${String(p.sku)} is running low (${String(p.quantity ?? '?')} left)`
        : 'A product is low on stock';
    case 'sync_complete':
      return p.recordsProcessed != null
        ? `Processed ${String(p.recordsProcessed)} records`
        : 'Catalog sync finished';
    case 'sync_failed':
      return p.detail ? String(p.detail) : 'A sync run failed';
    default:
      return 'Notification';
  }
}

export default function AlertsPage() {
  const { alerts, unreadCount, loading, refresh, markRead, markAllRead } = useAlerts();

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="animate-slide-up space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-100">Alerts</h1>
          <p className="text-sm text-slate-400">
            {unreadCount} unread of {alerts.length}
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          disabled={unreadCount === 0}
          onClick={markAllRead}
        >
          Mark all read
        </Button>
      </div>

      <Card>
        {loading && alerts.length === 0 ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : alerts.length === 0 ? (
          <EmptyState title="No alerts yet" hint="New activity will appear here." />
        ) : (
          <ul className="divide-y divide-white/5">
            {alerts.map((a) => {
              const type = a.type as AlertType;
              return (
                <li
                  key={a.id}
                  className={`flex items-start gap-3 px-4 py-3 transition-colors ${
                    a.read ? 'hover:bg-white/5' : 'bg-indigo-500/10'
                  }`}
                >
                  <span className="mt-0.5 text-lg">{ICONS[a.type] ?? '🔔'}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-slate-100">
                        {TITLES[a.type] ?? 'Alert'}
                      </span>
                      {!a.read && (
                        <span className="h-2 w-2 shrink-0 rounded-full bg-indigo-400" />
                      )}
                    </div>
                    <p className="truncate text-sm text-slate-400">{describe(a)}</p>
                    <span className="text-xs text-slate-500">
                      {relativeTime(a.createdAt)}
                    </span>
                  </div>
                  {!a.read && (
                    <Button size="sm" variant="ghost" onClick={() => markRead(a.id)}>
                      Mark read
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </div>
  );
}
