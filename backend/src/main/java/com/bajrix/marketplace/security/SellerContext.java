package com.bajrix.marketplace.security;

/** Identity of the calling seller. Every seller-scoped query is filtered by this id. */
public record SellerContext(long id) {
}
