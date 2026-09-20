package com.bajrix.marketplace.catalog;

import com.bajrix.marketplace.listing.ListingStatus;

import java.math.BigDecimal;

/** A catalogue product from a seller's point of view: market context plus whether they already list it. */
public record CatalogProductDto(Long id, String name, String brand, String unit, String categoryName,
                                int marketOfferCount, BigDecimal marketMinPrice,
                                Long listingId, ListingStatus listingStatus) {
}
