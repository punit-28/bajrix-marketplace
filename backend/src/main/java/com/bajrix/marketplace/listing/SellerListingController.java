package com.bajrix.marketplace.listing;

import com.bajrix.marketplace.catalog.CatalogProductDto;
import com.bajrix.marketplace.catalog.CatalogQueryRepository;
import com.bajrix.marketplace.common.ApiException;
import com.bajrix.marketplace.common.PageResponse;
import com.bajrix.marketplace.security.CurrentSeller;
import com.bajrix.marketplace.security.SellerContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Seller portal API. Every endpoint is scoped to the calling seller via {@link CurrentSeller}. */
@RestController
@RequestMapping("/api/seller")
@Validated
public class SellerListingController {

    private final ListingService listingService;
    private final CatalogQueryRepository catalog;

    public SellerListingController(ListingService listingService, CatalogQueryRepository catalog) {
        this.listingService = listingService;
        this.catalog = catalog;
    }

    @GetMapping("/listings")
    public PageResponse<ListingView> myListings(
            @CurrentSeller SellerContext seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Size(max = 100, message = "Search text is too long") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") @Max(value = 10000, message = "page is too large") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") @Max(value = 50, message = "size must be at most 50") int size) {
        return listingService.list(seller.id(), parseStatus(status), q, page, size);
    }

    /** Catalogue search for the "add a product" flow, annotated with market price and existing listing. */
    @GetMapping("/catalog")
    public PageResponse<CatalogProductDto> catalogSearch(
            @CurrentSeller SellerContext seller,
            @RequestParam(required = false) @Size(max = 100, message = "Search text is too long") String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") @Max(value = 10000, message = "page is too large") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") @Max(value = 50, message = "size must be at most 50") int size) {
        return catalog.searchCatalogForSeller(seller.id(), q, categoryId, page, size);
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingView create(@CurrentSeller SellerContext seller, @Valid @RequestBody CreateListingRequest request) {
        return listingService.create(seller.id(), request);
    }

    @PatchMapping("/listings/{id}")
    public ListingView update(@CurrentSeller SellerContext seller, @PathVariable long id,
                              @Valid @RequestBody UpdateListingRequest request) {
        return listingService.update(seller.id(), id, request);
    }

    /** Stop selling (INACTIVE) or resume selling (ACTIVE). */
    @PutMapping("/listings/{id}/status")
    public ListingView changeStatus(@CurrentSeller SellerContext seller, @PathVariable long id,
                                    @Valid @RequestBody ChangeListingStatusRequest request) {
        return listingService.changeStatus(seller.id(), id, request);
    }

    private static ListingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ListingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "status must be ACTIVE or INACTIVE.");
        }
    }
}
