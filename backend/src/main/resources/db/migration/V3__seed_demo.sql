-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Demo seed data. Two merchants with overlapping SKUs prove tenant isolation
-- (each only ever sees its own rows). Orders are scattered across the last 60
-- days so the analytics endpoints have real trends to render.
--
-- Dev login: POST /api/auth/dev-token { "email": "demo@merchanthub.dev" }
--            (also: rival@merchanthub.dev for the second tenant)
-- Runs as postgres (superuser) so RLS does not block the seed.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO merchants (id, auth_user_id, name, email, shop_api_key) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-0000000000a1', 'Acme Outfitters', 'demo@merchanthub.dev',  'demo-shop-key-acme'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '00000000-0000-0000-0000-0000000000b1', 'Globex Gadgets',  'rival@merchanthub.dev', 'demo-shop-key-globex');

-- ── Products ─────────────────────────────────────────────────────────────────
INSERT INTO products (merchant_id, sku, name, description, price, external_id, image_url) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TEE-001',  'Classic Cotton Tee',    'Soft 100% cotton t-shirt',        19.99, 'TEE-001',  null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'HOOD-002', 'Zip Hoodie',            'Fleece-lined zip hoodie',         49.99, 'HOOD-002', null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CAP-003',  'Trail Cap',             'Adjustable outdoor cap',          24.50, 'CAP-003',  null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SOCK-004', 'Merino Socks 3-Pack',   'Warm merino wool socks',          18.00, 'SOCK-004', null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'JKT-005',  'Rain Jacket',           'Waterproof shell jacket',        119.00, 'JKT-005',  null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BAG-006',  'Daypack 20L',           'Lightweight hiking daypack',      79.00, 'BAG-006',  null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BTL-007',  'Insulated Bottle',      'Stainless 750ml bottle',          29.99, 'BTL-007',  null),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'GLV-008',  'Touch Gloves',          'Touchscreen winter gloves',       22.00, 'GLV-008',  null),
  -- second tenant (overlapping SKUs on purpose)
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'TEE-001',  'Gadget Branded Tee',    'Promo tee',                       14.99, 'TEE-001',  null),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'USB-100',  'USB-C Cable 2m',        'Braided USB-C cable',              12.99, 'USB-100',  null),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PWR-101',  'Power Bank 10k',        '10000mAh power bank',             39.99, 'PWR-101',  null),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'HUB-102',  '7-Port USB Hub',        'Powered USB hub',                 45.00, 'HUB-102',  null);

-- ── Inventory (one low-stock item per merchant to exercise alerts) ───────────
INSERT INTO inventory (merchant_id, product_id, quantity, low_stock_threshold)
SELECT p.merchant_id, p.id,
       CASE WHEN p.sku IN ('JKT-005','PWR-101') THEN 3 ELSE 20 + (random()*180)::int END,
       5
FROM products p;

-- ── Orders + items for Acme (merchant A) ─────────────────────────────────────
WITH m AS (SELECT id FROM merchants WHERE email = 'demo@merchanthub.dev'),
ins AS (
  INSERT INTO orders (merchant_id, external_id, total, status, customer_email, created_at)
  SELECT (SELECT id FROM m),
         'seedA-' || g,
         0,
         (ARRAY['paid','fulfilled','fulfilled','fulfilled','created','abandoned','cancelled'])[1 + floor(random()*7)::int],
         'customer' || g || '@example.com',
         now() - (random()*60 || ' days')::interval
  FROM generate_series(1, 600) g
  RETURNING id, merchant_id
)
INSERT INTO order_items (merchant_id, order_id, product_id, sku, quantity, unit_price)
SELECT o.merchant_id, o.id, p.id, p.sku, 1 + floor(random()*3)::int, p.price
FROM ins o
CROSS JOIN LATERAL (
  SELECT id, sku, price FROM products
  WHERE merchant_id = o.merchant_id
  ORDER BY random()
  LIMIT 1 + floor(random()*3)::int
) p;

-- ── Orders + items for Globex (merchant B) ───────────────────────────────────
WITH m AS (SELECT id FROM merchants WHERE email = 'rival@merchanthub.dev'),
ins AS (
  INSERT INTO orders (merchant_id, external_id, total, status, customer_email, created_at)
  SELECT (SELECT id FROM m),
         'seedB-' || g,
         0,
         (ARRAY['paid','fulfilled','fulfilled','created','abandoned'])[1 + floor(random()*5)::int],
         'buyer' || g || '@example.com',
         now() - (random()*60 || ' days')::interval
  FROM generate_series(1, 180) g
  RETURNING id, merchant_id
)
INSERT INTO order_items (merchant_id, order_id, product_id, sku, quantity, unit_price)
SELECT o.merchant_id, o.id, p.id, p.sku, 1 + floor(random()*2)::int, p.price
FROM ins o
CROSS JOIN LATERAL (
  SELECT id, sku, price FROM products
  WHERE merchant_id = o.merchant_id
  ORDER BY random()
  LIMIT 1 + floor(random()*2)::int
) p;

-- ── Roll item totals up onto the orders ──────────────────────────────────────
UPDATE orders o
SET total = sub.t
FROM (SELECT order_id, sum(quantity * unit_price) AS t FROM order_items GROUP BY order_id) sub
WHERE o.id = sub.order_id;

-- ── A couple of starter alerts ───────────────────────────────────────────────
INSERT INTO alerts (merchant_id, type, payload)
SELECT i.merchant_id, 'low_stock',
       jsonb_build_object('product_id', i.product_id, 'sku', p.sku, 'quantity', i.quantity, 'threshold', i.low_stock_threshold)
FROM inventory i JOIN products p ON p.id = i.product_id
WHERE i.quantity <= i.low_stock_threshold;
