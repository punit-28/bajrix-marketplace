-- Sample data. Scenarios covered:
--  * same product sold by several sellers at different prices / stock / MOQ
--  * out-of-stock, stock below MOQ, and stopped (INACTIVE) listings
--  * APPROVED, PENDING and REJECTED sellers (PENDING/REJECTED offers are hidden from buyers)
--  * products with no offers at all, and products offered ONLY by non-approved sellers

INSERT INTO categories (name) VALUES
  ('Cement'), ('Steel & TMT'), ('Bricks & Blocks'), ('Sand & Aggregates'),
  ('Plumbing'), ('Electrical'), ('Paints & Putty'), ('Tiles & Flooring');

INSERT INTO sellers (name, city, status) VALUES
  ('Shree Traders',            'Pune',      'APPROVED'),
  ('Kashmir Build Mart',       'Srinagar',  'APPROVED'),
  ('Om Sai Enterprises',       'Mumbai',    'APPROVED'),
  ('Bharat Hardware & Steel',  'Delhi',     'APPROVED'),
  ('Patel Building Supplies',  'Ahmedabad', 'APPROVED'),
  ('Gupta & Sons Traders',     'Jaipur',    'PENDING'),
  ('Royal Construction Depot', 'Lucknow',   'PENDING'),
  ('Quick Cement Depot',       'Nagpur',    'REJECTED');

