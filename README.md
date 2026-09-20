# BajriX – Multi-Seller Product Marketplace

Buyers browse building-material products and compare what every approved local seller offers
(price, stock, minimum order). Sellers manage their own listings. Stack: **React (Vite)**,
**Java 21 / Spring Boot 3.3**, **PostgreSQL 16** (Flyway migrations + seed data).

## 1. Running it

### Docker (one command)
```bash
docker compose up --build
```
| What | URL |
|---|---|
| UI | http://localhost:3000 |
| API + Swagger UI | http://localhost:8080/swagger-ui.html |
| Postgres | `localhost:5432`, db/user/password `bajrix` |

Flyway creates the schema and loads the sample data on first start.

### Without Docker (three terminals)
```bash
# 1. PostgreSQL 16 with pg_trgm available (default in the official image / most packages)
createdb bajrix   # user bajrix / password bajrix, or override DB_URL, DB_USER, DB_PASSWORD

# 2. Backend  (http://localhost:8080)
cd backend && mvn spring-boot:run

# 3. Frontend (http://localhost:5173, proxies /api to :8080)
cd frontend && npm install && npm run dev
```

### Tests
```bash
cd backend  && mvn test          # unit tests + integration tests (integration needs Docker: Testcontainers)
cd backend  && mvn test -Dtest=ListingServiceTest   # unit tests only, no Docker
cd frontend && npm test
```

### Trying the scenarios (sample data)
Open **Sell on BajriX** and pick a seller (this is the mocked login).

| Scenario | Where to look |
|---|---|
| Same product, many sellers, different price/stock/MOQ | *UltraTech PPC Cement 50 kg*: 4 visible offers, cheapest that can actually supply is ₹385 (Kashmir Build Mart). Patel is cheaper (₹380) but has **0 stock**, so it is shown last and not counted as "lowest". |
| Seller statuses | Gupta & Sons (PENDING) and Quick Cement Depot (REJECTED) also list UltraTech at ₹370/₹360: **never visible to buyers**. *Birla White Putty* is offered only by those two, so it does not appear in browse at all. Royal Construction Depot (PENDING) has no listings (empty state). |
| Stopped listing | Bharat Hardware & Steel stopped selling UltraTech (INACTIVE). |
| Stock below minimum order | Shree Traders, *Tata Tiscon 12 mm*: 3 in stock, minimum order 5 → shown as unavailable. |
| Quantity comparison | On a product page enter a quantity (e.g. 500 bags): totals appear and sellers who cannot supply that quantity drop down. |
| Approve / reject a seller | `curl -X PATCH localhost:8080/api/admin/sellers/7/status -H 'X-Admin-Token: dev-admin-token' -H 'Content-Type: application/json' -d '{"status":"APPROVED"}'` – the seller's offers appear in the buyer view immediately. |
| Optimistic-lock conflict | Open the same listing's edit dialog in two tabs, save in one, then save in the other → "changed by someone else" with *Load latest values*. |

## 2. Architecture and key decisions

**Product vs. listing.** `products` is the catalogue (one row per real-world item, curated by BajriX);
`seller_listings` is one seller's offer for a product (price, stock, min order qty, status).
`UNIQUE (product_id, seller_id)` guarantees a seller has at most one listing per product.
"Stop selling" sets `status = INACTIVE` instead of deleting, so the row, its history and re-listing stay simple.
Sellers cannot create products; "add a product to my catalogue" means listing an existing catalogue product
(avoids 100k sellers creating 100k duplicates of "UltraTech PPC 50 kg").

**Seller status.**
| Status | Buyers see offers | Seller can change listings |
|---|---|---|
| APPROVED | yes | yes |
| PENDING | no | yes (can prepare listings ahead of approval) |
| REJECTED | no | no (read-only, 403 `SELLER_NOT_ALLOWED`) |

The visibility rule is *ACTIVE listing AND APPROVED seller*, and lives in exactly two places that are kept in sync:
the offers query and `ProductSummaryRepository`.

**Offer availability.** An offer is *orderable* when `stock >= min_order_qty`. Out-of-stock and below-MOQ offers are still shown
(greyed, at the bottom) so buyers see the whole market, but they never count as "lowest price".

**Authorization boundary.** Authentication is mocked with an `X-Seller-Id` header, resolved into a `SellerContext` by one argument
resolver, so swapping in real JWT/session auth touches only that class. Every seller query/write is scoped by that id
(`findByIdAndSellerId`, `WHERE seller_id = ?`). Someone else's listing returns **404, not 403**, so ids cannot be probed.
The service re-checks seller status from the database inside the transaction on every write.

