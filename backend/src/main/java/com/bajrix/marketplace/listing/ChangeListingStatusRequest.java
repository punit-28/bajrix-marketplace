package com.bajrix.marketplace.listing;

import jakarta.validation.constraints.NotNull;

public record ChangeListingStatusRequest(
        @NotNull(message = "version is required") Long version,
        @NotNull(message = "status is required") ListingStatus status) {
}
