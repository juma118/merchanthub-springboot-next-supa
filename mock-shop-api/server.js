'use strict';

const express = require('express');
const crypto = require('crypto');

// ---------------------------------------------------------------------------
// Config (env vars with defaults)
// ---------------------------------------------------------------------------
const PORT = parseInt(process.env.PORT || '4000', 10);
const BACKEND_WEBHOOK_URL =
  process.env.BACKEND_WEBHOOK_URL || 'http://backend:8080/api/webhooks/orders';
const WEBHOOK_SECRET =
  process.env.WEBHOOK_SECRET || 'dev-webhook-hmac-secret-change-me';

// ---------------------------------------------------------------------------
// Fixed in-memory catalog
// external_id == sku for each item.
// ---------------------------------------------------------------------------
const CATALOG = [
  {
    external_id: 'TEE-001',
    sku: 'TEE-001',
    name: 'Classic Cotton Tee',
    description: 'Classic Cotton Tee',
    price: 19.99,
    image_url: null,
    quantity: 120,
  },
  {
    external_id: 'HOOD-002',
    sku: 'HOOD-002',
    name: 'Zip Hoodie',
    description: 'Zip Hoodie',
    price: 49.99,
    image_url: null,
    quantity: 60,
  },
  {
    external_id: 'CAP-003',
    sku: 'CAP-003',
    name: 'Trail Cap',
    description: 'Trail Cap',
    price: 24.5,
    image_url: null,
    quantity: 40,
  },
  {
    external_id: 'BTL-007',
    sku: 'BTL-007',
    name: 'Insulated Bottle',
    description: 'Insulated Bottle',
    price: 29.99,
    image_url: null,
    quantity: 75,
  },
  {
    external_id: 'SHOP-900',
    sku: 'SHOP-900',
    name: 'Limited Edition Beanie',
    description: 'Limited Edition Beanie',
    price: 27.0,
    image_url: null,
    quantity: 15,
  },
  {
    external_id: 'SHOP-901',
    sku: 'SHOP-901',
    name: 'Eco Water Bottle',
    description: 'Eco Water Bottle',
    price: 21.5,
    image_url: null,
    quantity: 8,
  },
];

const ORDER_STATUSES = ['created', 'paid', 'fulfilled'];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function round2(n) {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

function randomInt(min, max) {
  // inclusive of both ends
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function randomCustomerEmail() {
  const names = ['alex', 'sam', 'jordan', 'casey', 'taylor', 'morgan', 'riley', 'jamie'];
  const domains = ['example.com', 'mail.test', 'shopper.io'];
  return `${pick(names)}.${randomInt(100, 999)}@${pick(domains)}`;
}

// Build a single random order. `createdAt` is a Date.
function buildRandomOrder(createdAt) {
  const itemCount = randomInt(1, 3);
  const items = [];
  const usedSkus = new Set();

  for (let i = 0; i < itemCount; i++) {
    let product = pick(CATALOG);
    // Avoid duplicate SKUs in the same order for cleaner demo data.
    let guard = 0;
    while (usedSkus.has(product.sku) && guard < 10) {
      product = pick(CATALOG);
      guard++;
    }
    usedSkus.add(product.sku);
    items.push({
      sku: product.sku,
      quantity: randomInt(1, 4),
      unit_price: product.price,
    });
  }

  const total = round2(
    items.reduce((sum, it) => sum + it.quantity * it.unit_price, 0)
  );

  return {
    external_id: `ORD-${Date.now()}-${randomInt(1000, 9999)}`,
    total,
    currency: 'USD',
    status: pick(ORDER_STATUSES),
    customer_email: randomCustomerEmail(),
    created_at: createdAt.toISOString(),
    items,
  };
}

// Generate N orders created within the last 48h (or after `since` if given).
function generateOrders(count, since) {
  const now = Date.now();
  const windowStartMs = 48 * 60 * 60 * 1000; // 48h
  let earliest = now - windowStartMs;

  if (since) {
    const sinceMs = Date.parse(since);
    if (!Number.isNaN(sinceMs) && sinceMs > earliest) {
      earliest = sinceMs;
    }
  }
  // Ensure earliest is strictly before now so we have a range to pick from.
  if (earliest >= now) {
    earliest = now - 60 * 1000;
  }

  const orders = [];
  for (let i = 0; i < count; i++) {
    const ts = randomInt(earliest, now);
    orders.push(buildRandomOrder(new Date(ts)));
  }
  // Sort newest first for a nicer demo.
  orders.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
  return orders;
}

// Deliver a signed webhook to the backend. Returns { delivered, status } or throws.
async function deliverWebhook(apiKey, order) {
  const body = { apiKey, order };
  const raw = JSON.stringify(body);
  const signature = crypto
    .createHmac('sha256', WEBHOOK_SECRET)
    .update(raw)
    .digest('hex');

  const res = await fetch(BACKEND_WEBHOOK_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Shop-Signature': signature,
      'X-Api-Key': apiKey,
    },
    body: raw,
  });

  return res.status;
}

