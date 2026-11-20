package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.service.MarketplaceListingService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=update",
        "app.seeder.enabled=true", "app.catalog.seed-enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
class MarketplaceListingQueryIntegrationTest {
    private static final String DATABASE_ENV = "MARKETPLACE_QUERY_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void stopOwnContainer() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MarketplaceListingService service;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean(name = "ollamaChatModel") ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") ChatModel google;

    private UUID sellerId;
    private UUID categoryId;
    private final List<UUID> activeIds = new ArrayList<>();

    @BeforeAll void startupDoesNotReadListingPages() {
        assertEquals(0, entityManagerFactory.unwrap(SessionFactory.class).getStatistics()
                .getEntityStatistics(com.secondlife.secondlife.entity.Post.class.getName()).getLoadCount());
    }

    @BeforeEach void listingsWithThreeImages() {
        activeIds.clear();
        sellerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash')",
                sellerId, sellerId + "@example.test");
        jdbc.update("INSERT INTO categories(id, name) VALUES (?, ?)", categoryId, "Query test " + categoryId);
        for (int index = 0; index < 9; index++) {
            UUID postId = UUID.randomUUID();
            if (index < 8) activeIds.add(postId);
            jdbc.update("""
                    INSERT INTO posts(id, user_id, category_id, title, status, created_at, published_at)
                    VALUES (?, ?, ?, ?, ?, now(), ?)
                    """, postId, sellerId, categoryId, "Product " + index, index < 8 ? "ACTIVE" : "DRAFT",
                    Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)));
            for (int image = 0; image < 3; image++) {
                jdbc.update("INSERT INTO post_images(post_id, image_position, image_url) VALUES (?, ?, ?)",
                        postId, image, imageUrl(postId, image));
            }
        }
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    @Test void pagedListingsLoadImagesWithBoundedQueriesAndKeepDatabasePagination() {
        var page = service.list(categoryId, null, PageRequest.of(1, 3, Sort.by("publishedAt").descending()));
        assertEquals(8, page.getTotalElements());
        assertEquals(1, page.getNumber());
        assertEquals(List.of(activeIds.get(4), activeIds.get(3), activeIds.get(2)),
                page.getContent().stream().map(listing -> listing.postId()).toList());
        for (var listing : page) {
            assertEquals(sellerId, listing.sellerId());
            assertEquals(List.of(imageUrl(listing.postId(), 0), imageUrl(listing.postId(), 1), imageUrl(listing.postId(), 2)),
                    listing.imageUrls());
        }
        long statements = entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
        assertEquals(4, statements, "Expected page, count, batched images and batched inspections");
    }

    @Test void listingDetailLoadsImagesAndInspectionInTwoQueries() {
        var listing = service.get(activeIds.getFirst());
        assertEquals(List.of(imageUrl(listing.postId(), 0), imageUrl(listing.postId(), 1), imageUrl(listing.postId(), 2)),
                listing.imageUrls());
        assertEquals(2, entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount());
    }

    @Test
    void publicEndpointsReturnLatestQualifyingInspectionAndExcludeUnsettledCancellations() throws Exception {
        UUID buyerId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (?, ?, 'test-hash')",
                buyerId, buyerId + "@example.test");

        UUID passedPost = activeIds.get(0);
        addInspection(passedPost, buyerId, "COMPLETED", "old-seal", "B", 1);
        addInspection(passedPost, buyerId, "PASSED", null, "A", 2);
        addInspection(passedPost, buyerId, "FAILED", null, "FAILED-GRADE", 3);
        addInspection(activeIds.get(1), buyerId, "COMPLETED", "public-seal", "A", 1);
        UUID returned = addInspection(activeIds.get(2), buyerId, "CANCELLED", null, "B", 1);
        recordSettlement(returned, "PASSED_BUYER_DEFAULTED", true);
        UUID pending = addInspection(activeIds.get(3), buyerId, "CANCELLED", null, "C", 1);
        recordSettlement(pending, "PASSED_BUYER_DEFAULTED", false);
        UUID expired = addInspection(activeIds.get(4), buyerId, "CANCELLED", null, "D", 1);
        recordSettlement(expired, "CANCELLED_BEFORE_INSPECTION", false);

        mvc.perform(get("/api/v1/posts").param("categoryId", categoryId.toString()).param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(8))
                .andExpect(jsonPath("$.data.content[7].isInspected").value(true))
                .andExpect(jsonPath("$.data.content[7].conditionGrade").value("A"))
                .andExpect(jsonPath("$.data.content[7].tamperSealId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[6].isInspected").value(true))
                .andExpect(jsonPath("$.data.content[6].tamperSealId").value("public-seal"))
                .andExpect(jsonPath("$.data.content[5].isInspected").value(true))
                .andExpect(jsonPath("$.data.content[5].conditionGrade").value("B"))
                .andExpect(jsonPath("$.data.content[4].isInspected").value(false))
                .andExpect(jsonPath("$.data.content[4].conditionGrade").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].isInspected").value(false))
                .andExpect(jsonPath("$.data.content[0].isInspected").value(false));
        mvc.perform(get("/api/v1/posts/search").param("categoryId", categoryId.toString())
                        .param("inspected", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/v1/posts/{postId}", passedPost))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isInspected").value(true))
                .andExpect(jsonPath("$.data.conditionGrade").value("A"));
    }

    @Test void marketplaceSearchCombinesFiltersBeforeSortingAndPaging() throws Exception {
        jdbc.update("INSERT INTO user_profiles (id, user_id, full_name, province) VALUES (?, ?, 'Seller', 'Hà Nội')",
                UUID.randomUUID(), sellerId);
        String warehouse = "Kho Dịch vụ, Đà Nẵng, Việt Nam";
        jdbc.update("INSERT INTO shipping_pickup_addresses (user_id, address_json) VALUES (?, ?)",
                sellerId, "{\"address\":\"" + warehouse + "\"}");
        mvc.perform(get("/api/v1/posts/{postId}", activeIds.getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerAddress").value(warehouse));
        mvc.perform(get("/api/v1/posts/search").param("categoryId", categoryId.toString()).param("location", "Hà Nội"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        jdbc.update("UPDATE posts SET title = ?, brand = ?, description = ?, item_condition = ?, price = ? WHERE id = ?",
                "Máy giặt cửa trước", "LG", "Động cơ inverter", "USED_GOOD", 3000000, activeIds.get(0));
        jdbc.update("UPDATE posts SET title = ?, description = ?, item_condition = ?, price = ? WHERE id = ?",
                "Máy giặt LG", "Động cơ inverter", "GOOD", 2500000, activeIds.get(1));
        jdbc.update("UPDATE posts SET title = ?, brand = ?, description = ?, item_condition = ?, price = ? WHERE id = ?",
                "Máy giặt", "Samsung", "Động cơ inverter, tương đương LG", "USED_GOOD", 2500000, activeIds.get(2));
        jdbc.update("UPDATE posts SET title = ?, description = ?, item_condition = ?, price = ? WHERE id = ?",
                "Máy giặt LGX", "Động cơ inverter", "USED_GOOD", 2500000, activeIds.get(3));
        jdbc.update("UPDATE posts SET title = ?, brand = ?, description = ?, item_condition = ?, price = ? WHERE id = ?",
                "Máy giặt", "LG", "Động cơ inverter", "LIKE_NEW", 2000000, activeIds.get(4));

        var filters = get("/api/v1/posts/search").param("categoryId", categoryId.toString())
                .param("q", " inverter ").param("brand", "lg").param("condition", "USED_GOOD")
                .param("minPrice", "2500000").param("maxPrice", "3000000")
                .param("location", "đà nẵng").param("sort", "priceAsc").param("size", "1");
        mvc.perform(filters)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].postId").value(activeIds.get(1).toString()));
        mvc.perform(filters.param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value(activeIds.get(0).toString()))
                .andExpect(jsonPath("$.data.content[0].brand").value("LG"));
        mvc.perform(get("/api/v1/posts/search").param("categoryId", categoryId.toString())
                        .param("q", "_").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/posts/search").param("minPrice", "100").param("maxPrice", "90"))
                .andExpect(status().isBadRequest());
    }

    private UUID addInspection(UUID postId, UUID buyerId, String status, String seal, String grade, long sequence) {
        UUID inspectionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inspection_orders (id, post_id, seller_id, buyer_id, inspection_scope, status,
                                               tamper_seal_id, condition_grade, created_at, inspection_fee, shipping_fee)
                VALUES (?, ?, ?, ?, 'BUYER_REQUEST', ?, ?, ?, ?, 100000, 30000)
                """, inspectionId, postId, sellerId, buyerId, status, seal, grade,
                Timestamp.from(Instant.parse("2026-01-02T00:00:00Z").plusSeconds(sequence)));
        return inspectionId;
    }

    private void recordSettlement(UUID inspectionId, String outcome, boolean returnSettled) {
        Timestamp settledAt = returnSettled ? Timestamp.from(Instant.now()) : null;
        jdbc.update("""
                INSERT INTO inspection_fee_settlements
                    (inspection_order_id, outcome, total_collected, return_shipping_settled_at)
                VALUES (?, ?, 0, ?)
                """, inspectionId, outcome, settledAt);
    }

    private String imageUrl(UUID postId, int position) {
        return "https://example.test/" + postId + "/" + position + ".jpg";
    }
}