INSERT INTO products (sku, category_id, name, brand, unit, description)
SELECT v.sku, c.id, v.name, v.brand, v.unit, v.descr
FROM (VALUES
  ('CEM-ULT-PPC-50',    'Cement', 'UltraTech PPC Cement 50 kg',            'UltraTech',   'bag',      'Portland Pozzolana Cement for general construction and plastering.'),
  ('CEM-ACC-PPC-50',    'Cement', 'ACC Gold Water Shield PPC 50 kg',       'ACC',         'bag',      'Water-repellent PPC for slabs, columns and plaster.'),
  ('CEM-AMB-PPC-50',    'Cement', 'Ambuja Plus PPC Cement 50 kg',          'Ambuja',      'bag',      'PPC with improved durability for RCC work.'),
  ('CEM-DAL-OPC53-50',  'Cement', 'Dalmia OPC 53 Grade Cement 50 kg',      'Dalmia',      'bag',      'High-strength OPC for beams, slabs and precast work.'),
  ('CEM-JKL-PPC-50',    'Cement', 'JK Lakshmi Pro+ PPC Cement 50 kg',      'JK Lakshmi',  'bag',      'PPC cement for residential construction.'),
  ('STL-TIS-8',         'Steel & TMT', 'Tata Tiscon 500D TMT Bar 8 mm',    'Tata Tiscon', 'tonne',    'Fe 500D earthquake-resistant TMT bar.'),
  ('STL-TIS-12',        'Steel & TMT', 'Tata Tiscon 500D TMT Bar 12 mm',   'Tata Tiscon', 'tonne',    'Fe 500D earthquake-resistant TMT bar.'),
  ('STL-JSW-10',        'Steel & TMT', 'JSW Neosteel 500D TMT Bar 10 mm',  'JSW',         'tonne',    'Fe 500D corrosion-resistant TMT bar.'),
  ('STL-SAIL-16',       'Steel & TMT', 'SAIL 500D TMT Bar 16 mm',          'SAIL',        'tonne',    'Fe 500D TMT bar for heavy structural work.'),
  ('STL-BIND-18',       'Steel & TMT', 'Binding Wire 18 Gauge',            'Generic',     'kg',       'Annealed GI binding wire for rebar tying.'),
  ('BRK-RED-1',         'Bricks & Blocks', 'First Class Red Clay Brick',   'Local Kiln',  'piece',    'Standard wire-cut red brick, 9 x 4 x 3 inch.'),
  ('BRK-AAC-100',       'Bricks & Blocks', 'AAC Block 600x200x100 mm',     'Siporex',     'piece',    'Lightweight autoclaved aerated concrete block.'),
  ('BRK-FLY-9',         'Bricks & Blocks', 'Fly Ash Brick 9 inch',         'Local Kiln',  'piece',    'Fly ash brick for load-bearing and partition walls.'),
  ('SND-RIV',           'Sand & Aggregates', 'River Sand (Zone II)',       'Local',       'cubic ft', 'Washed river sand for masonry and plaster.'),
  ('SND-MSAND',         'Sand & Aggregates', 'M-Sand Plastering Grade',    'Local',       'cubic ft', 'Manufactured sand, plastering grade.'),
  ('AGG-20MM',          'Sand & Aggregates', '20 mm Crushed Stone Aggregate', 'Local',     'cubic ft', 'Graded aggregate for concrete.'),
  ('PLB-AST-CPVC-1',    'Plumbing', 'Astral CPVC Pipe 1 inch (3 m)',       'Astral',      'piece',    'CPVC hot and cold water pipe.'),
  ('PLB-SUP-PVC-4',     'Plumbing', 'Supreme PVC Pipe 4 inch (6 m)',       'Supreme',     'piece',    'SWR drainage pipe.'),
  ('PLB-ASH-CPVC-34',   'Plumbing', 'Ashirvad CPVC Pipe 3/4 inch (3 m)',   'Ashirvad',    'piece',    'CPVC hot and cold water pipe.'),
  ('ELC-HAV-15',        'Electrical', 'Havells FRLS Wire 1.5 sq mm (90 m)','Havells',     'coil',     'Flame retardant house wire.'),
  ('ELC-POL-25',        'Electrical', 'Polycab FRLS Wire 2.5 sq mm (90 m)','Polycab',     'coil',     'Flame retardant house wire.'),
  ('ELC-LEG-MCB-32',    'Electrical', 'Legrand MCB 32 A Single Pole',      'Legrand',     'piece',    'Miniature circuit breaker, C curve.'),
  ('PNT-AP-ULT-20',     'Paints & Putty', 'Asian Paints Apex Ultima 20 L', 'Asian Paints','bucket',   'Weatherproof exterior emulsion.'),
  ('PNT-BER-WC-20',     'Paints & Putty', 'Berger WeatherCoat Long Life 20 L','Berger',   'bucket',   'Exterior emulsion with long life protection.'),
  ('PNT-BIRLA-PUTTY-40','Paints & Putty', 'Birla White WallCare Putty 40 kg','Birla White','bag',     'White cement based wall putty.'),
  ('TIL-KAJ-600',       'Tiles & Flooring', 'Kajaria Glazed Vitrified Tile 600x600 mm','Kajaria','box','Box of 4 tiles.'),
  ('TIL-SOM-600',       'Tiles & Flooring', 'Somany Duragres Tile 600x600 mm','Somany',   'box',      'Box of 4 tiles.')
) AS v(sku, category, name, brand, unit, descr)
JOIN categories c ON c.name = v.category;

