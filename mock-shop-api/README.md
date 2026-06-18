# MerchantHub Mock Shop API

A small standalone Node.js service that emulates a **ShopRenter-style external shop API**.
It exists so the MerchantHub Spring Boot backend can demo both ingestion styles:

- **Push** — `POST /shop/simulate/order` generates an order and delivers it to the backend
  as a signed webhook (HMAC-SHA256).
- **Pull** — `GET /shop/products` and `GET /shop/orders` provide a catalog and recent orders
  for the backend's scheduled sync job.

The catalog is fixed and in-memory. Some SKUs (`TEE-001`, `HOOD-002`, `CAP-003`, `BTL-007`)
overlap the backend's seed data, so a sync **updates** them. `SHOP-900` and `SHOP-901` are new,
so a sync demonstrates **product creation**.

## Requirements

- Node.js 18+ (uses the built-in `crypto` and global `fetch`).
- Only runtime dependency: `express`.

## Environment variables

| Variable              | Default                                        | Purpose                                              |
| --------------------- | ---------------------------------------------- | ---------------------------------------------------- |
| `PORT`                | `4000`                                          | HTTP port to listen on.                              |
| `BACKEND_WEBHOOK_URL` | `http://backend:8080/api/webhooks/orders`       | Where simulated-order webhooks are POSTed.           |
| `WEBHOOK_SECRET`      | `dev-webhook-hmac-secret-change-me`             | HMAC-SHA256 key used to sign webhook payloads.       |

## Running

```bash
npm install
npm start
# or with Docker:
docker build -t merchanthub-mock-shop-api .
docker run -p 4000:4000 merchanthub-mock-shop-api
```

## Endpoints

### `GET /health`

Liveness check. No auth.

```bash
curl http://localhost:4000/health
# {"status":"ok"}
```

### `GET /shop/products`

Returns the fixed catalog. Requires a non-empty `X-Api-Key` header (any value).

```bash
curl http://localhost:4000/shop/products -H "X-Api-Key: demo-shop-key-acme"
# {"products":[{"external_id":"TEE-001","sku":"TEE-001",...}, ...]}
```

Missing key returns `401 {"error":"missing api key"}`.

### `GET /shop/orders?since=<ISO8601>`

Returns 5 freshly generated recent orders, created within the last 48h (or after `since`
if provided). Requires `X-Api-Key`. Each order's `total` equals the sum of
`quantity * unit_price` across its items.

```bash
curl "http://localhost:4000/shop/orders?since=2026-06-13T00:00:00Z" \
  -H "X-Api-Key: demo-shop-key-acme"
# {"orders":[{"external_id":"ORD-...","total":...,"items":[...]}, ...]}
```

### `POST /shop/simulate/order?apiKey=<key>`

Generates ONE random order from the catalog and delivers it to the backend as a signed
webhook. `apiKey` query param defaults to `demo-shop-key-acme`.

```bash
curl -X POST "http://localhost:4000/shop/simulate/order?apiKey=demo-shop-key-acme"
# {"delivered":true,"status":200,"order":{...}}
```

If the backend call throws (e.g. backend not reachable), the response is
`{"delivered":false,"error":"<message>","order":{...}}` and the process does **not** crash.

## Webhook signature

When simulating an order the service builds this JSON body:

```json
{
  "apiKey": "<the apiKey>",
  "order": {
    "external_id": "...",
    "total": 0,
    "currency": "USD",
    "status": "created|paid|fulfilled",
    "customer_email": "...",
    "created_at": "<ISO8601>",
    "items": [{ "sku": "...", "quantity": 0, "unit_price": 0 }]
  }
}
```

It serializes that object to a string `raw` and computes:

```js
signature = crypto.createHmac('sha256', WEBHOOK_SECRET).update(raw).digest('hex');
```

It then POSTs `raw` to `BACKEND_WEBHOOK_URL` with headers:

- `Content-Type: application/json`
- `X-Shop-Signature: <signature>`
- `X-Api-Key: <apiKey>`

The backend recomputes the HMAC over the exact received body using the same shared
`WEBHOOK_SECRET` and compares it to `X-Shop-Signature` to verify authenticity.
