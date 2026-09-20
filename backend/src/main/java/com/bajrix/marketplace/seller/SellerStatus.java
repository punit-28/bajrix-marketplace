package com.bajrix.marketplace.seller;

/**
 * How seller status affects the platform:
 * <ul>
 *   <li>APPROVED - listings visible to buyers; can manage listings.</li>
 *   <li>PENDING  - can log in and prepare listings, but nothing is visible to buyers yet.</li>
 *   <li>REJECTED - hidden from buyers and read-only (cannot create or change listings).</li>
 * </ul>
 */
public enum SellerStatus {
    APPROVED, PENDING, REJECTED;

    public boolean isVisibleToBuyers() {
        return this == APPROVED;
    }

    public boolean canManageListings() {
        return this == APPROVED || this == PENDING;
    }
}
