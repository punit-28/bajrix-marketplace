package com.bajrix.marketplace.listing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One seller's offer for one product. product_id/seller_id are plain columns (no JPA relations):
 * the write model is tiny and this avoids accidental lazy loads.
 */
@Entity
@Table(name = "seller_listings")
public class SellerListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "min_order_qty", nullable = false)
    private int minOrderQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    /** Optimistic lock: Hibernate adds "AND version = ?" to every UPDATE and bumps it. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerListing() {}

    public SellerListing(Long productId, Long sellerId, BigDecimal price, int stock, int minOrderQty) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.price = price;
        this.stock = stock;
        this.minOrderQty = minOrderQty;
        this.status = ListingStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getSellerId() { return sellerId; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public int getMinOrderQty() { return minOrderQty; }
    public ListingStatus getStatus() { return status; }
    public Long getVersion() { return version; }

    public void setPrice(BigDecimal price) { this.price = price; }
    public void setStock(int stock) { this.stock = stock; }
    public void setMinOrderQty(int minOrderQty) { this.minOrderQty = minOrderQty; }
    public void setStatus(ListingStatus status) { this.status = status; }
}
