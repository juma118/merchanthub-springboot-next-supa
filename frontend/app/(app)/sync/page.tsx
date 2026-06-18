'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toaster';
import { Card, Button, Spinner, EmptyState, StatusBadge } from '@/components/ui';
import { formatDate, formatNumber } from '@/lib/format';
import type { SyncLog } from '@/lib/types';

export default function SyncPage() {
  const { toast } = useToast();
  const [logs, setLogs] = useState<SyncLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setLogs(await api.syncLogs());
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load sync logs';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  async function runSync() {
    setRunning(true);
    try {
      const res = await api.runSync();
      toast({
        title: 'Sync started',
        description: `${res.status} · ${formatNumber(res.recordsProcessed)} records processed`,
        variant: 'success',
      });
      await load();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Sync failed to start';
      toast({ title: 'Sync failed', description: msg, variant: 'error' });
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="animate-slide-up space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-100">Sync</h1>
          <p className="text-sm text-slate-400">Pull the latest catalog and orders</p>
        </div>
        <Button onClick={runSync} disabled={running}>
          {running ? (
            <>
              <Spinner className="text-white" /> Running…
            </>
          ) : (
            'Run sync now'
          )}
        </Button>
      </div>

      <Card>
        {loading ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : logs.length === 0 ? (
          <EmptyState title="No sync runs yet" hint="Run a sync to populate logs." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-white/5 text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">Type</th>
                  <th className="px-4 py-3 font-medium text-center">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Records</th>
                  <th className="px-4 py-3 font-medium">Started</th>
                  <th className="px-4 py-3 font-medium">Finished</th>
                  <th className="px-4 py-3 font-medium">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {logs.map((log) => (
                  <tr key={log.id} className="transition-colors hover:bg-white/5">
                    <td className="px-4 py-3 font-medium capitalize text-slate-200">
                      {log.type}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <StatusBadge status={log.status} />
                    </td>
                    <td className="px-4 py-3 text-right text-slate-300">
                      {formatNumber(log.recordsProcessed)}
                    </td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(log.startedAt)}</td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(log.finishedAt)}</td>
                    <td className="px-4 py-3 max-w-xs truncate text-slate-400">
                      {log.detail ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
