package com.bajrix.marketplace.listing;

import com.bajrix.marketplace.catalog.CatalogQueryRepository;
import com.bajrix.marketplace.catalog.ProductSummaryRepository;
import com.bajrix.marketplace.common.ApiException;
import com.bajrix.marketplace.seller.Seller;
import com.bajrix.marketplace.seller.SellerRepository;
import com.bajrix.marketplace.seller.SellerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Business rules in isolation (no database): status gating, duplicates, ownership, stale versions. */
@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final long SELLER = 1L;
    private static final long PRODUCT = 50L;
    private static final long LISTING = 10L;

    @Mock SellerRepository sellers;
    @Mock SellerListingRepository listings;
    @Mock CatalogQueryRepository catalog;
    @Mock ListingQueryRepository views;
    @Mock ProductSummaryRepository summaries;

    ListingService service;

    @BeforeEach
    void setUp() {
        service = new ListingService(sellers, listings, catalog, views, summaries);
    }

    private void sellerIs(SellerStatus status) {
        when(sellers.findById(SELLER)).thenReturn(Optional.of(new Seller("Test Seller", "Pune", status)));
    }

    private SellerListing existingListing(ListingStatus status, long version) {
        SellerListing l = new SellerListing(PRODUCT, SELLER, new BigDecimal("390.00"), 100, 10);
        l.setStatus(status);
        ReflectionTestUtils.setField(l, "id", LISTING);
        ReflectionTestUtils.setField(l, "version", version);
        return l;
    }

    private ListingView anyView() {
        return new ListingView(LISTING, PRODUCT, "Product", "Brand", "bag", "Cement", new BigDecimal("390.00"),
                100, 10, ListingStatus.ACTIVE, true, 1L, Instant.now(), null, 0);
    }

    private static void assertApiError(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static CreateListingRequest createRequest() {
        return new CreateListingRequest(PRODUCT, new BigDecimal("399.00"), 200, 5);
    }

    @Test
    void rejectedSellerCannotCreateListings() {
        sellerIs(SellerStatus.REJECTED);

        assertThatThrownBy(() -> service.create(SELLER, createRequest()))
                .satisfies(t -> assertApiError(t, "SELLER_NOT_ALLOWED"));
        verify(listings, never()).saveAndFlush(any());
    }

    @Test
    void pendingSellerMayPrepareListings() {
        sellerIs(SellerStatus.PENDING);
        when(catalog.productExists(PRODUCT)).thenReturn(true);
        when(listings.findByProductIdAndSellerId(PRODUCT, SELLER)).thenReturn(Optional.empty());
        when(listings.saveAndFlush(any(SellerListing.class))).thenAnswer(inv -> {
            SellerListing l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", LISTING);
            return l;
        });
        when(views.findOwned(LISTING, SELLER)).thenReturn(Optional.of(anyView()));

        assertThat(service.create(SELLER, createRequest())).isNotNull();
        verify(listings).saveAndFlush(any(SellerListing.class));
    }

    @Test
    void unknownProductIsRejected() {
        sellerIs(SellerStatus.APPROVED);
        when(catalog.productExists(PRODUCT)).thenReturn(false);

        assertThatThrownBy(() -> service.create(SELLER, createRequest()))
                .satisfies(t -> assertApiError(t, "PRODUCT_NOT_FOUND"));
    }

    @Test
    void duplicateActiveListingIsRejected() {
        sellerIs(SellerStatus.APPROVED);
        when(catalog.productExists(PRODUCT)).thenReturn(true);
        when(listings.findByProductIdAndSellerId(PRODUCT, SELLER))
                .thenReturn(Optional.of(existingListing(ListingStatus.ACTIVE, 0)));

        assertThatThrownBy(() -> service.create(SELLER, createRequest()))
                .satisfies(t -> assertApiError(t, "DUPLICATE_LISTING"));
        verify(listings, never()).saveAndFlush(any());
    }

    @Test
    void addingAProductYouStoppedSellingReactivatesItWithNewValues() {
        sellerIs(SellerStatus.APPROVED);
        SellerListing stopped = existingListing(ListingStatus.INACTIVE, 4);
        when(catalog.productExists(PRODUCT)).thenReturn(true);
        when(listings.findByProductIdAndSellerId(PRODUCT, SELLER)).thenReturn(Optional.of(stopped));
        when(views.findOwned(LISTING, SELLER)).thenReturn(Optional.of(anyView()));

        service.create(SELLER, createRequest());

        assertThat(stopped.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(stopped.getPrice()).isEqualByComparingTo("399.00");
        assertThat(stopped.getStock()).isEqualTo(200);
        assertThat(stopped.getMinOrderQty()).isEqualTo(5);
    }

    @Test
    void staleVersionIsRejectedBeforeAnythingIsWritten() {
        sellerIs(SellerStatus.APPROVED);
        when(listings.findByIdAndSellerId(LISTING, SELLER)).thenReturn(Optional.of(existingListing(ListingStatus.ACTIVE, 5)));

        UpdateListingRequest stale = new UpdateListingRequest(4L, new BigDecimal("380.00"), null, null);

        assertThatThrownBy(() -> service.update(SELLER, LISTING, stale))
                .satisfies(t -> assertApiError(t, "STALE_LISTING"));
        verify(listings, never()).saveAndFlush(any());
        verifyNoInteractions(summaries);
    }

    @Test
    void anotherSellersListingLooksLikeItDoesNotExist() {
        sellerIs(SellerStatus.APPROVED);
        when(listings.findByIdAndSellerId(LISTING, SELLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(SELLER, LISTING, new UpdateListingRequest(0L, null, 5, null)))
                .satisfies(t -> assertApiError(t, "LISTING_NOT_FOUND"));
    }

    @Test
    void updateWithoutAnyFieldIsRejected() {
        sellerIs(SellerStatus.APPROVED);

        assertThatThrownBy(() -> service.update(SELLER, LISTING, new UpdateListingRequest(0L, null, null, null)))
                .satisfies(t -> assertApiError(t, "NO_CHANGES"));
    }

    @Test
    void updateChangesOnlyProvidedFieldsThenLocksAndRefreshesSummary() {
        sellerIs(SellerStatus.APPROVED);
        SellerListing listing = existingListing(ListingStatus.ACTIVE, 2);
        when(listings.findByIdAndSellerId(LISTING, SELLER)).thenReturn(Optional.of(listing));
        when(views.findOwned(LISTING, SELLER)).thenReturn(Optional.of(anyView()));

        service.update(SELLER, LISTING, new UpdateListingRequest(2L, new BigDecimal("375.50"), null, null));

        assertThat(listing.getPrice()).isEqualByComparingTo("375.50");
        assertThat(listing.getStock()).isEqualTo(100);        // untouched
        assertThat(listing.getMinOrderQty()).isEqualTo(10);   // untouched

        InOrder order = inOrder(listings, summaries);
        order.verify(listings).saveAndFlush(listing);
        order.verify(summaries).lockProduct(PRODUCT);
        order.verify(summaries).refreshProduct(PRODUCT);
    }

    @Test
    void stoppingSaleMarksListingInactive() {
        sellerIs(SellerStatus.APPROVED);
        SellerListing listing = existingListing(ListingStatus.ACTIVE, 0);
        when(listings.findByIdAndSellerId(LISTING, SELLER)).thenReturn(Optional.of(listing));
        when(views.findOwned(LISTING, SELLER)).thenReturn(Optional.of(anyView()));

        service.changeStatus(SELLER, LISTING, new ChangeListingStatusRequest(0L, ListingStatus.INACTIVE));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.INACTIVE);
        verify(summaries).refreshProduct(PRODUCT);
    }
}
