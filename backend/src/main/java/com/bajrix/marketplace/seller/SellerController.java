package com.bajrix.marketplace.seller;

import com.bajrix.marketplace.security.CurrentSeller;
import com.bajrix.marketplace.security.SellerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @GetMapping("/api/seller/me")
    public SellerProfile me(@CurrentSeller SellerContext seller) {
        return sellerService.getProfile(seller.id());
    }
}
