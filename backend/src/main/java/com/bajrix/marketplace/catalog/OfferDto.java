package com.bajrix.marketplace.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/** One seller's offer as a buyer sees it. {@code orderable} = stock covers the minimum order quantity. */
public record OfferDto(Long listingId, Long sellerId, String sellerName, String sellerCity,
                       BigDecimal price, int stock, int minOrderQty, boolean orderable, Instant updatedAt) {
}
