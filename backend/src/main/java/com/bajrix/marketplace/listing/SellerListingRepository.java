package com.bajrix.marketplace.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerListingRepository extends JpaRepository<SellerListing, Long> {

    /** The ONLY way services load a listing for modification: scoped to the owner. */
    Optional<SellerListing> findByIdAndSellerId(Long id, Long sellerId);

    Optional<SellerListing> findByProductIdAndSellerId(Long productId, Long sellerId);
}
