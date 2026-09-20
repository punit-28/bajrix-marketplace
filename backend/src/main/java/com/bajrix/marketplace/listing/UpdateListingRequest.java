package com.bajrix.marketplace.listing;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Partial update: send only the fields that change. {@code version} is the version the client last saw. */
public record UpdateListingRequest(
        @NotNull(message = "version is required")
        Long version,

        @DecimalMin(value = "0.01", message = "Price must be at least ₹0.01")
        @DecimalMax(value = "10000000.00", message = "Price cannot exceed ₹1,00,00,000")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,

        @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 10_000_000, message = "Stock is too large")
        Integer stock,

        @Min(value = 1, message = "Minimum order quantity must be at least 1")
        @Max(value = 1_000_000, message = "Minimum order quantity is too large")
        Integer minOrderQty) {
}
