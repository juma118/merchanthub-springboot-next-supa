'use client';

import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Cell,
} from 'recharts';
import type { RevenuePoint, TopProductItem, TopMetric } from '@/lib/types';
import { formatCurrency, formatNumber, formatDateShort } from '@/lib/format';

const LINE = '#818cf8';
const BARS = ['#6366f1', '#818cf8', '#a78bfa', '#c4b5fd', '#ddd6fe'];
const GRID = '#ffffff14';
const AXIS = '#94a3b8';
const TOOLTIP_STYLE = {
  background: '#0f172a',
  border: '1px solid rgba(255,255,255,0.1)',
  borderRadius: 8,
  color: '#e2e8f0',
  fontSize: 12,
} as const;

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <AreaChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#818cf8" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#818cf8" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke={GRID} vertical={false} />
        <XAxis
          dataKey="bucket"
          tickFormatter={(v: string) => formatDateShort(v)}
          stroke={AXIS}
          tick={{ fill: AXIS }}
          fontSize={12}
          tickLine={false}
          axisLine={false}
        />
        <YAxis
          stroke={AXIS}
          tick={{ fill: AXIS }}
          fontSize={12}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v: number) => `$${formatNumber(v)}`}
          width={70}
        />
        <Tooltip
          formatter={(value: number, name: string) =>
            name === 'revenue'
              ? [formatCurrency(value), 'Revenue']
              : [formatNumber(value), 'Orders']
          }
          labelFormatter={(v: string) => formatDateShort(v)}
          contentStyle={TOOLTIP_STYLE}
          cursor={{ stroke: 'rgba(255,255,255,0.15)' }}
        />
        <Area
          type="monotone"
          dataKey="revenue"
          stroke={LINE}
          strokeWidth={2}
          fill="url(#revenueFill)"
          dot={false}
          activeDot={{ r: 5 }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function TopProductsChart({
  items,
  metric,
}: {
  items: TopProductItem[];
  metric: TopMetric;
}) {
  const data = items.map((it) => ({
    name: it.name.length > 18 ? `${it.name.slice(0, 18)}…` : it.name,
    value: metric === 'revenue' ? it.revenue : it.units,
  }));

  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart
        data={data}
        layout="vertical"
        margin={{ top: 4, right: 16, left: 8, bottom: 0 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke={GRID} horizontal={false} />
        <XAxis
          type="number"
          stroke={AXIS}
          tick={{ fill: AXIS }}
          fontSize={12}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v: number) =>
            metric === 'revenue' ? `$${formatNumber(v)}` : formatNumber(v)
          }
        />
        <YAxis
          type="category"
          dataKey="name"
          stroke={AXIS}
          tick={{ fill: AXIS }}
          fontSize={12}
          tickLine={false}
          axisLine={false}
          width={130}
        />
        <Tooltip
          formatter={(value: number) => [
            metric === 'revenue' ? formatCurrency(value) : formatNumber(value),
            metric === 'revenue' ? 'Revenue' : 'Units',
          ]}
          contentStyle={TOOLTIP_STYLE}
          cursor={{ fill: 'rgba(255,255,255,0.05)' }}
        />
        <Bar dataKey="value" radius={[0, 4, 4, 0]}>
          {data.map((_, i) => (
            <Cell key={i} fill={BARS[i % BARS.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
