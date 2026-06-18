'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { useToast } from '@/components/Toaster';
import { Card, Spinner, EmptyState, Button, Drawer, StatusBadge } from '@/components/ui';
import { formatCurrency, formatDate, formatNumber } from '@/lib/format';
import type { OrderSummary, OrderDetail } from '@/lib/types';

const STATUSES = ['', 'created', 'paid', 'fulfilled', 'cancelled', 'abandoned'];

export default function OrdersPage() {
  const { toast } = useToast();
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{
    content: OrderSummary[];
    totalPages: number;
    totalElements: number;
  }>({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);

  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.orders({
        status: status || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size: 20,
      });
      setData({
        content: res.content,
        totalPages: res.totalPages,
        totalElements: res.totalElements,
      });
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load orders';
      toast({ title: 'Error', description: msg, variant: 'error' });
    } finally {
      setLoading(false);
    }
  }, [status, from, to, page, toast]);

  useEffect(() => {
    load();
  }, [load]);

  async function openOrder(id: string) {
    setDrawerOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      setDetail(await api.order(id));
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to load order';
      toast({ title: 'Error', description: msg, variant: 'error' });
      setDrawerOpen(false);
    } finally {
      setDetailLoading(false);
    }
  }

  return (
    <div className="animate-slide-up space-y-5">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Orders</h1>
        <p className="text-sm text-slate-400">{formatNumber(data.totalElements)} orders</p>
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-400">Status</span>
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
              className="input w-auto capitalize [color-scheme:dark]"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s === '' ? 'All' : s}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-400">From</span>
            <input
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
              className="input w-auto [color-scheme:dark]"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-slate-400">To</span>
            <input
              type="date"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
              className="input w-auto [color-scheme:dark]"
            />
          </label>
          {(status || from || to) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setStatus('');
                setFrom('');
                setTo('');
                setPage(0);
              }}
            >
              Clear
            </Button>
          )}
        </div>
      </Card>

      <Card>
        {loading ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : data.content.length === 0 ? (
          <EmptyState title="No orders found" hint="Try adjusting the filters." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-white/5 text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">Order</th>
                  <th className="px-4 py-3 font-medium">Customer</th>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium text-center">Items</th>
                  <th className="px-4 py-3 font-medium text-center">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {data.content.map((o) => (
                  <tr
                    key={o.id}
                    onClick={() => openOrder(o.id)}
                    className="cursor-pointer transition-colors hover:bg-white/5"
                  >
                    <td className="px-4 py-3 font-mono text-xs text-slate-500">
                      {o.externalId ?? o.id.slice(0, 8)}
                    </td>
                    <td className="px-4 py-3 text-slate-200">{o.customerEmail ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-400">{formatDate(o.createdAt)}</td>
                    <td className="px-4 py-3 text-center text-slate-300">{o.itemCount}</td>
                    <td className="px-4 py-3 text-center">
                      <StatusBadge status={o.status} />
                    </td>
                    <td className="px-4 py-3 text-right font-medium text-slate-200">
                      {formatCurrency(o.total, o.currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">
            Page {page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={page >= data.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Detail drawer */}
      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} title="Order detail">
        {detailLoading ? (
          <div className="flex h-48 items-center justify-center text-slate-400">
            <Spinner />
          </div>
        ) : detail ? (
          <div className="space-y-5">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs text-slate-500">
                  {detail.externalId ?? detail.id}
                </span>
                <StatusBadge status={detail.status} />
              </div>
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <Info label="Customer" value={detail.customerEmail ?? '—'} />
                <Info label="Date" value={formatDate(detail.createdAt)} />
                <Info
                  label="Total"
                  value={formatCurrency(detail.total, detail.currency)}
                />
                <Info label="Items" value={String(detail.items.length)} />
              </dl>
            </div>

            <div>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                Line items
              </h3>
              <div className="overflow-hidden rounded-lg border border-white/10">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-white/5 text-left text-xs text-slate-400">
                      <th className="px-3 py-2 font-medium">SKU</th>
                      <th className="px-3 py-2 font-medium text-center">Qty</th>
                      <th className="px-3 py-2 font-medium text-right">Unit</th>
                      <th className="px-3 py-2 font-medium text-right">Total</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {detail.items.map((it) => (
                      <tr key={it.id}>
                        <td className="px-3 py-2 font-mono text-xs text-slate-500">
                          {it.sku}
                        </td>
                        <td className="px-3 py-2 text-center text-slate-300">
                          {it.quantity}
                        </td>
                        <td className="px-3 py-2 text-right text-slate-300">
                          {formatCurrency(it.unitPrice, detail.currency)}
                        </td>
                        <td className="px-3 py-2 text-right font-medium text-slate-200">
                          {formatCurrency(it.unitPrice * it.quantity, detail.currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-400">{label}</dt>
      <dd className="font-medium text-slate-200">{value}</dd>
    </div>
  );
}