// ---------------------------------------------------------------------------
// App
// ---------------------------------------------------------------------------
const app = express();
app.use(express.json());

// Request logging
app.use((req, _res, next) => {
  console.log(`[req] ${req.method} ${req.path}`);
  next();
});

// Wrap async handlers so rejections become a JSON 500 instead of crashing.
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

// Require a non-empty X-Api-Key header.
function requireApiKey(req, res) {
  const apiKey = req.get('X-Api-Key');
  if (!apiKey || apiKey.trim() === '') {
    res.status(401).json({ error: 'missing api key' });
    return null;
  }
  return apiKey;
}

// GET /health
app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

// GET /shop/products
app.get(
  '/shop/products',
  asyncHandler(async (req, res) => {
    if (!requireApiKey(req, res)) return;
    res.json({ products: CATALOG });
  })
);

// GET /shop/orders?since=<ISO8601>
app.get(
  '/shop/orders',
  asyncHandler(async (req, res) => {
    if (!requireApiKey(req, res)) return;
    const since = typeof req.query.since === 'string' ? req.query.since : undefined;
    const orders = generateOrders(5, since);
    res.json({ orders });
  })
);

// POST /shop/simulate/order?apiKey=<key>
app.post(
  '/shop/simulate/order',
  asyncHandler(async (req, res) => {
    const apiKey =
      (typeof req.query.apiKey === 'string' && req.query.apiKey) ||
      'demo-shop-key-acme';

    const order = buildRandomOrder(new Date());

    try {
      const status = await deliverWebhook(apiKey, order);
      console.log(
        `[webhook] delivered order ${order.external_id} -> ${BACKEND_WEBHOOK_URL} status=${status}`
      );
      res.json({ delivered: true, status, order });
    } catch (err) {
      console.error(
        `[webhook] delivery FAILED for order ${order.external_id}: ${err.message}`
      );
      res.json({ delivered: false, error: err.message, order });
    }
  })
);

// 404 fallback
app.use((_req, res) => {
  res.status(404).json({ error: 'not found' });
});

// Centralized error handler -> JSON 500, never crash.
// eslint-disable-next-line no-unused-vars
app.use((err, _req, res, _next) => {
  console.error(`[error] ${err && err.stack ? err.stack : err}`);
  res.status(500).json({ error: 'internal server error' });
});

// Keep the process alive on unexpected errors.
process.on('unhandledRejection', (reason) => {
  console.error(`[unhandledRejection] ${reason}`);
});
process.on('uncaughtException', (err) => {
  console.error(`[uncaughtException] ${err && err.stack ? err.stack : err}`);
});

app.listen(PORT, () => {
  console.log(`mock-shop-api listening on port ${PORT}`);
  console.log(`  webhook target: ${BACKEND_WEBHOOK_URL}`);
});
