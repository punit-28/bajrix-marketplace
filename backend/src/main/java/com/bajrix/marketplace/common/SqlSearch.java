package com.bajrix.marketplace.common;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Shared helper for the multi-word name/brand search used by buyer, seller and catalogue queries. */
public final class SqlSearch {

    private static final int MAX_TERMS = 5;

    private SqlSearch() {}

    /**
     * Appends one "(name ILIKE :t0 OR brand ILIKE :t0)" clause per word, so "ultratech cement"
     * matches "UltraTech PPC Cement 50 kg". Backed by the pg_trgm indexes on name and brand.
     *
     * @return the lower-cased, LIKE-escaped prefix pattern of the whole query (for ranking), or null if no text
     */
    public static String appendTextFilter(StringBuilder where, MapSqlParameterSource params, String alias, String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String[] terms = q.trim().split("\\s+");
        for (int i = 0; i < Math.min(terms.length, MAX_TERMS); i++) {
            String param = "t" + i;
            where.append(" AND (").append(alias).append(".name ILIKE :").append(param)
                    .append(" OR ").append(alias).append(".brand ILIKE :").append(param).append(")");
            params.addValue(param, "%" + escapeLike(terms[i]) + "%");
        }
        return escapeLike(q.trim().toLowerCase()) + "%";
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
