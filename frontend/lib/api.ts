// Typed API client. Attaches Bearer token to every request, throws on non-2xx
// surfacing the backend `{ message }`, and on 401 clears the token + redirects.

import { env } from './env';
import { getToken, clearToken } from './token';
import type {
  DevTokenResponse,
  Me,
  Page,
  Product,
  ProductInput,
  ImportResult,
  InventoryRow,
  InventoryInput,
  OrderSummary,
  OrderDetail,
  RevenueResponse,
  TopProductsResponse,
  FunnelResponse,
  ForecastResponse,
  Alert,
  SyncRunResult,
  SyncLog,
  Granularity,
  TopMetric,
} from './types';

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  auth?: boolean;
  raw?: boolean; // skip JSON parsing, return Response
}

function buildUrl(path: string, query?: Record<string, unknown>): string {
  const base = env.apiBaseUrl.replace(/\/$/, '');
  const url = new URL(`${base}${path.startsWith('/') ? path : `/${path}`}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '') continue;
      url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

function redirectToLogin() {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

async function request<T>(
  path: string,
  options: RequestOptions = {},
  query?: Record<string, unknown>,
): Promise<T> {
  const { body, auth = true, raw, headers, ...rest } = options;
  const finalHeaders = new Headers(headers);

  if (auth) {
    const token = getToken();
    if (token) finalHeaders.set('Authorization', `Bearer ${token}`);
  }

  let finalBody: BodyInit | undefined;
  if (body instanceof FormData) {
    finalBody = body;
  } else if (body !== undefined) {
    finalHeaders.set('Content-Type', 'application/json');
    finalBody = JSON.stringify(body);
  }

  const res = await fetch(buildUrl(path, query), {
    ...rest,
    headers: finalHeaders,
    body: finalBody,
  });

  if (res.status === 401) {
    clearToken();
    redirectToLogin();
    throw new ApiError('Your session has expired. Please log in again.', 401);
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (data && typeof data.message === 'string') message = data.message;
    } catch {
      // ignore parse errors, keep default message
    }
    throw new ApiError(message, res.status);
  }

  if (raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export const api = {
  // ── Auth ──
  devToken: (email: string) =>
    request<DevTokenResponse>('/auth/dev-token', {
      method: 'POST',
      body: { email },
      auth: false,
    }),
  me: () => request<Me>('/me'),

  // ── Products ──
  products: (q = '', page = 0, size = 20) =>
    request<Page<Product>>('/products', {}, { q, page, size }),
  createProduct: (input: ProductInput) =>
    request<Product>('/products', { method: 'POST', body: input }),
  updateProduct: (id: string, input: ProductInput) =>
    request<Product>(`/products/${id}`, { method: 'PUT', body: input }),
  deleteProduct: (id: string) =>
    request<void>(`/products/${id}`, { method: 'DELETE' }),
  importProducts: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return request<ImportResult>('/products/import', { method: 'POST', body: fd });
  },
  exportProductsUrl: () => '/products/export',
  exportProducts: () =>
    request<Response>('/products/export', { raw: true }),

  // ── Inventory ──
  inventory: () => request<InventoryRow[]>('/inventory'),
  updateInventory: (productId: string, input: InventoryInput) =>
    request<InventoryRow>(`/inventory/${productId}`, { method: 'PUT', body: input }),

  // ── Orders ──
  orders: (params: {
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  } = {}) =>
    request<Page<OrderSummary>>(
      '/orders',
      {},
      {
        status: params.status,
        from: params.from,
        to: params.to,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    ),
  order: (id: string) => request<OrderDetail>(`/orders/${id}`),

  // ── Analytics ──
  revenue: (granularity: Granularity = 'day', from?: string, to?: string) =>
    request<RevenueResponse>('/analytics/revenue', {}, { granularity, from, to }),
  topProducts: (metric: TopMetric = 'revenue', limit = 5) =>
    request<TopProductsResponse>('/analytics/top-products', {}, { metric, limit }),
  funnel: (from?: string, to?: string) =>
    request<FunnelResponse>('/analytics/funnel', {}, { from, to }),
  forecast: () => request<ForecastResponse>('/analytics/forecast'),

  // ── Alerts ──
  alerts: (unreadOnly = false) =>
    request<Alert[]>('/alerts', {}, { unreadOnly }),
  markAlertRead: (id: string) =>
    request<void>(`/alerts/${id}/read`, { method: 'POST' }),
  markAllAlertsRead: () =>
    request<void>('/alerts/read-all', { method: 'POST' }),

  // ── Sync ──
  runSync: () => request<SyncRunResult>('/sync/run', { method: 'POST' }),
  syncLogs: () => request<SyncLog[]>('/sync/logs'),
};

export { request };
