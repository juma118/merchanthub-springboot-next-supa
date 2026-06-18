'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useToast } from '@/components/Toaster';
import { Card, Spinner } from '@/components/ui';
import { RevenueChart, TopProductsChart } from '@/components/charts';
import {
  formatCurrency,
  formatNumber,
  formatPercent,
} from '@/lib/format';
import type {
  RevenueResponse,
  TopProductsResponse,
  FunnelResponse,
  InventoryRow,
  Granularity,
  TopMetric,
} from '@/lib/types';

function KpiCard({
  label,
  value,
  sub,
  tone = 'default',
}: {
  label: string;
  value: string;
  sub?: React.ReactNode;
  tone?: 'default' | 'warning' | 'danger';
}) {
  const ring =
    tone === 'danger'
      ? 'ring-1 ring-rose-500/30'
      : tone === 'warning'
        ? 'ring-1 ring-amber-500/30'
        : '';
  return (
    <Card className={`p-5 transition-colors hover:border-white/20 ${ring}`}>
      <div className="text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </div>
      <div className="mt-1 text-2xl font-bold text-slate-50">{value}</div>
      {sub && <div className="mt-1 text-xs">{sub}</div>}
    </Card>
  );
}

function DeltaBadge({ changePct }: { changePct: number }) {
  const positive = changePct >= 0;
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-xs font-semibold ${
        positive
          ? 'bg-emerald-500/15 text-emerald-300'
          : 'bg-rose-500/15 text-rose-300'
      }`}
    >
      {positive ? '▲' : '▼'} {formatPercent(Math.abs(changePct))}
    </span>
  );
}

function Funnel({ data }: { data: FunnelResponse }) {
  const steps = [
    { label: 'Created', value: data.created, color: 'bg-slate-500' },
    { label: 'Paid', value: data.paid, color: 'bg-gradient-to-r from-indigo-500 to-violet-500' },
    { label: 'Fulfilled', value: data.fulfilled, color: 'bg-emerald-500' },
  ];
  const max = Math.max(data.created, 1);
  return (
    <div className="space-y-3">
      {steps.map((s) => {
        const pct = Math.round((s.value / max) * 100);
        return (
          <div key={s.label}>
            <div className="mb-1 flex items-center justify-between text-xs">
              <span className="font-medium text-slate-300">{s.label}</span>
              <span className="text-slate-400">
                {formatNumber(s.value)} ({pct}%)
              </span>
            </div>
            <div className="h-2.5 w-full overflow-hidden rounded-full bg-white/5">
              <div
                className={`h-full rounded-full ${s.color}`}
                style={{ width: `${Math.max(pct, 2)}%` }}
              />
            </div>
          </div>
        );
      })}
      <div className="flex items-center justify-between pt-1 text-xs text-slate-400">
        <span>Abandoned rate</span>
        <span className="font-semibold text-amber-300">
          {formatPercent(data.abandonedRate)}
        </span>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { toast } = useToast();
  const [granularity, setGranularity] = useState<Granularity>('day');
  const [topMetric, setTopMetric] = useState<TopMetric>('revenue');

  const [revenue, setRevenue] = useState<RevenueResponse | null>(null);
  const [top, setTop] = useState<TopProductsResponse | null>(null);
  const [funnel, setFunnel] = useState<FunnelResponse | null>(null);
  const [inventory, setInventory] = useState<InventoryRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.allSettled([
      api.revenue(granularity),
      api.topProducts(topMetric, 5),
      api.funnel(),
      api.inventory(),
    ])
      .then(([rev, tp, fn, inv]) => {
        if (cancelled) return;
        if (rev.status === 'fulfilled') setRevenue(rev.value);
        if (tp.status === 'fulfilled') setTop(tp.value);
        if (fn.status === 'fulfilled') setFunnel(fn.value);
        if (inv.status === 'fulfilled') setInventory(inv.value);
        const failed = [rev, tp, fn, inv].some((r) => r.status === 'rejected');
        if (failed) {
          toast({
            title: 'Some data could not be loaded',
            description: 'Check that the backend is running.',
            variant: 'warning',
          });
        }
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [granularity, topMetric]);

  const lowStockCount = inventory.filter((i) => i.lowStock).length;

  if (loading && !revenue) {
    return (
      <div className="flex h-64 items-center justify-center text-slate-400">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="animate-slide-up space-y-6">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Dashboard</h1>
        <p className="text-sm text-slate-400">Performance overview</p>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          label="Total revenue"
          value={revenue ? formatCurrency(revenue.totalRevenue) : '—'}
          sub={
            revenue ? (
              <span className="flex items-center gap-1 text-slate-400">
                <DeltaBadge changePct={revenue.changePct} /> vs previous
              </span>
            ) : undefined
          }
        />
        <KpiCard
          label="Total orders"
          value={revenue ? formatNumber(revenue.totalOrders) : '—'}
          sub={
            revenue ? (
              <span className="text-slate-400">
                prev {formatNumber(revenue.previous.totalOrders)}
              </span>
            ) : undefined
          }
        />
        <KpiCard
          label="Abandoned rate"
          value={funnel ? formatPercent(funnel.abandonedRate) : '—'}
          tone={funnel && funnel.abandonedRate > 30 ? 'warning' : 'default'}
        />
        <KpiCard
          label="Low-stock items"
          value={formatNumber(lowStockCount)}
          tone={lowStockCount > 0 ? 'danger' : 'default'}
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-slate-200">Revenue</h2>
              {revenue && <DeltaBadge changePct={revenue.changePct} />}
            </div>
            <div className="flex rounded-lg border border-white/10 p-0.5">
              {(['day', 'week', 'month'] as Granularity[]).map((g) => (
                <button
                  key={g}
                  onClick={() => setGranularity(g)}
                  className={`rounded-md px-2.5 py-1 text-xs font-medium capitalize transition-colors ${
                    granularity === g
                      ? 'bg-gradient-to-r from-indigo-500 to-violet-500 text-white shadow shadow-indigo-500/20'
                      : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
                  }`}
                >
                  {g}
                </button>
              ))}
            </div>
          </div>
          {revenue && revenue.series.length > 0 ? (
            <RevenueChart data={revenue.series} />
          ) : (
            <div className="flex h-64 items-center justify-center text-sm text-slate-500">
              No revenue data
            </div>
          )}
        </Card>

        <Card className="p-5">
          <div className="mb-4">
            <h2 className="text-sm font-semibold text-slate-200">Order funnel</h2>
          </div>
          {funnel ? (
            <Funnel data={funnel} />
          ) : (
            <div className="flex h-48 items-center justify-center text-sm text-slate-500">
              No funnel data
            </div>
          )}
        </Card>
      </div>

      {/* Top products */}
      <Card className="p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-200">Top products</h2>
          <div className="flex rounded-lg border border-white/10 p-0.5">
            {(['revenue', 'units'] as TopMetric[]).map((m) => (
              <button
                key={m}
                onClick={() => setTopMetric(m)}
                className={`rounded-md px-2.5 py-1 text-xs font-medium capitalize transition-colors ${
                  topMetric === m
                    ? 'bg-gradient-to-r from-indigo-500 to-violet-500 text-white shadow shadow-indigo-500/20'
                    : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
                }`}
              >
                {m}
              </button>
            ))}
          </div>
        </div>
        {top && top.items.length > 0 ? (
          <TopProductsChart items={top.items} metric={topMetric} />
        ) : (
          <div className="flex h-48 items-center justify-center text-sm text-slate-500">
            No product data
          </div>
        )}
      </Card>
    </div>
  );
}
