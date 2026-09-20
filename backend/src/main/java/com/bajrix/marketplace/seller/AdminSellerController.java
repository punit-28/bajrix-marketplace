package com.bajrix.marketplace.seller;

import com.bajrix.marketplace.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Minimal stand-in for an admin/back-office API so reviewers can approve or reject a seller. */
@RestController
@RequestMapping("/api/admin/sellers")
public class AdminSellerController {

    public record ChangeStatusRequest(@NotNull(message = "Status is required") SellerStatus status) {}

    private final SellerService sellerService;
    private final String adminToken;

    public AdminSellerController(SellerService sellerService, @Value("${app.admin-token}") String adminToken) {
        this.sellerService = sellerService;
        this.adminToken = adminToken;
    }

    @PatchMapping("/{id}/status")
    public SellerProfile changeStatus(@PathVariable long id,
                                      @RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        if (token == null || !token.equals(adminToken)) {
            throw ApiException.forbidden("ADMIN_ONLY", "Admin token required.");
        }
        return sellerService.changeStatus(id, request.status());
    }
}
