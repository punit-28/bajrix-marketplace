-- OPTIONAL load generator for checking query plans at scale (not a migration).
--
--   psql "$DATABASE_URL" -v products=1000000 -v sellers=100000 -v per_product=10 -f scripts/bulk_data.sql
--
-- Defaults (small) are used when the variables are not given. Safe to run once on top of the seed data.
-- Afterwards try:  EXPLAIN ANALYZE SELECT ... (see README "Checking performance").

\if :{?products} \else \set products 100000 \endif
\if :{?sellers} \else \set sellers 5000 \endif
\if :{?per_product} \else \set per_product 10 \endif

\timing on

INSERT INTO sellers (name, city, status)
SELECT 'Bulk Seller ' || g,
       (ARRAY['Pune','Delhi','Mumbai','Jaipur','Srinagar','Lucknow','Nagpur','Surat'])[1 + g % 8],
       CASE WHEN g % 100 < 90 THEN 'APPROVED' WHEN g % 100 < 97 THEN 'PENDING' ELSE 'REJECTED' END
FROM generate_series(1, :sellers) g;

SELECT min(id) AS seller_base FROM sellers WHERE name LIKE 'Bulk Seller %' \gset

WITH cats AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM categories)
INSERT INTO products (sku, category_id, name, brand, unit, description)
SELECT 'BULK-' || g,
       c.id,
       (ARRAY['Cement','TMT Bar','Vitrified Tile','Exterior Paint','CPVC Pipe','FRLS Wire','Clay Brick','Plaster Sand'])[1 + g % 8]
         || ' Grade ' || (g % 97) || ' Lot ' || g,
       (ARRAY['UltraTech','Tata','Kajaria','Asian Paints','Astral','Havells','Local Kiln','Generic','ACC','JSW'])[1 + g % 10],
       'piece',
       NULL
FROM generate_series(1, :products) g
JOIN cats c ON c.rn = 1 + g % 8;

INSERT INTO seller_listings (product_id, seller_id, price, stock, min_order_qty, status)
SELECT p.id,
       :seller_base + ((p.id * 7919 + k * 104729) % :sellers),
       round((50 + (p.id % 500) * 3 + (k * 7 % 41))::numeric, 2),
       (k * 37 + p.id) % 2000,
       1 + (k % 20),
       CASE WHEN k % 15 = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM products p
CROSS JOIN generate_series(1, :per_product) k
WHERE p.sku LIKE 'BULK-%'
ON CONFLICT (product_id, seller_id) DO NOTHING;

-- Fill the buyer-facing summary (same rule as ProductSummaryRepository).
WITH agg AS (
    SELECT l.product_id,
           COUNT(*)                                                       AS offer_count,
           COUNT(*) FILTER (WHERE l.stock >= l.min_order_qty)             AS available_count,
           MIN(l.price) FILTER (WHERE l.stock >= l.min_order_qty)         AS min_price,
           MAX(l.price) FILTER (WHERE l.stock >= l.min_order_qty)         AS max_price
    FROM seller_listings l
    JOIN sellers s ON s.id = l.seller_id
    WHERE l.status = 'ACTIVE' AND s.status = 'APPROVED'
    GROUP BY l.product_id
)
UPDATE products p
SET offer_count = a.offer_count,
    available_offer_count = a.available_count,
    min_price = a.min_price,
    max_price = a.max_price
FROM agg a
WHERE a.product_id = p.id AND p.sku LIKE 'BULK-%';

ANALYZE sellers;
ANALYZE products;
ANALYZE seller_listings;
