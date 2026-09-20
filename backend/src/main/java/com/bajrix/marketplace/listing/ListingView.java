package com.bajrix.marketplace.listing;

import java.math.BigDecimal;
import java.time.Instant;

/** A seller's listing enriched with product info and current market price, for the seller portal. */
public record ListingView(Long id, Long productId, String productName, String brand, String unit, String categoryName,
                          BigDecimal price, int stock, int minOrderQty, ListingStatus status, boolean orderable,
                          Long version, Instant updatedAt, BigDecimal marketMinPrice, int marketOfferCount) {
}
