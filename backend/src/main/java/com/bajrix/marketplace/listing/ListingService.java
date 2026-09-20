package com.bajrix.marketplace.listing;

import com.bajrix.marketplace.catalog.CatalogQueryRepository;
import com.bajrix.marketplace.catalog.ProductSummaryRepository;
import com.bajrix.marketplace.common.ApiException;
import com.bajrix.marketplace.common.PageResponse;
import com.bajrix.marketplace.seller.Seller;
import com.bajrix.marketplace.seller.SellerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * All business rules for seller listings.
 *
 * <ul>
 *   <li>Authorization: listings are always loaded with {@code findByIdAndSellerId}; another seller's listing
 *       is indistinguishable from a missing one (404), so ids cannot be probed.</li>
 *   <li>Seller status: REJECTED sellers are read-only (403). PENDING sellers may prepare listings but buyers won't see them.</li>
 *   <li>Uniqueness: one listing per (product, seller). Adding a product you stopped selling re-activates it.</li>
 *   <li>Concurrency: optimistic locking via {@code version}; a stale writer gets 409 STALE_LISTING.</li>
 *   <li>After every write the product's buyer-facing summary is refreshed in the same transaction.</li>
 * </ul>
 */
@Service
public class ListingService {

    private final SellerRepository sellers;
    private final SellerListingRepository listings;
    private final CatalogQueryRepository catalog;
    private final ListingQueryRepository views;
    private final ProductSummaryRepository summaries;

    public ListingService(SellerRepository sellers, SellerListingRepository listings, CatalogQueryRepository catalog,
                          ListingQueryRepository views, ProductSummaryRepository summaries) {
        this.sellers = sellers;
        this.listings = listings;
        this.catalog = catalog;
        this.views = views;
        this.summaries = summaries;
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingView> list(long sellerId, ListingStatus status, String q, int page, int size) {
        return views.search(sellerId, status, q, page, size);
    }

    @Transactional
    public ListingView create(long sellerId, CreateListingRequest req) {
        requireWritable(sellerId);
        if (!catalog.productExists(req.productId())) {
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "That product is not in the catalogue.");
        }

        Optional<SellerListing> existing = listings.findByProductIdAndSellerId(req.productId(), sellerId);
        SellerListing listing;
        if (existing.isPresent()) {
            listing = existing.get();
            if (listing.getStatus() == ListingStatus.ACTIVE) {
                throw ApiException.conflict("DUPLICATE_LISTING",
                        "You already sell this product. Edit your existing listing instead.");
            }
            // Previously stopped: bring it back with the newly entered values.
            listing.setPrice(req.price());
            listing.setStock(req.stock());
            listing.setMinOrderQty(req.minOrderQty());
            listing.setStatus(ListingStatus.ACTIVE);
        } else {
            listing = new SellerListing(req.productId(), sellerId, req.price(), req.stock(), req.minOrderQty());
        }

        try {
            listings.saveAndFlush(listing);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous creates: the unique (product_id, seller_id) constraint is the final arbiter.
            throw ApiException.conflict("DUPLICATE_LISTING",
                    "You already sell this product. Edit your existing listing instead.");
        }
        syncSummary(listing.getProductId());
        return requireView(listing.getId(), sellerId);
    }

    @Transactional
    public ListingView update(long sellerId, long listingId, UpdateListingRequest req) {
        requireWritable(sellerId);
        if (req.price() == null && req.stock() == null && req.minOrderQty() == null) {
            throw ApiException.badRequest("NO_CHANGES", "Provide at least one of price, stock or minOrderQty.");
        }
        SellerListing listing = requireOwned(sellerId, listingId);
        assertFresh(listing, req.version());

        if (req.price() != null) listing.setPrice(req.price());
        if (req.stock() != null) listing.setStock(req.stock());
        if (req.minOrderQty() != null) listing.setMinOrderQty(req.minOrderQty());

        listings.saveAndFlush(listing);   // throws ObjectOptimisticLockingFailureException if we lost a race
        syncSummary(listing.getProductId());
        return requireView(listingId, sellerId);
    }

    @Transactional
    public ListingView changeStatus(long sellerId, long listingId, ChangeListingStatusRequest req) {
        requireWritable(sellerId);
        SellerListing listing = requireOwned(sellerId, listingId);
        assertFresh(listing, req.version());

        listing.setStatus(req.status());
        listings.saveAndFlush(listing);
        syncSummary(listing.getProductId());
        return requireView(listingId, sellerId);
    }

    // ------------------------------------------------------------------------------------------

    private Seller requireWritable(long sellerId) {
        Seller seller = sellers.findById(sellerId)
                .orElseThrow(() -> ApiException.unauthorized("UNKNOWN_SELLER", "Unknown seller."));
        if (!seller.getStatus().canManageListings()) {
            throw ApiException.forbidden("SELLER_NOT_ALLOWED",
                    "Your seller account is " + seller.getStatus().name().toLowerCase()
                            + ", so you cannot change listings.");
        }
        return seller;
    }

    private SellerListing requireOwned(long sellerId, long listingId) {
        return listings.findByIdAndSellerId(listingId, sellerId)
                .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Listing not found."));
    }

    /** Early, friendly stale check; the @Version column still protects against races after this point. */
    private void assertFresh(SellerListing listing, Long clientVersion) {
        if (!listing.getVersion().equals(clientVersion)) {
            throw ApiException.conflict("STALE_LISTING",
                    "This listing was changed by someone else. Reload it and try again.");
        }
    }

    private void syncSummary(long productId) {
        summaries.lockProduct(productId);
        summaries.refreshProduct(productId);
    }

    private ListingView requireView(long listingId, long sellerId) {
        return views.findOwned(listingId, sellerId)
                .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Listing not found."));
    }
}
