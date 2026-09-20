package com.bajrix.marketplace.catalog;

import com.bajrix.marketplace.common.ApiException;
import com.bajrix.marketplace.common.PageResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Public buyer-facing read API. No authentication; only buyer-visible data is ever returned. */
@RestController
@RequestMapping("/api")
@Validated
public class ProductController {

    private final CatalogQueryRepository catalog;

    public ProductController(CatalogQueryRepository catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return catalog.listCategories();
    }

    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> search(
            @RequestParam(required = false) @Size(max = 100, message = "Search text is too long") String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minPrice cannot be negative") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxPrice cannot be negative") BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page cannot be negative") @Max(value = 10000, message = "page is too large") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1") @Max(value = 50, message = "size must be at most 50") int size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice cannot be greater than maxPrice.");
        }
        return catalog.searchProducts(new ProductSearchCriteria(
                q, categoryId, minPrice, maxPrice, inStockOnly, ProductSort.parse(sort), page, size));
    }

    @GetMapping("/products/{id}")
    public ProductDetailDto detail(@PathVariable long id) {
        return catalog.findProductDetail(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found."));
    }
}
