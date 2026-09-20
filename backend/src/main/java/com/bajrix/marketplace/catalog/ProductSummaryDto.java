package com.bajrix.marketplace.catalog;

import java.math.BigDecimal;

/** A product as shown in buyer search results. Prices/counts only reflect offers buyers may see. */
public record ProductSummaryDto(Long id, String name, String brand, String unit, String description,
                                Long categoryId, String categoryName,
                                int offerCount, int availableOfferCount,
                                BigDecimal minPrice, BigDecimal maxPrice) {
}
