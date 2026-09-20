package com.bajrix.marketplace.catalog;

import com.bajrix.marketplace.common.ApiException;

public enum ProductSort {
    RELEVANCE, PRICE_ASC, PRICE_DESC, NAME;

    public static ProductSort parse(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_SORT",
                    "sort must be one of: relevance, price_asc, price_desc, name.");
        }
    }
}
