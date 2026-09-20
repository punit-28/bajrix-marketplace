package com.bajrix.marketplace.seller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** DEV ONLY: stands in for a login screen so reviewers can switch seller identity. Disable via app.dev-seller-selector=false. */
@RestController
@ConditionalOnProperty(name = "app.dev-seller-selector", havingValue = "true", matchIfMissing = true)
public class DevSellerController {

    private final SellerService sellerService;

    public DevSellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @GetMapping("/api/sellers")
    public List<SellerProfile> listSellers() {
        return sellerService.listForDevSelector();
    }
}
