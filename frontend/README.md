# MerchantHub Frontend

Next.js 14 (App Router) dashboard for the MerchantHub multi-tenant e-commerce
analytics platform. Built with React 18, TypeScript, Tailwind CSS, Recharts, and
optional Supabase Auth + Realtime.

## Quick start (dev)

```bash
cp .env.local.example .env.local   # then edit if needed
npm install
npm run dev
```

App runs at http://localhost:3000.

### Logging in

The login page has a **Developer login** form. Enter an email (defaults to
`demo@merchanthub.dev`) and click **Log in** — this calls
`POST {API}/auth/dev-token` and stores the returned JWT. A second demo tenant is
available as `rival@merchanthub.dev`.

If Supabase is configured (see below), an additional email/password form appears
that uses `supabase.auth.signInWithPassword`.

## Environment variables

All are `NEXT_PUBLIC_` and inlined at **build time**.

| Variable | Required | Description |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | yes | Backend REST base, e.g. `http://localhost:8080/api` |
| `NEXT_PUBLIC_SUPABASE_URL` | no | Supabase project URL. Blank → dev-token auth + polling |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | no | Supabase anon key. Blank → dev-token auth + polling |

When the Supabase vars are empty the app works fully using dev-token auth and
polls `GET /alerts?unreadOnly=true` every 10 seconds for new alerts. When they
are set, the app subscribes to Postgres `INSERT` changes on the `alerts` and
`orders` tables for live toasts.

## Scripts

- `npm run dev` — start the dev server
- `npm run build` — production build (Next standalone output)
- `npm run start` — run the production build
- `npm run lint` — lint

## Docker

The `Dockerfile` is a multi-stage Node 20 alpine build producing the Next
standalone server. Because `NEXT_PUBLIC_*` vars are inlined at build time, pass
them as build args:

```bash
docker build \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api \
  --build-arg NEXT_PUBLIC_SUPABASE_URL= \
  --build-arg NEXT_PUBLIC_SUPABASE_ANON_KEY= \
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
  login/            developer + optional Supabase login
  page.tsx          redirect to /dashboard or /login
components/         Sidebar, Topbar, Toaster, charts, shared UI primitives
lib/                api client, types, auth hook, realtime/alerts hook, supabase
```
