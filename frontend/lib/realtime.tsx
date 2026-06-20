'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api } from './api';
import { getSupabase } from './supabase';
import { isSupabaseConfigured } from './env';
import { useToast } from '@/components/Toaster';
import { useAuth } from './useAuth';
import type { Alert } from './types';

interface AlertsContextValue {
  alerts: Alert[];
  unreadCount: number;
  loading: boolean;
  refresh: () => Promise<void>;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

const AlertsContext = createContext<AlertsContextValue | null>(null);

const POLL_MS = 10_000;

function alertTitle(type: string): string {
  switch (type) {
    case 'new_order':
      return 'New order received';
    case 'low_stock':
      return 'Low stock warning';
    case 'sync_complete':
      return 'Sync complete';
    case 'sync_failed':
      return 'Sync failed';
    default:
      return 'New alert';
  }
}

export function AlertsProvider({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth();
  const { toast } = useToast();
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(false);
  const seenIds = useRef<Set<string>>(new Set());
  const primed = useRef(false);

  const refresh = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const list = await api.alerts(false);
      setAlerts(list);
      list.forEach((a) => seenIds.current.add(a.id));
    } catch {
      // swallow; transient backend errors shouldn't break the UI
    } finally {
      setLoading(false);
    }
  }, [token]);

  const markRead = useCallback(async (id: string) => {
    setAlerts((prev) => prev.map((a) => (a.id === id ? { ...a, read: true } : a)));
    try {
      await api.markAlertRead(id);
    } catch {
      // revert on failure
      setAlerts((prev) => prev.map((a) => (a.id === id ? { ...a, read: false } : a)));
    }
  }, []);

  const markAllRead = useCallback(async () => {
    const snapshot = alerts;
    setAlerts((prev) => prev.map((a) => ({ ...a, read: true })));
    try {
      await api.markAllAlertsRead();
    } catch {
      setAlerts(snapshot);
    }
  }, [alerts]);

  // Initial load whenever the token becomes available.
  useEffect(() => {
    if (!ready || !token) {
      setAlerts([]);
      seenIds.current = new Set();
      primed.current = false;
      return;
    }
    refresh().then(() => {
      primed.current = true;
    });
  }, [ready, token, refresh]);

  // Realtime via Supabase, or polling fallback.
  useEffect(() => {
    if (!ready || !token) return;

    if (isSupabaseConfigured()) {
      const supabase = getSupabase();
      if (!supabase) return;
      const channel = supabase
        .channel('merchanthub-realtime')
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'alerts' },
          (payload: { new?: { type?: string } }) => {
            const type = payload?.new?.type ?? 'alert';
            toast({ title: alertTitle(type), variant: 'info' });
            refresh();
          },
        )
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'orders' },
          () => {
            toast({ title: 'New order received', variant: 'success' });
            refresh();
          },
        )
        .subscribe();

      return () => {
        supabase.removeChannel(channel);
      };
    }

    // Polling fallback.
    const interval = setInterval(async () => {
      if (!token) return;
      try {
        const unread = await api.alerts(true);
        const fresh = unread.filter((a) => !seenIds.current.has(a.id));
        if (primed.current && fresh.length > 0) {
          fresh.forEach((a) => {
            toast({ title: alertTitle(a.type), variant: 'info' });
          });
        }
        unread.forEach((a) => seenIds.current.add(a.id));
        if (fresh.length > 0) {
          await refresh();
        }
      } catch {
        // ignore transient errors
      }
    }, POLL_MS);

    return () => clearInterval(interval);
  }, [ready, token, toast, refresh]);

  const unreadCount = useMemo(
    () => alerts.filter((a) => !a.read).length,
    [alerts],
  );

  const value = useMemo(
    () => ({ alerts, unreadCount, loading, refresh, markRead, markAllRead }),
    [alerts, unreadCount, loading, refresh, markRead, markAllRead],
  );

  return <AlertsContext.Provider value={value}>{children}</AlertsContext.Provider>;
}

export function useAlerts(): AlertsContextValue {
  const ctx = useContext(AlertsContext);
  if (!ctx) {
    return {
      alerts: [],
      unreadCount: 0,
      loading: false,
      refresh: async () => {},
      markRead: async () => {},
      markAllRead: async () => {},
    };
  }
  return ctx;
}
