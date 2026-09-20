package com.bajrix.marketplace.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * End-to-end tests against a real PostgreSQL (Testcontainers, requires Docker) with the Flyway migrations
 * and seed data applied. Covers what mocks cannot: SQL visibility rules, constraints, authorization
 * boundaries and real concurrent writers.
 *
 * Each test touches its own seed rows so tests are independent of execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketplaceApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ULTRATECH = "CEM-ULT-PPC-50";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    // --- helpers -------------------------------------------------------------------------------

    private long sellerId(String name) {
        return jdbc.queryForObject("SELECT id FROM sellers WHERE name = ?", Long.class, name);
    }

    private long productId(String sku) {
        return jdbc.queryForObject("SELECT id FROM products WHERE sku = ?", Long.class, sku);
    }

    private long listingId(String seller, String sku) {
        return jdbc.queryForObject("""
                SELECT l.id FROM seller_listings l
                JOIN sellers s ON s.id = l.seller_id JOIN products p ON p.id = l.product_id
                WHERE s.name = ? AND p.sku = ?""", Long.class, seller, sku);
    }

    private long dbVersion(long listingId) {
        return jdbc.queryForObject("SELECT version FROM seller_listings WHERE id = ?", Long.class, listingId);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long sellerId, Object body) throws Exception {
        b.header("X-Seller-Id", sellerId).contentType(MediaType.APPLICATION_JSON);
        return body == null ? b : b.content(mapper.writeValueAsString(body));
    }

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(request).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isBlank() ? null : mapper.readTree(body);
    }

    private JsonNode productByName(JsonNode page, String name) {
        for (JsonNode item : page.get("items")) {
            if (name.equals(item.get("name").asText())) return item;
        }
        return null;
    }

    private List<String> offerSellers(long productId) throws Exception {
        JsonNode detail = send(get("/api/products/" + productId), 200);
        List<String> names = new ArrayList<>();
        detail.get("offers").forEach(o -> names.add(o.get("sellerName").asText()));
        return names;
    }

    // --- buyer side ----------------------------------------------------------------------------

    @Test
    void buyerSearchSummarisesOnlyOffersBuyersMaySee() throws Exception {
        JsonNode page = send(get("/api/products").param("q", "ultratech cement"), 200);

        JsonNode ultratech = productByName(page, "UltraTech PPC Cement 50 kg");
        assertThat(ultratech).isNotNull();
        // Visible offers: Shree, Kashmir, Om Sai, Patel(out of stock). Hidden: Gupta (pending), Quick (rejected), Bharat (stopped).
        assertThat(ultratech.get("offerCount").asInt()).isEqualTo(4);
        assertThat(ultratech.get("availableOfferCount").asInt()).isEqualTo(3);
        assertThat(ultratech.get("minPrice").decimalValue()).isEqualByComparingTo("385");   // not Patel's 380 (no stock), not Gupta's 370
        assertThat(ultratech.get("maxPrice").decimalValue()).isEqualByComparingTo("405");
    }

    @Test
    void productsOfferedOnlyByNonApprovedSellersAreNotListed() throws Exception {
        JsonNode page = send(get("/api/products").param("size", "50"), 200);

        assertThat(productByName(page, "Birla White WallCare Putty 40 kg")).isNull();
        assertThat(productByName(page, "UltraTech PPC Cement 50 kg")).isNotNull();
    }

    @Test
    void productDetailRanksOrderableOffersByPriceAndPushesOutOfStockLast() throws Exception {
        assertThat(offerSellers(productId(ULTRATECH))).containsExactly(
                "Kashmir Build Mart", "Shree Traders", "Om Sai Enterprises", "Patel Building Supplies");
    }

    @Test
    void priceSortingAndInStockFilterWork() throws Exception {
        JsonNode cheapestFirst = send(get("/api/products").param("categoryId",
                String.valueOf(jdbc.queryForObject("SELECT id FROM categories WHERE name = 'Cement'", Long.class)))
                .param("sort", "price_asc"), 200);
        assertThat(cheapestFirst.get("items").get(0).get("name").asText()).isEqualTo("ACC Gold Water Shield PPC 50 kg");

        send(get("/api/products").param("sort", "cheapest-please"), 400);
        send(get("/api/products").param("size", "500"), 400);
        send(get("/api/products").param("minPrice", "500").param("maxPrice", "100"), 400);
    }

    // --- seller side: validation & business rules -----------------------------------------------

    @Test
    void sellerEndpointsRequireAKnownSeller() throws Exception {
        send(get("/api/seller/listings"), 401);
        send(get("/api/seller/listings").header("X-Seller-Id", 999999), 401);
    }

    @Test
    void invalidValuesAreRejectedWithFieldErrors() throws Exception {
        long kashmir = sellerId("Kashmir Build Mart");
        JsonNode error = send(as(post("/api/seller/listings"), kashmir,
                Map.of("productId", productId("STL-BIND-18"), "price", -5, "stock", -1, "minOrderQty", 0)), 400);

        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("fieldErrors").has("price")).isTrue();
        assertThat(error.get("fieldErrors").has("stock")).isTrue();
        assertThat(error.get("fieldErrors").has("minOrderQty")).isTrue();

        // three decimals is not a valid rupee amount
        send(as(post("/api/seller/listings"), kashmir,
                Map.of("productId", productId("STL-BIND-18"), "price", 10.123, "stock", 1, "minOrderQty", 1)), 400);
    }

    @Test
    void duplicateActiveListingIsRejected() throws Exception {
        long kashmir = sellerId("Kashmir Build Mart");
        JsonNode error = send(as(post("/api/seller/listings"), kashmir,
                Map.of("productId", productId(ULTRATECH), "price", 380, "stock", 10, "minOrderQty", 1)), 409);
        assertThat(error.get("code").asText()).isEqualTo("DUPLICATE_LISTING");
    }

    @Test
    void rejectedSellerIsReadOnly() throws Exception {
        long quick = sellerId("Quick Cement Depot");
        JsonNode error = send(as(post("/api/seller/listings"), quick,
                Map.of("productId", productId("CEM-DAL-OPC53-50"), "price", 400, "stock", 10, "minOrderQty", 1)), 403);
        assertThat(error.get("code").asText()).isEqualTo("SELLER_NOT_ALLOWED");
        send(as(get("/api/seller/listings"), quick, null), 200);   // can still look
    }

    @Test
    void newListingAppearsInBuyerView() throws Exception {
        long product = productId("CEM-DAL-OPC53-50");
        long kashmir = sellerId("Kashmir Build Mart");

        JsonNode created = send(as(post("/api/seller/listings"), kashmir,
                Map.of("productId", product, "price", 399.50, "stock", 40, "minOrderQty", 5)), 201);

        assertThat(created.get("price").decimalValue()).isEqualByComparingTo("399.50");
        assertThat(offerSellers(product)).contains("Kashmir Build Mart");
    }

    // --- seller side: authorization ------------------------------------------------------------

    @Test
    void sellerCannotModifyAnotherSellersListing() throws Exception {
        long shreeListing = listingId("Shree Traders", "CEM-ACC-PPC-50");
        long kashmir = sellerId("Kashmir Build Mart");
        long version = dbVersion(shreeListing);

        send(as(patch("/api/seller/listings/" + shreeListing), kashmir,
                Map.of("version", version, "price", 1)), 404);
        send(as(put("/api/seller/listings/" + shreeListing + "/status"), kashmir,
                Map.of("version", version, "status", "INACTIVE")), 404);

        assertThat(dbVersion(shreeListing)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT price FROM seller_listings WHERE id = ?", BigDecimal.class, shreeListing))
                .isEqualByComparingTo("372.00");
    }

    @Test
    void sellerListingsAreScopedToTheCaller() throws Exception {
        JsonNode page = send(as(get("/api/seller/listings").param("size", "50"), sellerId("Kashmir Build Mart"), null), 200);
        for (JsonNode item : page.get("items")) {
            long id = item.get("id").asLong();
            assertThat(jdbc.queryForObject("SELECT seller_id FROM seller_listings WHERE id = ?", Long.class, id))
                    .isEqualTo(sellerId("Kashmir Build Mart"));
        }
    }

    // --- stop / resume -------------------------------------------------------------------------

    @Test
    void stoppingAndResumingUpdatesWhatBuyersSee() throws Exception {
        long product = productId("CEM-AMB-PPC-50");
        long kashmir = sellerId("Kashmir Build Mart");
        long listing = listingId("Kashmir Build Mart", "CEM-AMB-PPC-50");

        JsonNode stopped = send(as(put("/api/seller/listings/" + listing + "/status"), kashmir,
                Map.of("version", dbVersion(listing), "status", "INACTIVE")), 200);
        assertThat(stopped.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(offerSellers(product)).doesNotContain("Kashmir Build Mart");

        send(as(put("/api/seller/listings/" + listing + "/status"), kashmir,
                Map.of("version", dbVersion(listing), "status", "ACTIVE")), 200);
        assertThat(offerSellers(product)).contains("Kashmir Build Mart");
    }

    // --- seller status -------------------------------------------------------------------------

    @Test
    void sellerStatusControlsBuyerVisibility() throws Exception {
        long royal = sellerId("Royal Construction Depot");          // PENDING, no listings in seed data
        long product = productId("CEM-JKL-PPC-50");                 // no offers in seed data

        // A pending seller can prepare a listing...
        send(as(post("/api/seller/listings"), royal,
                Map.of("productId", product, "price", 380, "stock", 50, "minOrderQty", 10)), 201);
        // ...but buyers cannot see it.
        assertThat(productByName(send(get("/api/products").param("q", "JK Lakshmi"), 200), "JK Lakshmi Pro+ PPC Cement 50 kg")).isNull();
        assertThat(offerSellers(product)).isEmpty();

        // Admin token is required to change status.
        send(as(patch("/api/admin/sellers/" + royal + "/status"), royal, Map.of("status", "APPROVED")), 403);

        // Approval makes the offer visible immediately.
        send(as(patch("/api/admin/sellers/" + royal + "/status").header("X-Admin-Token", "dev-admin-token"), royal,
                Map.of("status", "APPROVED")), 200);
        JsonNode page = send(get("/api/products").param("q", "JK Lakshmi"), 200);
        assertThat(productByName(page, "JK Lakshmi Pro+ PPC Cement 50 kg").get("minPrice").decimalValue()).isEqualByComparingTo("380");

        // Rejection hides it again and makes the seller read-only.
        send(as(patch("/api/admin/sellers/" + royal + "/status").header("X-Admin-Token", "dev-admin-token"), royal,
                Map.of("status", "REJECTED")), 200);
        assertThat(offerSellers(product)).isEmpty();
        send(as(post("/api/seller/listings"), royal,
                Map.of("productId", productId("CEM-DAL-OPC53-50"), "price", 400, "stock", 1, "minOrderQty", 1)), 403);
    }

    // --- concurrency ---------------------------------------------------------------------------

    @Test
    void concurrentEditsOfTheSameVersionLetExactlyOneWin() throws Exception {
        long bharat = sellerId("Bharat Hardware & Steel");
        long listing = listingId("Bharat Hardware & Steel", "CEM-ACC-PPC-50");
        long version = dbVersion(listing);
        int writers = 8;

        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            BigDecimal price = new BigDecimal("300").add(new BigDecimal(i));
            results.add(pool.submit(() -> {
                start.await();
                MockHttpServletRequestBuilder req = as(patch("/api/seller/listings/" + listing), bharat,
                        Map.of("version", version, "price", price));
                return mvc.perform(req).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();

        int ok = 0, conflicts = 0;
        for (Future<Integer> f : results) {
            int status = f.get(30, TimeUnit.SECONDS);
            if (status == 200) ok++;
            else if (status == 409) conflicts++;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflicts).isEqualTo(writers - 1);
        assertThat(dbVersion(listing)).isEqualTo(version + 1);
    }
}
