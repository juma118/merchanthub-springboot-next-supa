// Centralized API types matching the MerchantHub REST contract.

export interface Merchant {
  id: string;
  name: string;
  email: string;
}

export interface DevTokenResponse {
  token: string;
  tokenType: 'Bearer';
  merchant: Merchant;
}

export interface Me {
  id: string;
  name: string;
  email: string;
  shopApiKey: string;
}

export interface Inventory {
  quantity: number;
  lowStockThreshold: number;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  imageUrl: string | null;
  externalId: string | null;
  createdAt: string;
  updatedAt: string;
  inventory: Inventory;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductInput {
  sku: string;
  name: string;
  description: string;
  price: number;
  imageUrl: string;
  quantity: number;
  lowStockThreshold: number;
}

export interface ImportResult {
  imported: number;
  updated: number;
  errors: string[];
}

export interface InventoryRow {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
}

export interface InventoryInput {
  quantity: number;
  lowStockThreshold: number;
}

export type OrderStatus =
  | 'created'
  | 'paid'
  | 'fulfilled'
  | 'cancelled'
  | 'abandoned'
  | string;

export interface OrderSummary {
  id: string;
  externalId: string | null;
  total: number;
  currency: string;
  status: OrderStatus;
  customerEmail: string | null;
  createdAt: string;
  itemCount: number;
}

export interface OrderItem {
  id: string;
  productId: string | null;
  sku: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderDetail {
  id: string;
  externalId: string | null;
  total: number;
  currency: string;
  status: OrderStatus;
  customerEmail: string | null;
  createdAt: string;
  items: OrderItem[];
}

export type Granularity = 'day' | 'week' | 'month';

export interface RevenuePoint {
  bucket: string;
  revenue: number;
  orders: number;
}

export interface RevenueResponse {
  granularity: Granularity;
  series: RevenuePoint[];
  totalRevenue: number;
  totalOrders: number;
  previous: { totalRevenue: number; totalOrders: number };
  changePct: number;
}

export type TopMetric = 'units' | 'revenue';

export interface TopProductItem {
  productId: string;
  sku: string;
  name: string;
  units: number;
  revenue: number;
}

export interface TopProductsResponse {
  metric: TopMetric;
  items: TopProductItem[];
}

export interface FunnelResponse {
  created: number;
  paid: number;
  fulfilled: number;
  cancelled: number;
  abandoned: number;
  abandonedRate: number;
}

export type ForecastStatus = 'ok' | 'low' | 'critical';

export interface ForecastItem {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  avgDailyUnits: number;
  daysToStockout: number | null;
  status: ForecastStatus;
}

export interface ForecastResponse {
  items: ForecastItem[];
}

export type AlertType =
  | 'new_order'
  | 'low_stock'
  | 'sync_complete'
  | 'sync_failed'
  | string;

export interface Alert {
  id: string;
  type: AlertType;
  payload: Record<string, unknown> | null;
  read: boolean;
  createdAt: string;
}

export type SyncStatus = 'running' | 'success' | 'failed' | string;

export interface SyncRunResult {
  syncLogId: string;
  status: SyncStatus;
  recordsProcessed: number;
}

export interface SyncLog {
  id: string;
  type: string;
  status: SyncStatus;
  detail: string | null;
  recordsProcessed: number;
  startedAt: string;
  finishedAt: string | null;
}
