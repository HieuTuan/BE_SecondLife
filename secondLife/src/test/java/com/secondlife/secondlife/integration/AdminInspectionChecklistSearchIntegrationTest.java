package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.InspectionChecklistSearchRequest;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.InspectionChecklistItem;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionChecklistTemplateRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.NotificationService;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
class AdminInspectionChecklistSearchIntegrationTest {
    private static final String ROOT = "/api/v1/admin/inspection/checklist-proposals";
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() {
        return GhnTestDatabase.available(DATABASE_ENV);
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ItemRepository items;
    @Autowired InspectionChecklistTemplateRepository templates;
    @Autowired InspectionFeeProposalRepository fees;
    @Autowired PostRepository posts;
    @Autowired InspectionOrderRepository orders;
    @Autowired JwtTokenProvider tokens;
    @Autowired InspectionChecklistService checklists;
    @Autowired InspectionConfigurationService configurations;
    @Autowired EntityManager entityManager;
    @MockitoBean NotificationService notifications;

    private User admin;
    private User center;
    private User otherCenter;
    private User buyer;
    private Category category;

    @BeforeEach
    void fixture() {
        admin = actor("ADMIN", "Administrator");
        center = actor("INSPECTION_CENTER", "Trung tâm kiểm định Đông Nam");
        otherCenter = actor("INSPECTION_CENTER", "Trung tâm kiểm định miền Bắc");
        buyer = actor("BUYER", "Buyer");
        category = new Category();
        category.setName("Phòng làm việc " + UUID.randomUUID());
        category = categories.saveAndFlush(category);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ban lam viec", "BÀN LÀM", "  bÀn   làm   VIỆC  ", "ban viec"})
    void partialSearchMatchesCaseAccentsAndOrderedWords(String keyword) throws Exception {
        InspectionChecklistTemplate wanted = template(item("Bàn làm việc"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Tiêu chuẩn bàn làm việc");
        template(item("Ghế xoay"), InspectionComplexityLevel.L1,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Tiêu chuẩn ghế xoay");

        mvc.perform(list().param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].itemName").value("Bàn làm việc"));
    }

    @Test
    void searchNormalizesUppercaseVietnameseItemNamesIndependentlyOfDatabaseLocale() throws Exception {
        InspectionChecklistTemplate wanted = template(item("BÀN ĐIỆN"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Kiểm tra thiết bị");
        mvc.perform(list().param("keyword", "ban dien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()));
    }

    @Test
    void searchAlsoNormalizesCombiningMarksInStoredVietnameseText() throws Exception {
        String decomposed = Normalizer.normalize("Tủ lạnh gia đình", Normalizer.Form.NFD);
        InspectionChecklistTemplate wanted = template(item(decomposed), InspectionComplexityLevel.L3,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, decomposed);
        mvc.perform(list().param("keyword", "TU LANH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void searchTreatsWildcardAndEscapeCharactersAsLiteralText(String literal) throws Exception {
        InspectionChecklistTemplate wanted = template(item("Thiết bị " + literal + " chuyên dụng"),
                InspectionComplexityLevel.L2, InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Kiểm tra");
        template(item("Thiết bị thông thường"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Kiểm tra");
        mvc.perform(list().param("keyword", literal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()));
    }

    @Test
    void keywordFindsCategoryCenterDescriptionAndTitle() throws Exception {
        InspectionChecklistTemplate wanted = template(item("Thiết bị gia dụng"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Quy chuẩn thiết bị");
        wanted.setDescription("Đo độ cách điện và kiểm tra nhiệt độ");
        templates.saveAndFlush(wanted);

        for (String keyword : List.of("phong lam viec", "dong nam", "cach dien", "quy chuan")) {
            mvc.perform(list().param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()));
        }
    }

    @Test
    void allExactFiltersAreAppliedBeforePaginationAndCount() throws Exception {
        Item wantedItem = item("Bàn ban công");
        InspectionChecklistTemplate wanted = template(wantedItem, InspectionComplexityLevel.L1,
                InspectionChecklistStatus.APPROVED, true, center, "Tiêu chuẩn đang áp dụng");
        template(item("Bàn ăn"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Đề xuất khác");
        template(item("Ghế bành"), InspectionComplexityLevel.L1,
                InspectionChecklistStatus.APPROVED, true, otherCenter, "Trung tâm khác");
        mvc.perform(list()
                        .param("categoryId", category.getId().toString())
                        .param("itemId", wantedItem.getId().toString())
                        .param("complexityLevel", "L1").param("status", "APPROVED")
                        .param("active", "true").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(wanted.getId().toString()));
        mvc.perform(list().param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(list().param("itemId", wantedItem.getId().toString()).param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void noMatchAndOutOfRangePagesHaveCorrectTotals() throws Exception {
        for (int index = 0; index < 4; index++) {
            template(item("Bàn thử nghiệm " + index), InspectionComplexityLevel.L2,
                    InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Tiêu chuẩn bàn");
        }
        mvc.perform(list().param("keyword", "ban").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1));
        mvc.perform(list().param("keyword", "ban").param("page", "4").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(list().param("keyword", "khongcosanphamnay"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void legacyRowsStaySearchableWithoutInventingAnItemLevelOrPrice() throws Exception {
        InspectionChecklistTemplate legacy = template(null, null, InspectionChecklistStatus.LEGACY, true,
                center, "Bộ chuẩn cũ");
        fee(center, InspectionComplexityLevel.L1, InspectionFeeProposalStatus.APPROVED, "50000", Instant.now());
        mvc.perform(list().param("keyword", "chuan cu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(legacy.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].itemId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].complexityLevel").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].pricing.priceStatus").value("LEGACY"))
                .andExpect(jsonPath("$.data.items[0].pricing.baseInspectionFee").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].pricing.applicable").value(false));
    }

    @Test
    void pendingChecklistShowsApprovedLevelPriceAsPreviewAndPendingPriceSeparately() throws Exception {
        InspectionChecklistTemplate checklist = template(item("Nồi cơm cơ"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Tiêu chuẩn nồi cơm");
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "40000",
                Instant.now().minusSeconds(3600));
        InspectionFeeProposal latest = fee(center, InspectionComplexityLevel.L2,
                InspectionFeeProposalStatus.APPROVED, "50000", Instant.now().minusSeconds(60));
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.PENDING_APPROVAL, "60000", null);
        InspectionFeeProposal pending = fee(center, InspectionComplexityLevel.L2,
                InspectionFeeProposalStatus.PENDING_APPROVAL, "65000", null);
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.REJECTED, "99000", null);
        fee(otherCenter, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "90000", Instant.now());

        JsonNode response = detail(checklist);
        assertMoney(response.at("/pricing/baseInspectionFee"), "50000");
        assertMoney(response.at("/pricing/pendingBaseInspectionFee"), "65000");
        assertEquals(latest.getId().toString(), response.at("/pricing/feeProposalId").asText());
        assertEquals(pending.getId().toString(), response.at("/pricing/pendingFeeProposalId").asText());
        assertEquals(center.getId().toString(), response.at("/pricing/inspectionCenterId").asText());
        assertEquals("Trung tâm kiểm định Đông Nam", response.at("/pricing/inspectionCenterName").asText());
        assertEquals("APPROVED", response.at("/pricing/priceStatus").asText());
        assertFalse(response.at("/pricing/applicable").asBoolean());
        assertEquals("CHECKLIST_NOT_EFFECTIVE", response.at("/pricing/unavailableReason").asText());
        assertEquals(checklist.getTitle(), response.get("title").asText());
        assertEquals(checklist.getItems().getFirst().getId().toString(), response.at("/items/0/id").asText());
    }

    @Test
    void pendingPriceIsNotReturnedAsApprovedPriceAndMissingPriceIsNotZero() throws Exception {
        InspectionChecklistTemplate pendingOnly = template(item("Bếp từ"), InspectionComplexityLevel.L3,
                InspectionChecklistStatus.APPROVED, true, center, "Kiểm định bếp");
        fee(center, InspectionComplexityLevel.L3, InspectionFeeProposalStatus.PENDING_APPROVAL, "120000", null);
        JsonNode pending = detail(pendingOnly);
        assertTrue(pending.at("/pricing/baseInspectionFee").isNull());
        assertMoney(pending.at("/pricing/pendingBaseInspectionFee"), "120000");
        assertEquals("PENDING_APPROVAL", pending.at("/pricing/priceStatus").asText());
        assertFalse(pending.at("/pricing/applicable").asBoolean());

        InspectionChecklistTemplate unconfigured = template(item("Máy lọc nước"), InspectionComplexityLevel.L4,
                InspectionChecklistStatus.APPROVED, true, center, "Kiểm định máy lọc nước");
        JsonNode absent = detail(unconfigured);
        assertTrue(absent.at("/pricing/baseInspectionFee").isNull());
        assertEquals("NOT_CONFIGURED", absent.at("/pricing/priceStatus").asText());
        assertFalse(absent.at("/pricing/applicable").asBoolean());
    }

    @Test
    void approvedZeroPriceIsConfiguredAndApplicable() throws Exception {
        InspectionChecklistTemplate checklist = template(item("Ấm đun"), InspectionComplexityLevel.L1,
                InspectionChecklistStatus.APPROVED, true, center, "Kiểm định ấm");
        fee(center, InspectionComplexityLevel.L1, InspectionFeeProposalStatus.APPROVED, "0", Instant.now());
        JsonNode result = detail(checklist);
        assertMoney(result.at("/pricing/baseInspectionFee"), "0");
        assertEquals("APPROVED", result.at("/pricing/priceStatus").asText());
        assertTrue(result.at("/pricing/applicable").asBoolean());
    }

    @Test
    void unavailableCenterAndRetiredItemCannotHaveAnApplicablePrice() throws Exception {
        InspectionChecklistTemplate checklist = template(item("Lò nướng"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.APPROVED, true, center, "Kiểm định lò nướng");
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "70000", Instant.now());
        center.setAccountStatus(AccountStatus.LOCKED);
        users.saveAndFlush(center);
        JsonNode inactive = detail(checklist);
        assertFalse(inactive.at("/pricing/applicable").asBoolean());
        assertEquals("CENTER_UNAVAILABLE", inactive.at("/pricing/priceStatus").asText());

        center.setAccountStatus(AccountStatus.ACTIVE);
        users.saveAndFlush(center);
        checklist.getCatalogItem().setSelectable(false);
        items.saveAndFlush(checklist.getCatalogItem());
        JsonNode retired = detail(checklist);
        assertFalse(retired.at("/pricing/applicable").asBoolean());
        assertEquals("ITEM_NOT_SELECTABLE", retired.at("/pricing/unavailableReason").asText());
    }

    @Test
    void searchRequiresAdminAndRejectsMalformedFilters() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).header("Authorization", auth(buyer))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).header("Authorization", auth(center))).andExpect(status().isForbidden());
        mvc.perform(list().param("keyword", "a".repeat(121))).andExpect(status().isBadRequest());
        mvc.perform(list().param("sort", "string")).andExpect(status().isBadRequest());
        mvc.perform(list().param("complexityLevel", "L5")).andExpect(status().isBadRequest());
        mvc.perform(list().param("status", "PENDING")).andExpect(status().isBadRequest());
        mvc.perform(list().param("itemId", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void serviceSearchCannotBroadenCenterOwnershipOrExposeAnotherCentersPendingPrice() throws Exception {
        InspectionChecklistTemplate own = template(item("Bàn làm việc"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.APPROVED, true, center, "Tiêu chuẩn của trung tâm A");
        template(item("Bàn ăn"), InspectionComplexityLevel.L2,
                InspectionChecklistStatus.PENDING_APPROVAL, false, otherCenter, "Đề xuất riêng của trung tâm B");
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.PENDING_APPROVAL, "65000", null);
        InspectionChecklistSearchRequest otherAuthor = new InspectionChecklistSearchRequest(null, null,
                category.getId(), null, null, otherCenter.getId(), null);
        var hidden = checklists.searchProposals(center.getId(), otherAuthor, PageRequest.of(0, 20));
        assertEquals(0, hidden.getTotalElements());

        mvc.perform(get("/api/v1/inspector-center/checklists/" + own.getId())
                        .header("Authorization", auth(otherCenter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing").doesNotExist());
    }

    @Test
    void explicitSearchRouteReturnsTheSameContractAsTheExistingList() throws Exception {
        InspectionChecklistTemplate checklist = template(item("Máy giặt"), InspectionComplexityLevel.L3,
                InspectionChecklistStatus.PENDING_APPROVAL, false, center, "Tiêu chuẩn máy giặt");
        mvc.perform(get(ROOT + "/search").header("Authorization", auth(admin))
                        .param("inspectionCenterId", center.getId().toString()).param("keyword", "may giat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(checklist.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].pricing.priceStatus").value("NOT_CONFIGURED"));
    }

    @Test
    void liveManagementPricesDoNotRefreshChecklistSnapshotsInExistingOrders() throws Exception {
        Item device = item("Nồi cơm điện tử");
        InspectionChecklistTemplate checklist = template(device, InspectionComplexityLevel.L2,
                InspectionChecklistStatus.APPROVED, true, center, "Checklist đã chốt");
        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "50000",
                Instant.now().minusSeconds(3600));
        User seller = actor("SELLER", "Seller");
        Post post = new Post();
        post.setUser(seller);
        post.setCategoryId(category.getId());
        post.setItemId(device.getId());
        post.setTitle("Nồi cơm đã qua sử dụng");
        post.setStatus("ACTIVE");
        post.setPrice(new BigDecimal("1000000"));
        posts.saveAndFlush(post);
        InspectionOrder order = new InspectionOrder();
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setStatus("PENDING");
        var configuration = configurations.resolve(post, center.getId());
        order.setInspectionFee(configuration.inspectionFee());
        configurations.snapshot(order, configuration);
        orders.saveAndFlush(order);
        String stored = order.getChecklistSnapshotJson();

        fee(center, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "80000", Instant.now());
        assertMoney(detail(checklist).at("/pricing/baseInspectionFee"), "80000");
        mvc.perform(get("/api/v1/inspector/orders/" + order.getId() + "/checklist")
                        .header("Authorization", auth(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(checklist.getId().toString()))
                .andExpect(jsonPath("$.pricing").doesNotExist());
        assertEquals(stored, orders.findById(order.getId()).orElseThrow().getChecklistSnapshotJson());
        assertEquals(0, new BigDecimal("50000").compareTo(order.getInspectionFee()));
    }

    @Test
    void paginationAndPriceEnrichmentUseConstantSqlAsRowsIncrease() {
        for (int index = 0; index < 6; index++) {
            User owner = actor("INSPECTION_CENTER", "Trung tâm " + index);
            template(item("Thiết bị thử " + index), InspectionComplexityLevel.L2,
                    InspectionChecklistStatus.PENDING_APPROVAL, false, owner, "Bộ kiểm tra " + index);
            fee(owner, InspectionComplexityLevel.L2, InspectionFeeProposalStatus.APPROVED, "50000", Instant.now());
        }
        UUID adminId = admin.getId();
        InspectionChecklistSearchRequest filter = new InspectionChecklistSearchRequest(null,
                InspectionChecklistStatus.PENDING_APPROVAL, category.getId(), null, null, null, null);
        entityManager.flush();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            entityManager.clear();
            statistics.clear();
            var single = checklists.searchProposals(adminId, filter,
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));
            long singleCount = statistics.getPrepareStatementCount();

            entityManager.clear();
            statistics.clear();
            var page = checklists.searchProposals(adminId, filter,
                    PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createdAt")));
            long pageCount = statistics.getPrepareStatementCount();

            assertEquals(6, page.getTotalElements());
            assertEquals(3, page.getNumberOfElements());
            assertEquals(single.getContent().getFirst().id(), page.getContent().getFirst().id());
            assertEquals(3, page.stream().map(row -> row.createdById()).distinct().count());
            assertTrue(page.stream().allMatch(row -> row.items().size() == 1));
            assertEquals(singleCount, pageCount, "Fetching more checklist rows must not add per-row price/user queries");
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private MockHttpServletRequestBuilder list() {
        return get(ROOT).header("Authorization", auth(admin))
                .param("inspectionCenterId", center.getId().toString())
                .param("sort", "createdAt,desc");
    }

    private JsonNode detail(InspectionChecklistTemplate template) throws Exception {
        return json.readTree(mvc.perform(get(ROOT + "/" + template.getId()).header("Authorization", auth(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    private User actor(String role, String name) {
        User actor = new User(UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        actor.addRole(roles.findByCode(role).orElseThrow());
        UserProfile profile = new UserProfile();
        profile.setFullName(name);
        actor.setProfile(profile);
        return users.saveAndFlush(actor);
    }

    private Item item(String name) {
        Item item = new Item();
        item.setCategory(category);
        item.setName(name);
        return items.saveAndFlush(item);
    }

    private InspectionChecklistTemplate template(Item item, InspectionComplexityLevel level,
            InspectionChecklistStatus state, boolean active, User owner, String title) {
        InspectionChecklistTemplate template = new InspectionChecklistTemplate();
        template.setCategory(category);
        template.setCatalogItem(item);
        template.setComplexityLevel(level);
        template.setCreatedBy(owner);
        template.setTitle(title);
        template.setApprovalStatus(state);
        template.setActive(active);
        if (state == InspectionChecklistStatus.APPROVED) {
            template.setReviewedBy(admin);
            template.setReviewedAt(Instant.now());
        }
        InspectionChecklistItem criterion = new InspectionChecklistItem();
        criterion.setTemplate(template);
        criterion.setItemTitle("Kiểm tra theo tiêu chuẩn");
        criterion.setMandatory(true);
        criterion.setOrderIndex(1);
        template.setItems(List.of(criterion));
        return templates.saveAndFlush(template);
    }

    private InspectionFeeProposal fee(User owner, InspectionComplexityLevel level,
            InspectionFeeProposalStatus state, String amount, Instant reviewedAt) {
        InspectionFeeProposal proposal = new InspectionFeeProposal();
        proposal.setProposedBy(owner);
        proposal.setComplexityLevel(level);
        proposal.setStatus(state);
        proposal.setBaseInspectionFee(new BigDecimal(amount));
        proposal.setTiersJson("[]");
        if (state == InspectionFeeProposalStatus.APPROVED) {
            proposal.setReviewedBy(admin);
            proposal.setReviewedAt(reviewedAt);
        }
        return fees.saveAndFlush(proposal);
    }

    private String auth(User actor) {
        return "Bearer " + tokens.generateAccessToken(actor);
    }

    private void assertMoney(JsonNode value, String expected) {
        assertTrue(value.isNumber(), "An approved price must be present as a number");
        assertEquals(0, new BigDecimal(expected).compareTo(value.decimalValue()));
    }
}

