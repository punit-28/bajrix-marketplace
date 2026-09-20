package com.bajrix.marketplace.catalog;

import java.util.List;

/** {@code offers} is capped (best first); {@code product.offerCount} is the true total. */
public record ProductDetailDto(ProductSummaryDto product, List<OfferDto> offers) {
}
