package com.bajrix.marketplace.seller;

public record SellerProfile(Long id, String name, String city, SellerStatus status,
                            boolean visibleToBuyers, boolean canManageListings) {

    public static SellerProfile from(Seller s) {
        return new SellerProfile(s.getId(), s.getName(), s.getCity(), s.getStatus(),
                s.getStatus().isVisibleToBuyers(), s.getStatus().canManageListings());
    }
}
