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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("databaseAvailable")
@SpringBootTest
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
        assertTrue(statements <= 3, "Expected at most page, count and batched image queries, got " + statements);
    }

    @Test void listingDetailLoadsItsImagesInOneQuery() {
        var listing = service.get(activeIds.getFirst());
        assertEquals(List.of(imageUrl(listing.postId(), 0), imageUrl(listing.postId(), 1), imageUrl(listing.postId(), 2)),
                listing.imageUrls());
        assertEquals(1, entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount());
    }

    private String imageUrl(UUID postId, int position) {
        return "https://example.test/" + postId + "/" + position + ".jpg";
    }
}
