package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.CloudinaryService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class ListingImageUploadIntegrationTest {
    private static final GhnTestDatabase DATABASE = new GhnTestDatabase("LISTING_IMAGE_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("LISTING_IMAGE_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { DATABASE.close(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private SellerVerificationRepository verifications;
    @Autowired private CategoryRepository categories;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CloudinaryService cloud;
    @MockitoBean private AiChatService ai;
    private User seller;
    private UUID categoryId;

    @BeforeEach
    void setup() throws Exception {
        seller = new User("image-test-" + UUID.randomUUID() + "@example.test", "test-password-hash", AccountStatus.ACTIVE);
        seller.addRole(roles.findByCodeWithPermissions("SELLER").orElseThrow());
        seller = users.saveAndFlush(seller);
        var verification = new SellerVerification(seller, VerificationType.CITIZEN_ID, null,
                "https://example.test/identity-front", "https://example.test/identity-back");
        verification.setStatus(SellerVerificationStatus.APPROVED);
        verifications.saveAndFlush(verification);
        var category = new Category();
        category.setName("Image validation " + UUID.randomUUID());
        categoryId = categories.saveAndFlush(category).getId();
        when(cloud.uploadImage(any())).thenAnswer(call -> {
            String id = "listings/images/" + UUID.randomUUID();
            return new CloudinaryService.UploadedImage("https://example.test/" + id + ".jpg", id);
        });
    }

    @Test
    void authenticatedMultipartPersistsThreePhotosInARealDatabase() throws Exception {
        String body = mvc.perform(multipart("/api/v1/posts/init").file(file("valid-front.jpg", "image/jpeg"))
                        .file(file("valid-back.png", "image/png")).file(file("valid-label.webp", "image/webp"))
                        .param("categoryId", categoryId.toString()).param("mode", "manual")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID postId = UUID.fromString(mapper.readTree(body).get("postId").asText());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, postId));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, postId));
        assertEquals(0, creditEntries());
        verifyNoInteractions(ai);
    }

    @Test
    void corruptSecondPhotoReturnsAnIndexedErrorWithoutStorageDatabaseOrCreditChanges() throws Exception {
        mvc.perform(multipart("/api/v1/posts/init").file(file("valid-front.jpg", "image/jpeg"))
                        .file(file("fake-jpeg.jpg", "image/jpeg")).file(file("valid-label.webp", "image/webp"))
                        .param("categoryId", categoryId.toString()).param("mode", "manual")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("images[1]"));
        assertNoSideEffects();
    }

    @Test
    void duplicatePhotoReturnsAnIndexedErrorWithoutSideEffects() throws Exception {
        mvc.perform(multipart("/api/v1/posts/init").file(file("valid-front.jpg", "image/jpeg"))
                        .file(file("duplicate-front.jpg", "image/jpeg")).file(file("valid-label.webp", "image/webp"))
                        .param("categoryId", categoryId.toString()).param("mode", "manual")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("images[1]"));
        assertNoSideEffects();
    }

    private void assertNoSideEffects() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE user_id = ?", Integer.class, seller.getId()));
        assertEquals(0, creditEntries());
        verifyNoInteractions(cloud, ai);
    }

    private Integer creditEntries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM credit_ledger WHERE user_id = ?", Integer.class, seller.getId());
    }

    private String bearer() { return "Bearer " + tokens.generateAccessToken(seller); }

    private MockMultipartFile file(String name, String mime) throws Exception {
        return new MockMultipartFile("images", name, mime,
                Files.readAllBytes(Path.of("src/test/resources/image-validation", name)));
    }
}
