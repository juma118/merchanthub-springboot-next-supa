# notification-service

A small, independent Spring Boot **microservice** that demonstrates an event-driven
boundary in MerchantHub. It owns one responsibility — turning domain events into
outbound notifications (email/Slack, simulated) — and shares nothing with the main
backend except the **Kafka event contract**.

## How it fits

```
backend  ──(transactional outbox)──▶ Kafka topics ──▶ notification-service
   writes order + event atomically      order.ingested        @KafkaListener
   publisher relays to Kafka            inventory.low-stock   → "sends" email/Slack
```

- The **backend** writes a domain event into an `outbox_events` table in the *same
  DB transaction* as the business change (so the event can't be lost or emitted for a
  rolled-back change). A scheduled publisher relays unpublished rows to Kafka.
- **This service** consumes those topics with a `@KafkaListener`, formats a
  notification, keeps the last 500 in memory, and exposes them over REST.

It has its own process, its own deployable, and **no database** — a clean
single-responsibility service.

## Endpoints

| Method & path | Description |
|---|---|
| `GET /` | Service info + how many notifications are held |
| `GET /notifications` | Recent notifications (newest first) |
| `GET /actuator/health` | Health |

## Config (env vars)

| Var | Default | Purpose |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `NOTIFICATION_PORT` | `8081` | HTTP port |

## Run

It starts automatically with `docker compose up`. To exercise it, trigger an order:

```bash
curl -X POST "http://localhost:4000/shop/simulate/order?apiKey=demo-shop-key-acme"
curl http://localhost:8081/notifications
```