INSERT INTO seller_listings (product_id, seller_id, price, stock, min_order_qty, status)
SELECT p.id, s.id, v.price, v.stock, v.moq, v.status
FROM (VALUES
  -- UltraTech PPC: full showcase (best available 385 @ Kashmir; Patel cheapest but out of stock;
  -- Gupta/Quick hidden because not approved; Bharat stopped selling)
  ('CEM-ULT-PPC-50', 'Shree Traders',           390.00,  500, 10, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Kashmir Build Mart',      385.00,  120, 20, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Om Sai Enterprises',      405.00, 2000,  5, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Patel Building Supplies', 380.00,    0, 10, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Gupta & Sons Traders',    370.00,  800, 10, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Quick Cement Depot',      360.00,  999,  1, 'ACTIVE'),
  ('CEM-ULT-PPC-50', 'Bharat Hardware & Steel', 388.00,  100, 10, 'INACTIVE'),
  ('CEM-ACC-PPC-50', 'Shree Traders',           372.00,  300, 10, 'ACTIVE'),
  ('CEM-ACC-PPC-50', 'Om Sai Enterprises',      368.00, 1500, 20, 'ACTIVE'),
  ('CEM-ACC-PPC-50', 'Bharat Hardware & Steel', 375.00,   50,  5, 'ACTIVE'),
  ('CEM-AMB-PPC-50', 'Kashmir Build Mart',      395.00,  200, 10, 'ACTIVE'),
  ('CEM-AMB-PPC-50', 'Patel Building Supplies', 389.00,  640, 10, 'ACTIVE'),
  ('CEM-DAL-OPC53-50','Shree Traders',          410.00,  100, 20, 'ACTIVE'),
  -- Steel
  ('STL-TIS-8',  'Bharat Hardware & Steel', 61500.00,  40, 2, 'ACTIVE'),
  ('STL-TIS-8',  'Om Sai Enterprises',      60800.00,  25, 5, 'ACTIVE'),
  ('STL-TIS-8',  'Patel Building Supplies', 62200.00,  12, 1, 'ACTIVE'),
  ('STL-TIS-12', 'Bharat Hardware & Steel', 60900.00,  30, 2, 'ACTIVE'),
  ('STL-TIS-12', 'Patel Building Supplies', 61400.00,   8, 1, 'ACTIVE'),
  ('STL-TIS-12', 'Shree Traders',           60100.00,   3, 5, 'ACTIVE'),  -- stock below MOQ
  ('STL-JSW-10', 'Om Sai Enterprises',      59900.00,  60, 3, 'ACTIVE'),
  ('STL-JSW-10', 'Kashmir Build Mart',      60500.00,  15, 2, 'ACTIVE'),
  ('STL-SAIL-16','Bharat Hardware & Steel', 58700.00, 100, 5, 'ACTIVE'),
  ('STL-SAIL-16','Gupta & Sons Traders',    57900.00, 200, 5, 'ACTIVE'),
  ('STL-BIND-18','Bharat Hardware & Steel',    78.00, 400, 25, 'ACTIVE'),
  ('STL-BIND-18','Shree Traders',              82.00, 250, 10, 'ACTIVE'),
  ('STL-BIND-18','Om Sai Enterprises',         75.00,   0, 25, 'ACTIVE'),
  -- Bricks & blocks
  ('BRK-RED-1',  'Shree Traders',            9.50, 20000, 1000, 'ACTIVE'),
  ('BRK-RED-1',  'Kashmir Build Mart',      10.00,  5000,  500, 'ACTIVE'),
  ('BRK-RED-1',  'Patel Building Supplies',  9.00, 50000, 2000, 'ACTIVE'),
  ('BRK-AAC-100','Om Sai Enterprises',      58.00,  3000,  200, 'ACTIVE'),
  ('BRK-AAC-100','Patel Building Supplies', 55.50,  8000,  500, 'ACTIVE'),
  ('BRK-AAC-100','Shree Traders',           61.00,   900,  100, 'ACTIVE'),
  ('BRK-FLY-9',  'Kashmir Build Mart',       7.50, 12000, 1000, 'ACTIVE'),
  -- Sand & aggregates
  ('SND-RIV',    'Patel Building Supplies', 62.00, 1500, 100, 'ACTIVE'),
  ('SND-RIV',    'Shree Traders',           68.00,  800,  50, 'ACTIVE'),
  ('SND-RIV',    'Kashmir Build Mart',      75.00,  300,  50, 'ACTIVE'),
  ('SND-MSAND',  'Shree Traders',           45.00, 5000, 100, 'ACTIVE'),
  ('SND-MSAND',  'Patel Building Supplies', 42.00,  900, 100, 'ACTIVE'),
  ('SND-MSAND',  'Om Sai Enterprises',      48.00,    0, 100, 'ACTIVE'),
  ('AGG-20MM',   'Shree Traders',           38.00, 4000, 100, 'ACTIVE'),
  ('AGG-20MM',   'Bharat Hardware & Steel', 36.00, 2500, 200, 'INACTIVE'),
  -- Plumbing
  ('PLB-AST-CPVC-1',  'Bharat Hardware & Steel', 610.00, 200, 5, 'ACTIVE'),
  ('PLB-AST-CPVC-1',  'Kashmir Build Mart',      640.00,  80, 2, 'ACTIVE'),
  ('PLB-AST-CPVC-1',  'Om Sai Enterprises',      598.00,   0, 5, 'ACTIVE'),
  ('PLB-SUP-PVC-4',   'Shree Traders',          1240.00,  60, 2, 'ACTIVE'),
  ('PLB-SUP-PVC-4',   'Patel Building Supplies',1195.00,  45, 4, 'ACTIVE'),
  ('PLB-ASH-CPVC-34', 'Kashmir Build Mart',      385.00, 150, 10, 'ACTIVE'),
  -- Electrical
  ('ELC-HAV-15', 'Bharat Hardware & Steel', 1850.00,  45, 1, 'ACTIVE'),
  ('ELC-HAV-15', 'Om Sai Enterprises',      1799.00, 120, 2, 'ACTIVE'),
  ('ELC-HAV-15', 'Shree Traders',           1930.00,  10, 1, 'ACTIVE'),
  ('ELC-POL-25', 'Om Sai Enterprises',      2980.00,  60, 1, 'ACTIVE'),
  ('ELC-POL-25', 'Bharat Hardware & Steel', 3050.00,  20, 1, 'ACTIVE'),
  ('ELC-POL-25', 'Patel Building Supplies', 2940.00,   0, 1, 'ACTIVE'),
  ('ELC-LEG-MCB-32','Bharat Hardware & Steel', 285.00, 500,  5, 'ACTIVE'),
  ('ELC-LEG-MCB-32','Om Sai Enterprises',      270.00, 300, 10, 'ACTIVE'),
  -- Paints (Birla putty is offered only by non-approved sellers => invisible to buyers)
  ('PNT-AP-ULT-20',     'Patel Building Supplies', 8990.00,  25, 1, 'ACTIVE'),
  ('PNT-AP-ULT-20',     'Shree Traders',           9200.00,  12, 1, 'ACTIVE'),
  ('PNT-AP-ULT-20',     'Kashmir Build Mart',      9350.00,   6, 1, 'ACTIVE'),
  ('PNT-BER-WC-20',     'Om Sai Enterprises',      7450.00,  10, 1, 'ACTIVE'),
  ('PNT-BIRLA-PUTTY-40','Gupta & Sons Traders',    1080.00, 100, 5, 'ACTIVE'),
  ('PNT-BIRLA-PUTTY-40','Quick Cement Depot',      1050.00,  40, 5, 'ACTIVE'),
  -- Tiles
  ('TIL-KAJ-600','Patel Building Supplies', 780.00, 300, 10, 'ACTIVE'),
  ('TIL-KAJ-600','Shree Traders',           810.00, 150,  5, 'ACTIVE'),
  ('TIL-KAJ-600','Om Sai Enterprises',      795.00,  40, 10, 'ACTIVE'),
  ('TIL-SOM-600','Kashmir Build Mart',      640.00, 220, 10, 'ACTIVE')
) AS v(sku, seller, price, stock, moq, status)
JOIN products p ON p.sku = v.sku
JOIN sellers  s ON s.name = v.seller;

-- Initial fill of the denormalised buyer-facing summary (same rule the application uses:
-- ACTIVE listing + APPROVED seller; "available" = stock >= min_order_qty).
WITH agg AS (
    SELECT l.product_id,
           COUNT(*)                                                        AS offer_count,
           COUNT(*) FILTER (WHERE l.stock >= l.min_order_qty)              AS available_count,
           MIN(l.price) FILTER (WHERE l.stock >= l.min_order_qty)          AS min_price,
           MAX(l.price) FILTER (WHERE l.stock >= l.min_order_qty)          AS max_price
    FROM seller_listings l
    JOIN sellers s ON s.id = l.seller_id
    WHERE l.status = 'ACTIVE' AND s.status = 'APPROVED'
    GROUP BY l.product_id
)
UPDATE products p
SET offer_count = COALESCE(a.offer_count, 0),
    available_offer_count = COALESCE(a.available_count, 0),
    min_price = a.min_price,
    max_price = a.max_price
FROM products t
LEFT JOIN agg a ON a.product_id = t.id
WHERE p.id = t.id;
