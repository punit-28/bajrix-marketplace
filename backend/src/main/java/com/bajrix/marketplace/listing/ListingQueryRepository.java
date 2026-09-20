package com.bajrix.marketplace.listing;

import com.bajrix.marketplace.common.PageResponse;
import com.bajrix.marketplace.common.SqlSearch;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Read side of the seller portal. Every query is filtered by seller_id: a seller can only ever read their own rows. */
@Repository
public class ListingQueryRepository {

    private static final String SELECT = """
            SELECT l.id, l.product_id, p.name, p.brand, p.unit, c.name AS category_name,
                   l.price, l.stock, l.min_order_qty, l.status, l.version, l.updated_at,
                   p.min_price AS market_min_price, p.offer_count AS market_offer_count
            FROM seller_listings l
            JOIN products p ON p.id = l.product_id
            JOIN categories c ON c.id = p.category_id
            """;

    private static final RowMapper<ListingView> MAPPER = (rs, i) -> new ListingView(
            rs.getLong("id"), rs.getLong("product_id"), rs.getString("name"), rs.getString("brand"),
            rs.getString("unit"), rs.getString("category_name"), rs.getBigDecimal("price"),
            rs.getInt("stock"), rs.getInt("min_order_qty"), ListingStatus.valueOf(rs.getString("status")),
            rs.getInt("stock") >= rs.getInt("min_order_qty"), rs.getLong("version"),
            rs.getTimestamp("updated_at").toInstant(), rs.getBigDecimal("market_min_price"),
            rs.getInt("market_offer_count"));

    private final NamedParameterJdbcTemplate jdbc;

    public ListingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ListingView> findOwned(long listingId, long sellerId) {
        return jdbc.query(SELECT + " WHERE l.id = :id AND l.seller_id = :sellerId",
                new MapSqlParameterSource("id", listingId).addValue("sellerId", sellerId), MAPPER)
                .stream().findFirst();
    }

    public PageResponse<ListingView> search(long sellerId, ListingStatus status, String q, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("sellerId", sellerId);
        StringBuilder where = new StringBuilder(" WHERE l.seller_id = :sellerId");
        if (status != null) {
            where.append(" AND l.status = :status");
            params.addValue("status", status.name());
        }
        SqlSearch.appendTextFilter(where, params, "p", q);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_listings l JOIN products p ON p.id = l.product_id" + where,
                params, Long.class);

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        List<ListingView> items = jdbc.query(
                SELECT + where + " ORDER BY l.updated_at DESC, l.id DESC LIMIT :limit OFFSET :offset",
                params, MAPPER);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }
}
