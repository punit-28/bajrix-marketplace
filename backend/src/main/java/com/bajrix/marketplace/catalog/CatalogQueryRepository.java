package com.bajrix.marketplace.catalog;

import com.bajrix.marketplace.common.PageResponse;
import com.bajrix.marketplace.common.SqlSearch;
import com.bajrix.marketplace.listing.ListingStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read side of the catalogue. Uses plain SQL on purpose: these are dynamic, performance-sensitive
 * queries and we want to see (and EXPLAIN) exactly what runs against the indexes.
 */
@Repository
public class CatalogQueryRepository {

    private static final int MAX_OFFERS_ON_DETAIL = 100;

    private static final RowMapper<ProductSummaryDto> PRODUCT_MAPPER = (rs, i) -> new ProductSummaryDto(
            rs.getLong("id"), rs.getString("name"), rs.getString("brand"), rs.getString("unit"),
            rs.getString("description"), rs.getLong("category_id"), rs.getString("category_name"),
            rs.getInt("offer_count"), rs.getInt("available_offer_count"),
            rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"));

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryDto> listCategories() {
        return jdbc.query("SELECT id, name FROM categories ORDER BY name",
                (rs, i) -> new CategoryDto(rs.getLong("id"), rs.getString("name")));
    }

    public boolean productExists(long productId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM products WHERE id = :id)",
                new MapSqlParameterSource("id", productId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** Buyer search. Only products with at least one buyer-visible offer are returned. */
    public PageResponse<ProductSummaryDto> searchProducts(ProductSearchCriteria c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE p.offer_count > 0");
        if (c.categoryId() != null) {
            where.append(" AND p.category_id = :categoryId");
            params.addValue("categoryId", c.categoryId());
        }
        if (c.minPrice() != null) {
            where.append(" AND p.min_price >= :minPrice");
            params.addValue("minPrice", c.minPrice());
        }
        if (c.maxPrice() != null) {
            where.append(" AND p.min_price <= :maxPrice");
            params.addValue("maxPrice", c.maxPrice());
        }
        if (c.inStockOnly()) {
            where.append(" AND p.available_offer_count > 0");
        }
        String prefix = SqlSearch.appendTextFilter(where, params, "p", c.q());

        String orderBy = switch (c.sort()) {
            case PRICE_ASC -> " ORDER BY p.min_price ASC NULLS LAST, p.id";
            case PRICE_DESC -> " ORDER BY p.min_price DESC NULLS LAST, p.id";
            case NAME -> " ORDER BY p.name, p.id";
            case RELEVANCE -> {
                String boost = "";
                if (prefix != null) {
                    boost = "CASE WHEN lower(p.name) LIKE :prefix THEN 0 ELSE 1 END, ";
                    params.addValue("prefix", prefix);
                }
                yield " ORDER BY " + boost + "p.available_offer_count DESC, p.name, p.id";
            }
        };

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM products p" + where, params, Long.class);

        params.addValue("limit", c.size());
        params.addValue("offset", (long) c.page() * c.size());
        List<ProductSummaryDto> items = jdbc.query(
                "SELECT p.id, p.name, p.brand, p.unit, p.description, p.category_id, cat.name AS category_name, "
                        + "p.offer_count, p.available_offer_count, p.min_price, p.max_price "
                        + "FROM products p JOIN categories cat ON cat.id = p.category_id"
                        + where + orderBy + " LIMIT :limit OFFSET :offset",
                params, PRODUCT_MAPPER);
        return PageResponse.of(items, c.page(), c.size(), total == null ? 0 : total);
    }

    public Optional<ProductDetailDto> findProductDetail(long productId) {
        List<ProductSummaryDto> products = jdbc.query(
                "SELECT p.id, p.name, p.brand, p.unit, p.description, p.category_id, cat.name AS category_name, "
                        + "p.offer_count, p.available_offer_count, p.min_price, p.max_price "
                        + "FROM products p JOIN categories cat ON cat.id = p.category_id WHERE p.id = :id",
                new MapSqlParameterSource("id", productId), PRODUCT_MAPPER);
        if (products.isEmpty()) {
            return Optional.empty();
        }
        // Buyer visibility rule lives here: ACTIVE listing AND APPROVED seller. Orderable offers first, cheapest first.
        List<OfferDto> offers = jdbc.query(
                "SELECT l.id, l.price, l.stock, l.min_order_qty, l.updated_at, "
                        + "s.id AS seller_id, s.name AS seller_name, s.city AS seller_city "
                        + "FROM seller_listings l JOIN sellers s ON s.id = l.seller_id "
                        + "WHERE l.product_id = :id AND l.status = 'ACTIVE' AND s.status = 'APPROVED' "
                        + "ORDER BY (l.stock >= l.min_order_qty) DESC, l.price ASC, l.id ASC LIMIT :limit",
                new MapSqlParameterSource("id", productId).addValue("limit", MAX_OFFERS_ON_DETAIL),
                (rs, i) -> new OfferDto(rs.getLong("id"), rs.getLong("seller_id"), rs.getString("seller_name"),
                        rs.getString("seller_city"), rs.getBigDecimal("price"), rs.getInt("stock"),
                        rs.getInt("min_order_qty"),
                        rs.getInt("stock") >= rs.getInt("min_order_qty"),
                        rs.getTimestamp("updated_at").toInstant()));
        return Optional.of(new ProductDetailDto(products.get(0), offers));
    }

    /** Seller-facing catalogue search: every product, annotated with market price and this seller's own listing. */
    public PageResponse<CatalogProductDto> searchCatalogForSeller(long sellerId, String q, Long categoryId,
                                                                  int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("sellerId", sellerId);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (categoryId != null) {
            where.append(" AND p.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        SqlSearch.appendTextFilter(where, params, "p", q);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM products p" + where, params, Long.class);

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        List<CatalogProductDto> items = jdbc.query(
                "SELECT p.id, p.name, p.brand, p.unit, cat.name AS category_name, p.offer_count, p.min_price, "
                        + "l.id AS listing_id, l.status AS listing_status "
                        + "FROM products p JOIN categories cat ON cat.id = p.category_id "
                        + "LEFT JOIN seller_listings l ON l.product_id = p.id AND l.seller_id = :sellerId"
                        + where + " ORDER BY p.name, p.id LIMIT :limit OFFSET :offset",
                params,
                (rs, i) -> {
                    long listingId = rs.getLong("listing_id");
                    boolean listed = !rs.wasNull();
                    String status = rs.getString("listing_status");
                    return new CatalogProductDto(rs.getLong("id"), rs.getString("name"), rs.getString("brand"),
                            rs.getString("unit"), rs.getString("category_name"), rs.getInt("offer_count"),
                            rs.getBigDecimal("min_price"),
                            listed ? listingId : null,
                            status == null ? null : ListingStatus.valueOf(status));
                });
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }
}
