package com.bajrix.marketplace.seller;

import com.bajrix.marketplace.catalog.ProductSummaryRepository;
import com.bajrix.marketplace.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SellerService {

    private final SellerRepository sellers;
    private final ProductSummaryRepository summaries;

    public SellerService(SellerRepository sellers, ProductSummaryRepository summaries) {
        this.sellers = sellers;
        this.summaries = summaries;
    }

    @Transactional(readOnly = true)
    public SellerProfile getProfile(long sellerId) {
        return SellerProfile.from(sellers.findById(sellerId)
                .orElseThrow(() -> ApiException.unauthorized("UNKNOWN_SELLER", "Unknown seller.")));
    }

    /** Dev helper for the "sell as" picker. Capped: a real system would never list 100k sellers. */
    @Transactional(readOnly = true)
    public List<SellerProfile> listForDevSelector() {
        return sellers.findAll(PageRequest.of(0, 50, Sort.by("id"))).map(SellerProfile::from).getContent();
    }

    /**
     * Changes a seller's status and re-derives the buyer-facing summary of every product the seller lists,
     * in the same transaction, so buyers never see a half-updated catalogue.
     */
    @Transactional
    public SellerProfile changeStatus(long sellerId, SellerStatus status) {
        Seller seller = sellers.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound("SELLER_NOT_FOUND", "Seller not found."));
        seller.setStatus(status);
        sellers.saveAndFlush(seller);
        summaries.lockProductsOfSeller(sellerId);
        summaries.refreshProductsOfSeller(sellerId);
        return SellerProfile.from(seller);
    }
}
