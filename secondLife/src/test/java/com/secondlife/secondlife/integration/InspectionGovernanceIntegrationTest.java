package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.ChecklistItemResultDto;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.integration.shipping.ShippingProviderRouter;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShippingPickupAddressRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class InspectionGovernanceIntegrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ShippingPickupAddressRepository pickupAddresses;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired ItemRepository items;
    @Autowired PostRepository posts;
    @Autowired InspectionOrderRepository orders;
    @Autowired JwtTokenProvider tokens;
    @Autowired InspectionConfigurationService configurations;
    @Autowired InspectionChecklistService checklists;
    @Autowired EntityManager entityManager;
    @Autowired InspectionOrderChecklistService orderChecklists;
    @MockitoBean NotificationService notifications;
    @MockitoBean ShippingProviderRouter carrier;
    @Autowired InspectionShippingQuoteService inspectionShipping;
    @MockitoSpyBean WalletService wallets;
    @Autowired InspectionFeeSettlementService settlements;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private User center;
    private User otherCenter;
    private User admin;
    private User seller;
    private User buyer;
    private User inspector;
    private Category category;
    private Item riceCooker;

    @BeforeEach
    void fixture() {
        center = actor("INSPECTION_CENTER");
        otherCenter = actor("INSPECTION_CENTER");
        admin = actor("ADMIN");
        seller = actor("SELLER");
        ShippingPickupAddress pickup = new ShippingPickupAddress();
        pickup.setUserId(seller.getId());
        pickup.setAddressJson(json.writeValueAsString(new ShippingAddress("Seller", "0900000000", "Seller pickup",
                new BigDecimal("10.7769"), new BigDecimal("106.7009"))));
        pickupAddresses.saveAndFlush(pickup);
        buyer = actor("BUYER");
        inspector = actor("INSPECTOR");
        inspector.setManager(center);
        users.saveAndFlush(inspector);
        category = new Category();
        category.setName("Kitchen governance " + UUID.randomUUID());
        category = categories.saveAndFlush(category);
        riceCooker = item("Mechanical rice cooker");
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.preview(any())).thenAnswer(invocation -> json.valueToTree(Map.of(
                "total_fee", 50000, "expires_at", Instant.now().plusSeconds(600).toString(),
                "provider_quote", Map.of("quotationId", UUID.randomUUID().toString(), "serviceType", "MOTORCYCLE"))));
    }

    @Test
    void centerSubmitsPendingItemChecklistAndCannotApproveIt() throws Exception {
        UUID id = propose(riceCooker, "L2", "Check heating");
        mvc.perform(get("/api/v1/inspector-center/checklists/" + id).header("Authorization", auth(center)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.itemId").value(riceCooker.getId().toString()));
        mvc.perform(post("/api/v1/admin/inspection/checklist-proposals/" + id + "/review")
                        .header("Authorization", auth(center)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        quote(listing(riceCooker), center).andExpect(status().isConflict());
    }

    @Test
    void sameRoomUsesDifferentApprovedItemChecklistsAndLevelPrices() throws Exception {
        Item oven = item("Oven");
        UUID riceChecklist = approve(propose(riceCooker, "L2", "Check rice cooking"));
        UUID ovenChecklist = approve(propose(oven, "L3", "Check oven modes"));
        UUID riceFee = approveFee(center, "L2", 50000);
        approveFee(center, "L3", 75000);

        var riceConfig = configurations.resolve(listing(riceCooker), center.getId());
        var ovenConfig = configurations.resolve(listing(oven), center.getId());
        assertEquals(riceChecklist, riceConfig.checklistTemplateId());
        assertEquals(ovenChecklist, ovenConfig.checklistTemplateId());
        assertEquals(InspectionComplexityLevel.L2, riceConfig.complexityLevel());
        assertEquals(InspectionComplexityLevel.L3, ovenConfig.complexityLevel());
        assertEquals(0, new BigDecimal("50000").compareTo(riceConfig.inspectionFee()));
        assertEquals(0, new BigDecimal("75000").compareTo(ovenConfig.inspectionFee()));
        quote(listing(riceCooker), center).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseInspectionFee").value(50000))
                .andExpect(jsonPath("$.inspectionConfiguration.feeProposalId").value(riceFee.toString()));
        mvc.perform(get("/api/v1/inspection/centers").header("Authorization", auth(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void differentCentersHaveIndependentLevelPrices() throws Exception {
        approve(propose(riceCooker, "L2", "Check rice cooking"));
        approveFee(center, "L2", 50000);
        approveFee(otherCenter, "L2", 65000);
        Post product = listing(riceCooker);
        assertEquals(0, new BigDecimal("50000").compareTo(configurations.resolve(product, center.getId()).inspectionFee()));
        assertEquals(0, new BigDecimal("65000").compareTo(configurations.resolve(product, otherCenter.getId()).inspectionFee()));
        assertEquals(center.getId(), configurations.resolve(product, null).inspectionCenterId());
    }

    @Test
    void approvedChecklistWithoutApprovedLevelPriceNeverFallsBackToConfiguredCategoryFee() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        quote(listing(riceCooker), center).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Trung tâm chưa có giá được duyệt cho cấp L2"));
    }

    @Test
    void oldOrderKeepsItsChecklistComplexityAndMoneyAfterNewApproval() throws Exception {
        UUID first = approve(propose(riceCooker, "L2", "Original criterion"));
        approveFee(center, "L2", 50000);
        approveFee(center, "L3", 75000);
        InspectionOrder order = snapshotOrder(listing(riceCooker));
        InspectionChecklistResponse original = orderChecklists.getForOrder(inspector.getId(), order.getId());

        JsonNode revised = json.readTree(mvc.perform(put("/api/v1/inspector-center/checklists/" + first)
                        .header("Authorization", auth(center)).contentType(MediaType.APPLICATION_JSON)
                        .content(proposalBody(riceCooker, "L3", "New criterion")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString());
        UUID next = UUID.fromString(revised.get("id").asText());
        assertEquals(first, configurations.resolve(order.getPost(), center.getId()).checklistTemplateId());
        approve(next);

        var latest = configurations.resolve(order.getPost(), center.getId());
        assertEquals(next, latest.checklistTemplateId());
        assertEquals(InspectionComplexityLevel.L3, latest.complexityLevel());
        assertEquals(0, new BigDecimal("75000").compareTo(latest.inspectionFee()));
        assertEquals(original, orderChecklists.getForOrder(inspector.getId(), order.getId()));
        assertEquals(first, order.getChecklistTemplate().getId());
        assertEquals(InspectionComplexityLevel.L2, order.getComplexityLevel());
        assertEquals(0, new BigDecimal("50000").compareTo(order.getInspectionFee()));
    }

    @Test
    void otherCenterCannotReadUpdateOrWithdrawAnUnapprovedProposal() throws Exception {
        UUID id = propose(riceCooker, "L2", "Private draft");
        mvc.perform(get("/api/v1/inspector-center/checklists/" + id).header("Authorization", auth(otherCenter)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/inspector-center/checklists/" + id).header("Authorization", auth(otherCenter))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody(riceCooker, "L3", "Changed")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/inspector-center/checklists/" + id).header("Authorization", auth(otherCenter)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inspector-center/checklists/proposals").header("Authorization", auth(otherCenter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void rejectionNeedsReasonAndDoesNotActivateChecklist() throws Exception {
        UUID id = propose(riceCooker, "L2", "Check heating");
        mvc.perform(post("/api/v1/admin/inspection/checklist-proposals/" + id + "/review")
                        .header("Authorization", auth(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"Thiếu tiêu chí\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.active").value(false));
        quote(listing(riceCooker), center).andExpect(status().isConflict());
    }

    @Test
    void approvedChecklistCannotBeDeletedAndDuplicateReviewConflicts() throws Exception {
        UUID id = approve(propose(riceCooker, "L2", "Check heating"));
        mvc.perform(delete("/api/v1/inspector-center/checklists/" + id).header("Authorization", auth(center)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/inspection/checklist-proposals/" + id + "/review")
                        .header("Authorization", auth(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void requiredResultsAreValidatedAgainstSnapshotAndClientCannotRenameCriteria() throws Exception {
        approve(propose(riceCooker, "L2", "Canonical criterion"));
        approveFee(center, "L2", 50000);
        InspectionOrder order = snapshotOrder(listing(riceCooker));
        UUID criterion = orderChecklists.getForOrder(inspector.getId(), order.getId()).items().getFirst().id();
        assertThrows(BadRequestException.class, () -> orderChecklists.validateResults(order, List.of(), "PASSED"));
        assertThrows(BadRequestException.class, () -> orderChecklists.validateResults(order,
                List.of(new ChecklistItemResultDto(UUID.randomUUID(), "Foreign criterion", true, null)), "PASSED"));
        var failed = new ChecklistItemResultDto(criterion, "Forged title", false, "Does not heat");
        assertThrows(BadRequestException.class, () -> orderChecklists.validateResults(order, List.of(failed), "PASSED"));
        var passed = new ChecklistItemResultDto(criterion, "Forged title", true, "Verified");
        assertThrows(BadRequestException.class, () -> orderChecklists.validateResults(order, List.of(passed, passed), "PASSED"));
        assertEquals("Canonical criterion", orderChecklists.validateResults(order, List.of(passed), "PASSED").getFirst().itemTitle());
        assertFalse(orderChecklists.validateResults(order, List.of(failed), "FAILED").getFirst().passed());
    }

    @Test
    void onlyOrderParticipantsAndAssignedCenterCanReadItsChecklist() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        InspectionOrder order = snapshotOrder(listing(riceCooker));
        String path = "/api/v1/inspector/orders/" + order.getId() + "/checklist";
        mvc.perform(get(path).header("Authorization", auth(otherCenter))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", auth(actor("BUYER")))).andExpect(status().isForbidden());
        for (User participant : List.of(center, inspector, seller, buyer, admin)) {
            mvc.perform(get(path).header("Authorization", auth(participant))).andExpect(status().isOk());
        }
    }

    @Test
    void buyerRequestStoresApprovedConfigurationAndFeeShares() throws Exception {
        UUID template = approve(propose(riceCooker, "L2", "Check heating"));
        UUID fee = approveFee(center, "L2", 50000);
        Post product = listing(riceCooker);
        mvc.perform(post("/api/v1/inspection/requests").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "postId", product.getId(), "orderType", "AT_CENTER", "feePayer", "SPLIT_50_50",
                                "buyerConsentZin", true, "inspectionCenterId", center.getId(),
                                "expectedChecklistTemplateId", template, "expectedFeeProposalId", fee,
                                "inspectionShippingQuoteId", inspectionShipping.quote(product.getId(), center.getId(), null).id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionCenterId").value(center.getId().toString()))
                .andExpect(jsonPath("$.checklistTemplateId").value(template.toString()))
                .andExpect(jsonPath("$.complexityLevel").value("L2"))
                .andExpect(jsonPath("$.inspectionFee").value(50000))
                .andExpect(jsonPath("$.buyerFeeShare").value(75000))
                .andExpect(jsonPath("$.sellerFeeShare").value(75000));
        assertTrue(orders.findActiveByPostId(product.getId()).orElseThrow().getChecklistSnapshotJson().contains("Check heating"));
    }

    @Test
    void quoteRejectsAChangedPriceVersionBeforeCreatingAnOrder() throws Exception {
        UUID template = approve(propose(riceCooker, "L2", "Check heating"));
        UUID originalFee = approveFee(center, "L2", 50000);
        approveFee(center, "L2", 70000);
        mvc.perform(post("/api/v1/inspection/quote").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("postId", listing(riceCooker).getId(),
                                "inspectionCenterId", center.getId(), "expectedChecklistTemplateId", template,
                                "expectedFeeProposalId", originalFee))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidChecklistAndNegativeFeesAreRejected() throws Exception {
        mvc.perform(post("/api/v1/inspector-center/checklists").header("Authorization", auth(center))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Invalid", "items", List.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/inspector-center/level-fees/propose").header("Authorization", auth(center))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complexityLevel\":\"L2\",\"baseInspectionFee\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retiredOrUnclassifiedItemsCannotReceiveAnInventedLowestLevelFee() throws Exception {
        Post product = listing(riceCooker);
        quote(product, center).andExpect(status().isConflict());
        riceCooker.setSelectable(false);
        items.saveAndFlush(riceCooker);
        quote(product, center).andExpect(status().isConflict());
    }

    private User actor(String role) {
        User user = new User(UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCode(role).orElseThrow());
        if ("INSPECTION_CENTER".equals(role)) {
            UserProfile profile = new UserProfile();
            profile.setFullName("Inspection center");
            profile.setLatitude(10.7300);
            profile.setLongitude(106.7050);
            profile.setPhone("02838229999");
            profile.setProvince("Thành phố Hồ Chí Minh");
            profile.setWard("Tân Phong");
            profile.setStreetAddress("Test inspection center");
            user.setProfile(profile);
        }
        return users.saveAndFlush(user);
    }

    @Test
    void checklistPaginationLoadsCompleteCriteriaWithoutGrowingSqlPerRow() throws Exception {
        for (int index = 0; index < 6; index++) {
            propose(item("Specific appliance " + index), "L2", "Operational criterion " + index);
        }
        UUID centerId = center.getId();
        entityManager.flush();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            entityManager.clear();
            statistics.clear();
            var single = checklists.getProposals(centerId, null, PageRequest.of(0, 1));
            long singleCount = statistics.getPrepareStatementCount();

            entityManager.clear();
            statistics.clear();
            var page = checklists.getProposals(centerId, null, PageRequest.of(0, 3));
            long pageCount = statistics.getPrepareStatementCount();

            assertEquals(6, page.getTotalElements());
            assertEquals(3, page.getNumberOfElements());
            assertEquals(single.getContent().getFirst().id(), page.getContent().getFirst().id());
            assertTrue(page.stream().allMatch(template -> template.items().size() == 1));
            assertEquals(singleCount, pageCount, "Loading more checklist rows must not add individual criterion queries");
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void buyerAndSellerApprovePayAndInspectorSubmitsTheApprovedItemChecklist() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        wallets.getWalletByUserId(buyer.getId()).setBalance(new BigDecimal("200000"));
        Post product = listing(riceCooker);
        UUID shippingQuoteId = inspectionShipping.quote(product.getId(), center.getId(), null).id();
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/inspection/requests").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "postId", product.getId(), "orderType", "AT_CENTER", "feePayer", "BUYER_PAYS",
                                "inspectionShippingQuoteId", shippingQuoteId,
                                "buyerConsentZin", true, "inspectionCenterId", center.getId()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID orderId = UUID.fromString(created.get("id").asText());
        mvc.perform(post("/api/v1/inspector-center/orders/" + orderId + "/assign").header("Authorization", auth(center))
                        .param("inspectorId", inspector.getId().toString())).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/inspection/requests/" + orderId + "/respond").header("Authorization", auth(seller))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\",\"sellerConsentZin\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/inspection/orders/" + orderId + "/pay-fee").header("Authorization", auth(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED_PENDING_DISPATCH"));
        mvc.perform(post("/api/v1/inspector-center/orders/" + orderId + "/assign").header("Authorization", auth(center))
                        .param("inspectorId", inspector.getId().toString())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/inspector/orders/" + orderId + "/start").header("Authorization", auth(inspector)))
                .andExpect(status().isOk());
        UUID criterion = orderChecklists.getForOrder(inspector.getId(), orderId).items().getFirst().id();
        mvc.perform(post("/api/v1/inspector/orders/" + orderId + "/result").header("Authorization", auth(inspector))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("status", "PASSED",
                                "checklistResults", List.of(Map.of("itemId", criterion, "itemTitle", "Client title", "passed", true))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PASSED"));
        assertTrue(orders.findById(orderId).orElseThrow().getChecklistResultJson().contains("Check heating"));
    }

    @Test
    void homeTravelUsesTheSelectedCentersCoordinates() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        approveFee(otherCenter, "L2", 50000);
        otherCenter.getProfile().setLatitude(10.90);
        otherCenter.getProfile().setLongitude(106.80);
        users.saveAndFlush(otherCenter);
        Post product = listing(riceCooker);
        JsonNode first = json.readTree(quote(product, center).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode second = json.readTree(quote(product, otherCenter).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(first.get("travelDistanceKm").decimalValue().compareTo(second.get("travelDistanceKm").decimalValue()) < 0);
    }

    @Test
    void missingGpsDoesNotProduceAQuoteUsingInventedCoordinates() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        center.getProfile().setLatitude(null);
        users.saveAndFlush(center);
        quote(listing(riceCooker), center).andExpect(status().isConflict());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void walletFailureRollsBackSettlementAndRetryDoesNotPayTwice() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        UUID buyerWallet = wallets.getWalletByUserId(buyer.getId()).getId();
        wallets.getWalletByUserId(center.getId());
        jdbc.update("UPDATE user_wallets SET balance=200000 WHERE id=?", buyerWallet);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID orderId = transaction.execute(status -> {
            InspectionOrder order = snapshotOrder(listing(riceCooker));
            order.setShippingFee(new BigDecimal("50000"));
            order.setReturnShippingReserveFee(new BigDecimal("50000"));
            order.setBuyerFeeShare(new BigDecimal("150000"));
            order.setSellerFeeShare(BigDecimal.ZERO);
            wallets.processPayment(buyer.getId(), new BigDecimal("150000"), order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
            order.setBuyerPaidAt(Instant.now());
            orders.saveAndFlush(order);
            return order.getId();
        });
        doThrow(new IllegalStateException("Simulated wallet failure")).when(wallets)
                .processRefund(eq(buyer.getId()), any(BigDecimal.class), eq(orderId), eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> settlements.settle(
                orders.findByIdForUpdate(orderId).orElseThrow(), InspectionSettlementOutcome.PASSED_PURCHASED, "Test rollback")));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inspection_fee_settlements WHERE inspection_order_id=?", Integer.class, orderId));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT balance FROM user_wallets WHERE user_id=?", BigDecimal.class, center.getId())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=?", Integer.class, orderId));
        doCallRealMethod().when(wallets)
                .processRefund(eq(buyer.getId()), any(BigDecimal.class), eq(orderId), eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        for (int attempt = 0; attempt < 2; attempt++) {
            transaction.execute(status -> settlements.settle(orders.findByIdForUpdate(orderId).orElseThrow(),
                    InspectionSettlementOutcome.PASSED_PURCHASED, "Retry"));
        }
        assertEquals(0, new BigDecimal("50000").compareTo(jdbc.queryForObject("SELECT balance FROM user_wallets WHERE user_id=?", BigDecimal.class, center.getId())));
        assertEquals(0, new BigDecimal("100000").compareTo(jdbc.queryForObject("SELECT balance FROM user_wallets WHERE user_id=?", BigDecimal.class, buyer.getId())));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM wallet_transactions WHERE reference_id=?", Integer.class, orderId));
    }

    @Test
    void currentLevelFeesReturnOnlyTheLatestApprovedPriceForEachLevel() throws Exception {
        approveFee(center, "L2", 50000);
        approveFee(center, "L2", 70000);
        approveFee(otherCenter, "L2", 90000);
        mvc.perform(get("/api/v1/inspector-center/level-fees").header("Authorization", auth(center)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].baseInspectionFee").value(70000));
    }

    @Test
    void centerListsDoNotExposeOrdersBoundToAnotherCenter() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        snapshotOrder(listing(riceCooker));
        for (String path : List.of("/api/v1/inspector-center/orders", "/api/v1/inspector/orders/all")) {
            mvc.perform(get(path).header("Authorization", auth(otherCenter)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
            mvc.perform(get(path).header("Authorization", auth(center)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Test
    void technicianFromAnotherCenterCannotTakeOverAssignedInspection() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        InspectionOrder order = snapshotOrder(listing(riceCooker));
        order.setStatus("DISPATCHED");
        User outsider = actor("INSPECTOR");
        outsider.setManager(otherCenter);
        users.saveAndFlush(outsider);
        mvc.perform(post("/api/v1/inspector/orders/" + order.getId() + "/start")
                        .header("Authorization", auth(outsider)))
                .andExpect(status().isForbidden());
        assertEquals(inspector.getId(), order.getInspector().getId());
    }

    @Test
    void invalidSortIsReportedAsBadRequestInsteadOfARepositoryError() throws Exception {
        mvc.perform(get("/api/v1/inspector-center/checklists/proposals").header("Authorization", auth(center)).param("sort", "string"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReviewsForTheSameItemActivateExactlyOneVersion() throws Exception {
        UUID first = propose(riceCooker, "L2", "First proposal");
        UUID second = propose(riceCooker, "L3", "Competing proposal");
        String adminAuthorization = auth(admin);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(first, second).stream().map(id -> executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/admin/inspection/checklist-proposals/" + id + "/review")
                        .header("Authorization", adminAuthorization).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}")).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            List<Integer> statuses = List.of(futures.getFirst().get(20, TimeUnit.SECONDS), futures.getLast().get(20, TimeUnit.SECONDS));
            assertEquals(1L, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1L, statuses.stream().filter(code -> code == 409).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sellerCanReclassifyOnlyAnUnlockedLegacyListingTheyOwn() throws Exception {
        Item oldItem = item("Unsplit historical item");
        oldItem.setSelectable(false);
        items.saveAndFlush(oldItem);
        Post product = listing(oldItem);
        String path = "/api/v1/posts/my-posts/" + product.getId() + "/item";
        mvc.perform(patch(path)
                        .header("Authorization", auth(seller)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("itemId", riceCooker.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(riceCooker.getId().toString()));
        assertEquals(riceCooker.getId(), product.getItemId());
    }

    private Item item(String name) {
        Item item = new Item();
        item.setName(name + " " + UUID.randomUUID());
        item.setCategory(category);
        return items.saveAndFlush(item);
    }

    private Post listing(Item item) {
        Post post = new Post();
        post.setUser(seller);
        post.setCategoryId(category.getId());
        post.setItemId(item.getId());
        post.setTitle("Household appliance");
        post.setStatus("ACTIVE");
        post.setPrice(new BigDecimal("1000000"));
        post.setShippingWeight(1700);
        post.setShippingLength(25);
        post.setShippingWidth(20);
        post.setShippingHeight(15);
        return posts.saveAndFlush(post);
    }

    private String auth(User actor) { return "Bearer " + tokens.generateAccessToken(actor); }

    private String proposalBody(Item item, String level, String criterion) {
        return json.writeValueAsString(Map.of("categoryId", category.getId(), "itemId", item.getId(),
                "complexityLevel", level, "title", "Checklist " + item.getName(), "items",
                List.of(Map.of("itemTitle", criterion, "mandatory", true, "orderIndex", 1))));
    }

    private UUID propose(Item item, String level, String criterion) throws Exception {
        JsonNode body = json.readTree(mvc.perform(post("/api/v1/inspector-center/checklists")
                        .header("Authorization", auth(center)).contentType(MediaType.APPLICATION_JSON)
                        .content(proposalBody(item, level, criterion)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private UUID approve(UUID id) throws Exception {
        mvc.perform(post("/api/v1/admin/inspection/checklist-proposals/" + id + "/review")
                        .header("Authorization", auth(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
        return id;
    }

    private UUID approveFee(User author, String level, long price) throws Exception {
        JsonNode body = json.readTree(mvc.perform(post("/api/v1/inspector-center/level-fees/propose")
                        .header("Authorization", auth(author)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("complexityLevel", level, "baseInspectionFee", price))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        mvc.perform(post("/api/v1/admin/inspection/fee-proposals/" + id + "/review")
                        .header("Authorization", auth(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}")).andExpect(status().isOk());
        return id;
    }

    private ResultActions quote(Post post, User center) throws Exception {
        return mvc.perform(post("/api/v1/inspection/quote").header("Authorization", auth(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("postId", post.getId(), "inspectionCenterId", center.getId()))));
    }

    @Test
    void homeQuoteDoesNotRequireCarrierQuotationsOrPretendCenterShippingIsFree() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50000);
        mvc.perform(post("/api/v1/inspection/quote").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "postId", listing(riceCooker).getId(), "inspectionCenterId", center.getId(), "orderType", "AT_HOME"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.atHomeTotalFee").isNumber())
                .andExpect(jsonPath("$.shippingQuote").doesNotExist())
                .andExpect(jsonPath("$.atCenterTotalFee").doesNotExist());
        verifyNoInteractions(carrier);
    }

    @Test
    void splitPriceShownToBuyerMatchesStoredSharesForAnOddTotal() throws Exception {
        approve(propose(riceCooker, "L2", "Check heating"));
        approveFee(center, "L2", 50001);
        Post product = listing(riceCooker);
        JsonNode quote = json.readTree(quote(product, center).andExpect(status().isOk())
                .andExpect(jsonPath("$.atCenterTotalFee").value(150001))
                .andExpect(jsonPath("$.atCenterSplitOptions.SPLIT_50_50").value(75000.5))
                .andExpect(jsonPath("$.atCenterPackage.suggestedSplit50").value(75000.5))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/inspection/requests").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "postId", product.getId(), "inspectionCenterId", center.getId(), "orderType", "AT_CENTER",
                                "feePayer", "SPLIT_50_50", "buyerConsentZin", true,
                                "inspectionShippingQuoteId", quote.path("shippingQuote").path("id").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerFeeShare").value(75000.5))
                .andExpect(jsonPath("$.sellerFeeShare").value(75000.5));
    }

    private InspectionOrder snapshotOrder(Post post) {
        var configuration = configurations.resolve(post, center.getId());
        InspectionOrder order = new InspectionOrder();
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setInspector(inspector);
        order.setInspectionFee(configuration.inspectionFee());
        configurations.snapshot(order, configuration);
        return orders.saveAndFlush(order);
    }
}
