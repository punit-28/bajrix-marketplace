package com.bajrix.marketplace.catalog;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Maintains the denormalised buyer-facing columns on {@code products}
 * (offer_count, available_offer_count, min_price, max_price).
 *
 * <p>Visibility rule: an offer counts only if the listing is ACTIVE and the seller is APPROVED.
 * "Available" additionally requires stock &gt;= min_order_qty.
 *
 * <p><b>Concurrency:</b> two transactions changing different listings of the same product could each
 * recompute the summary without seeing the other's uncommitted change, leaving stale numbers.
 * So callers first take a row lock on the product ({@code lockProduct}) and only then refresh: the
 * refresh is a new statement, hence a fresh snapshot that includes everything committed by whoever held the lock before.
 * Must be called inside a transaction.
 */
@Repository
public class ProductSummaryRepository {

    private static final String REFRESH_TEMPLATE = """
            WITH agg AS (
                SELECT l.product_id,
                       COUNT(*)                                                AS offer_count,
                       COUNT(*) FILTER (WHERE l.stock >= l.min_order_qty)      AS available_count,
                       MIN(l.price) FILTER (WHERE l.stock >= l.min_order_qty)  AS min_price,
                       MAX(l.price) FILTER (WHERE l.stock >= l.min_order_qty)  AS max_price
                FROM seller_listings l
                JOIN sellers s ON s.id = l.seller_id
                WHERE l.status = 'ACTIVE' AND s.status = 'APPROVED' AND l.product_id IN (%1$s)
                GROUP BY l.product_id
            )
            UPDATE products p
            SET offer_count = COALESCE(a.offer_count, 0),
                available_offer_count = COALESCE(a.available_count, 0),
                min_price = a.min_price,
                max_price = a.max_price
            FROM (SELECT id FROM products WHERE id IN (%1$s)) t
            LEFT JOIN agg a ON a.product_id = t.id
            WHERE p.id = t.id
            """;

    private static final String ONE_PRODUCT = "SELECT CAST(:productId AS bigint)";
    private static final String SELLER_PRODUCTS = "SELECT DISTINCT product_id FROM seller_listings WHERE seller_id = :sellerId";

    private final NamedParameterJdbcTemplate jdbc;

    public ProductSummaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockProduct(long productId) {
        jdbc.query("SELECT id FROM products WHERE id = :productId FOR UPDATE",
                new MapSqlParameterSource("productId", productId), rs -> {
                });
    }

    public void refreshProduct(long productId) {
        jdbc.update(REFRESH_TEMPLATE.formatted(ONE_PRODUCT), new MapSqlParameterSource("productId", productId));
    }

    /** Locks in id order so concurrent bulk operations cannot deadlock each other. */
    public void lockProductsOfSeller(long sellerId) {
        jdbc.query("SELECT id FROM products WHERE id IN (" + SELLER_PRODUCTS + ") ORDER BY id FOR UPDATE",
                new MapSqlParameterSource("sellerId", sellerId), rs -> {
                });
    }

    public void refreshProductsOfSeller(long sellerId) {
        jdbc.update(REFRESH_TEMPLATE.formatted(SELLER_PRODUCTS), new MapSqlParameterSource("sellerId", sellerId));
    }
}
