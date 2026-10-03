package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false", "app.catalog.seed-enabled=true"
})
class CatalogIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired JwtTokenProvider jwt;
    @Autowired com.secondlife.secondlife.service.CatalogSeedService seed;

    @Test void startupSeedsSixRoomCategoriesAndTheirItemTypes() throws Exception {
        String token = auth("BUYER");
        mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        var categories = mapper.readTree(mvc.perform(get("/api/v1/categories").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var defaults = java.util.Set.of("Living Room", "Kitchen", "Bedroom", "Bathroom & Sanitation", "Balcony & Laundry", "Other");
        assertEquals(6, java.util.stream.StreamSupport.stream(categories.spliterator(), false)
                .filter(c -> defaults.contains(c.get("name").asText())).count());
        for (var category : categories) {
            if (!defaults.contains(category.get("name").asText())) continue;
            mvc.perform(get("/api/v1/items/category/" + category.get("id").asText()).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$[0].category.id").value(category.get("id").asText()));
        }
        assertEquals(36, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE seed_key IS NOT NULL", Integer.class));
    }

    @Test
    void adminWithoutCatalogPermissionIsRejected() throws Exception {
        UUID role = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'ADMIN'", UUID.class);
        UUID permission = jdbc.queryForObject("SELECT id FROM permissions WHERE code = 'ADMIN_CATALOG_MANAGE'", UUID.class);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?", role, permission);
        try {
            mvc.perform(post("/api/v1/categories").header("Authorization", auth("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rejected\"}"))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("INSERT INTO role_permissions(role_id, permission_id) VALUES(?, ?)", role, permission);
        }
    }

    @Test
    void catalogPermissionWithoutAdminRoleIsRejected() throws Exception {
        UUID role = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'BUYER'", UUID.class);
        UUID permission = jdbc.queryForObject("SELECT id FROM permissions WHERE code = 'ADMIN_CATALOG_MANAGE'", UUID.class);
        // Deliberately create an invalid role assignment directly in the test DB to verify the separate ADMIN guard.
        jdbc.update("INSERT INTO role_permissions(role_id, permission_id) VALUES(?, ?)", role, permission);
        try {
            mvc.perform(post("/api/v1/categories").header("Authorization", auth("BUYER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rejected\"}"))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?", role, permission);
        }
    }

    @Test void onlyAdminCanCrudAndDuplicateNamesAreRejected() throws Exception {
        String admin = auth("ADMIN"), buyer = auth("BUYER"), seller = auth("SELLER");
        String body = "{\"name\":\"Test Room\",\"description\":\"Test category\"}";
        mvc.perform(post("/api/v1/categories").header("Authorization", buyer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories").header("Authorization", seller).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        UUID category = createCategory(admin, "Test Room");
        mvc.perform(post("/api/v1/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" test room \"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/categories/" + category).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Room\",\"description\":\"New description\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Room"));
        UUID item = createItem(admin, category, "Table");
        mvc.perform(get("/api/v1/items/" + item).header("Authorization", buyer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/items").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + category + "\",\"name\":\" table \"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/items/" + item).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + category + "\",\"name\":\"Desk\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Desk"));
        mvc.perform(delete("/api/v1/categories/" + category).header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/items/" + item).header("Authorization", buyer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/items/" + item).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/categories/" + category).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/categories/" + category).header("Authorization", buyer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items/category/" + category).header("Authorization", buyer)).andExpect(status().isNotFound());
    }

    @Test void usedCategoryOrItemCannotBeDeletedOrMoved() throws Exception {
        String admin = auth("ADMIN"); UUID category = createCategory(admin, "Referenced Room");
        UUID item = createItem(admin, category, "Referenced Table"); UUID other = createCategory(admin, "Other Test Room");
        var owner = users.findAll().getFirst();
        Post p = new Post(); p.setUser(owner); p.setCategoryId(category); p.setItemId(item); posts.saveAndFlush(p);
        mvc.perform(delete("/api/v1/items/" + item).header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/categories/" + category).header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/items/" + item).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + other + "\",\"name\":\"Referenced Table\"}"))
                .andExpect(status().isConflict());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE items SET category_id = ? WHERE id = ?", other, item));
    }

    @Test void startupIsIdempotentPreservesEditsAndAdoptsExistingRows() throws Exception {
        String admin = auth("ADMIN");
        UUID room = jdbc.queryForObject("SELECT id FROM categories WHERE seed_key = 'bedroom'", UUID.class);
        UUID oldItem = jdbc.queryForObject("SELECT id FROM items WHERE seed_key = 'bedroom/desk'", UUID.class);
        mvc.perform(delete("/api/v1/items/" + oldItem).header("Authorization", admin)).andExpect(status().isNoContent());
        UUID adopted = createItem(admin, room, "Desk");
        seed.seedDefaults();
        assertEquals(adopted, jdbc.queryForObject("SELECT id FROM items WHERE seed_key = 'bedroom/desk'", UUID.class));

        mvc.perform(put("/api/v1/categories/" + room).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Phòng ngủ\",\"description\":\"Đã sửa\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/items/" + adopted).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", "Bàn học", "categoryId", room))))
                .andExpect(status().isOk());
        try {
            // Simulate two backend instances starting at once; the shared transaction lock serializes seed writes.
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> seed.seedDefaults());
                var second = executor.submit(() -> seed.seedDefaults());
                first.get(30, java.util.concurrent.TimeUnit.SECONDS);
                second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(room, jdbc.queryForObject("SELECT id FROM categories WHERE seed_key = 'bedroom'", UUID.class));
            assertEquals("Phòng ngủ", jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, room));
            assertEquals("Đã sửa", jdbc.queryForObject("SELECT description FROM categories WHERE id = ?", String.class, room));
            assertEquals("Bàn học", jdbc.queryForObject("SELECT name FROM items WHERE id = ?", String.class, adopted));
            assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE seed_key IS NOT NULL", Integer.class));
            assertEquals(36, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE seed_key IS NOT NULL", Integer.class));
        } finally {
            jdbc.update("UPDATE categories SET name = 'Bedroom' WHERE id = ?", room);
            jdbc.update("UPDATE items SET name = 'Desk' WHERE id = ?", adopted);
        }
        mvc.perform(delete("/api/v1/items/" + adopted).header("Authorization", admin)).andExpect(status().isNoContent());
        seed.seedDefaults(); seed.seedDefaults();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE seed_key = 'bedroom/desk'", Integer.class));
    }

    @Test void templatesAlsoProtectCatalogReferencesAndMissingItemsReturn404() throws Exception {
        String admin = auth("ADMIN"), buyer = auth("BUYER");
        UUID room = createCategory(admin, "Template Room"), item = createItem(admin, room, "Template Item");
        jdbc.update("INSERT INTO category_question_templates(id, category_id, item_id, template_text) VALUES(?, ?, ?, ?)",
                UUID.randomUUID(), room, item, "Template text");
        mvc.perform(delete("/api/v1/items/" + item).header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/categories/" + room).header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/items/" + UUID.randomUUID()).header("Authorization", buyer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/items").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No category\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items").param("categoryId", room.toString()).header("Authorization", buyer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(room.toString()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'ADMIN_CATALOG_MANAGE' AND r.code = 'ADMIN'
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'ADMIN_CATALOG_MANAGE' AND r.code <> 'ADMIN'
                """, Integer.class));
    }

    private UUID createCategory(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/categories").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(result).get("id").asText());
    }
    private UUID createItem(String token, UUID category, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("name", name, "categoryId", category))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(result).get("id").asText());
    }
    private String auth(String code) {
        User u = new User("catalog-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        u.addRole(roles.findByCodeWithPermissions(code).orElseThrow());
        return "Bearer " + jwt.generateAccessToken(users.saveAndFlush(u));
    }
}
