-- ─────────────────────────────────────────────────────────────────────────────
-- V4: Transactional Outbox.
--
-- Domain events are written to this table IN THE SAME TRANSACTION as the business
-- change that produced them (e.g. an order insert + its OrderIngested event commit
-- atomically). A background publisher then relays unpublished rows to Kafka and
-- marks them published. This guarantees an event is never lost or emitted for a
-- change that rolled back — the classic outbox pattern for reliable messaging.
--
-- NOTE: this is INFRASTRUCTURE, not tenant data, so it intentionally has NO RLS:
-- the publisher polls across all tenants on a background thread with no tenant
-- context. (The merchant id still travels inside the event payload/key.)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE outbox_events (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  topic        text NOT NULL,
  event_key    text,
  event_type   text NOT NULL,
  payload      jsonb NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz
);

-- Fast lookup of the unpublished backlog, oldest first.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
  WHERE published_at IS NULL;
