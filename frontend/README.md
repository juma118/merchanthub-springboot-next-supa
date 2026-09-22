# MerchantHub Frontend

Next.js 14 (App Router) dashboard for the MerchantHub multi-tenant e-commerce
analytics platform. Built with React 18, TypeScript, Tailwind CSS, and Recharts.

## Quick start (dev)

```bash
cp .env.local.example .env.local   # then edit if needed
npm install
npm run dev
```

App runs at http://localhost:3000.

### Logging in

The login page has **Log in** and **Create account** tabs, backed by the backend's
self-contained JWT auth (`POST /auth/login`, `POST /auth/register`). Two demo tenants are
seeded: `demo@merchanthub.dev` and `rival@merchanthub.dev`, both with password `demo1234` —
useful for confirming neither merchant can see the other's data.

## Environment variables

All are `NEXT_PUBLIC_` and inlined at **build time**.

| Variable | Required | Description |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | yes | Backend REST base, e.g. `http://localhost:8080/api` |

The app polls `GET /alerts?unreadOnly=true` every 10 seconds for new alerts and toasts on
anything unseen — there's no external realtime dependency.

## Scripts

- `npm run dev` — start the dev server
- `npm run build` — production build (Next standalone output)
- `npm run start` — run the production build
- `npm run lint` — lint

## Docker

The `Dockerfile` is a multi-stage Node 20 alpine build producing the Next
standalone server. Because `NEXT_PUBLIC_*` vars are inlined at build time, pass
them as a build arg:

```bash
docker build \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api \
  -t merchanthub-frontend .

docker run -p 3000:3000 merchanthub-frontend
```

## Project structure

```
app/
  (app)/            authenticated shell (sidebar + topbar + guard)
    dashboard/      KPIs, revenue chart, top products, funnel
    products/       searchable table, CRUD modal, CSV import/export
    inventory/      inline-editable quantity & threshold
    orders/         filterable table + detail drawer
    alerts/         alert list with read/unread
    sync/           run sync + sync logs
  login/            log in / create account
  page.tsx          redirect to /dashboard or /login
components/         Sidebar, Topbar, Toaster, charts, shared UI primitives
lib/                api client, types, auth hook, alerts polling hook
```