**Concurrency.** Two levels:
1. *Listing edits:* `@Version` optimistic locking. Clients send the `version` they loaded; a stale version gets `409 STALE_LISTING`.
   An early check gives a clean message, and the `@Version` column protects the remaining race window (the losing `UPDATE ... WHERE version = ?`
   affects 0 rows). Optimistic rather than pessimistic because conflicts are rare and a human decides how to resolve them
   (last-write-wins would silently overwrite someone's price change). Covered by a test with 8 simultaneous writers: exactly one wins.
2. *Denormalised summary:* see below. Writers take a row lock on the product before recomputing it, otherwise two listings of the
   same product edited concurrently could each recompute without seeing the other's change and leave stale numbers.

**Search and scale.**
- `products` carries a small denormalised summary (`offer_count`, `available_offer_count`, `min_price`, `max_price`) maintained in the same
  transaction as every listing/seller-status change. Browsing, price filtering and price sorting therefore never aggregate over listings:
  the default browse query is an index-only scan on a partial index (`WHERE offer_count > 0`), checked with `EXPLAIN` on generated data.
  Trade-off: write amplification and one extra invariant to protect. The alternative (aggregating 10M listings per request) does not scale.
- Text search: every word must match name or brand (`ILIKE '%word%'`), backed by `pg_trgm` GIN indexes, so "ultratech cement" works and
  wildcards typed by users are escaped.
- Product detail loads offers for one product via the `(product_id, seller_id)` index, capped at 100 (best first) while `offerCount` reports the true total.
- Pagination is bounded (`size <= 50`, `page <= 10000`), and every list endpoint is paginated.
- Reads use plain SQL (`NamedParameterJdbcTemplate`) because they are dynamic and performance-critical and we want to see exactly what runs; writes use JPA.

**API errors.** A single `@RestControllerAdvice` returns `{status, code, message, fieldErrors}`. The frontend branches on `code`
(`STALE_LISTING`, `DUPLICATE_LISTING`, `SELLER_NOT_ALLOWED`, ...) and shows `fieldErrors` next to the inputs.

**Validation rules** (bean validation + DB `CHECK` constraints as a backstop): price 0.01–10,000,000 with at most 2 decimals; stock 0–10,000,000;
min order qty 1–1,000,000; at least one field on update; unknown product 404; duplicate active listing 409. Adding a product you previously
stopped re-activates that listing with the new values instead of erroring.

**Frontend.** React Router + TanStack Query (caching, loading/error states, invalidation after writes). Buyer filters live in the URL, so
results are shareable and back/forward works. A quantity field on the product page turns the offer list into a total-cost comparison.
Seller flows: filterable listing table with edit / stop / resume, "Add products" with market price context, and inline form validation that mirrors
the server. Native `<dialog>` for modals (focus trap and Esc for free), semantic tables that collapse to cards on mobile, visible focus states,
and reduced-motion support.

## 3. Assumptions
- One currency (₹), prices are per catalogue unit (bag, tonne, piece...), quantities are whole numbers.
- The catalogue is curated by BajriX; sellers only list existing products.
- Products with no buyer-visible offer are hidden from browse but still open by direct link (shows "no sellers").
- The "best price" filter/sort uses each product's lowest *available* offer.
- Category is one level deep.

## 4. Intentionally not implemented
Real authentication/authorization, seller registration and admin UI (a single admin endpoint exists so status changes can be demoed),
product creation/moderation, product images, checkout/payments/RFQ, notifications, audit history of price changes, bulk listing upload,
reserved stock/inventory decrement.

## 5. With more time
- Real auth (OIDC/JWT) with roles; seller ↔ user mapping.
- Price/stock change history table (audit + "price dropped" alerts), and an `updated_at`-based "last confirmed" freshness signal.
- Bulk edit / CSV upload for sellers; optimistic UI updates.
- More tests: repository/SQL tests per filter combination, frontend component tests, an E2E (Playwright) happy path.
- Observability: request logging with correlation ids, metrics on search latency.
- Accessibility pass with a screen reader; i18n (Hindi) for the buyer UI.

## 6. If it reached millions of products / listings
- **Summary maintenance:** listing writes already lock and update one product row. Hot products with thousands of sellers make that row a
  contention point; move to an outbox/queue and recompute asynchronously (eventually consistent, coalescing bursts). A seller status change
  currently locks and updates all of that seller's products in one transaction; that must be batched or async for sellers with 100k listings.
- **Search:** Postgres trigram search is fine to a few million rows; beyond that, or for relevance ranking, typo tolerance, synonyms and facets,
  move to OpenSearch/Elasticsearch fed by change events, keeping Postgres as the source of truth.
- **Pagination:** offset pagination gets slow for deep pages and `COUNT(*)` gets expensive; switch to keyset pagination and approximate/capped totals.
- **Listings table:** partition by `seller_id` or hash of `product_id`; review the index set against real query patterns; read replicas for browse traffic.
- **Caching:** cache category list and hot product pages (short TTL/CDN); offers must stay fresh so keep TTLs small or invalidate on write.
- **Seller dev selector** (`GET /api/sellers`) is capped at 50 and disabled with `app.dev-seller-selector=false`; it would not exist with real auth.
- **Idempotency and rate limits** on write endpoints; bulk integrations would use a separate ingestion path.

### Checking performance yourself
`scripts/bulk_data.sql` generates data at any scale (defaults: 100k products / 5k sellers / ~1M listings):
```bash
psql postgresql://bajrix:bajrix@localhost:5432/bajrix -v products=1000000 -v sellers=100000 -v per_product=10 -f scripts/bulk_data.sql
```
then `EXPLAIN ANALYZE` the queries in `CatalogQueryRepository`.

## Project layout
```
backend/   Spring Boot API
  catalog/   buyer read API, catalogue queries, product summary maintenance
  listing/   seller listings: entity, service (business rules), controller, DTOs
  seller/    seller entity/status, dev selector, demo admin endpoint
  security/  mocked authentication (X-Seller-Id -> SellerContext)
  common/    error handling, paging, search helper
  src/main/resources/db/migration   V1 schema, V2 seed data
frontend/  React app (pages/, components/, lib/)
scripts/   optional bulk data generator
```
