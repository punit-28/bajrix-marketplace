package com.bajrix.marketplace.listing;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateListingRequest(
        @NotNull(message = "Choose a product")
        Long productId,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least ₹0.01")
        @DecimalMax(value = "10000000.00", message = "Price cannot exceed ₹1,00,00,000")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "Stock is required")
        @Min(value = 0, message = "Stock cannot be negative")
        @Max(value = 10_000_000, message = "Stock is too large")
        Integer stock,

        @NotNull(message = "Minimum order quantity is required")
        @Min(value = 1, message = "Minimum order quantity must be at least 1")
        @Max(value = 1_000_000, message = "Minimum order quantity is too large")
        Integer minOrderQty) {
}
