package com.bajrix.marketplace.catalog;

import java.math.BigDecimal;

public record ProductSearchCriteria(String q, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                    boolean inStockOnly, ProductSort sort, int page, int size) {
}
