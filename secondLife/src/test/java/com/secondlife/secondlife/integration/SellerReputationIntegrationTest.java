package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable")
@SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class SellerReputationIntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("REPUTATION_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("REPUTATION_TEST_JDBC_URL"); }
    @AfterAll static void closeOwnDatabase() { database.close(); }
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", database::getJdbcUrl);
        r.add("spring.datasource.username", database::getUsername);
        r.add("spring.datasource.password", database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PostRepository posts;
    @Autowired OrderRepository orders;
    @Autowired SellerReputationService reputation;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper mapper;
    @MockitoBean AiPriceProvider priceProvider;
    @MockitoBean CloudinaryService cloudinary;
    @MockitoBean ReportEvidenceStorage evidenceStorage;
    @MockitoBean(name="ollamaChatModel") ChatModel ollama;
    @MockitoBean(name="googleGenAiChatModel") ChatModel google;

    @BeforeEach void privateEvidenceStorage() throws Exception {
        org.mockito.Mockito.when(evidenceStorage.upload(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            org.springframework.web.multipart.MultipartFile file=i.getArgument(0);
            return new ReportEvidenceStorage.StoredImage("report-evidence/"+UUID.randomUUID(),"image/png",file.getSize());
        });
        org.mockito.Mockito.when(evidenceStorage.read(org.mockito.ArgumentMatchers.anyString())).thenReturn(new byte[]{1,2,3});
    }

    @Test void newSellerHasProvisionalScoreAndNoPublicPrivateData() throws Exception {
        var seller = user("SELLER");
        mvc.perform(get("/api/v1/sellers/" + seller.getId() + "/reputation"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.evaluatedOrders").value(0))
            .andExpect(jsonPath("$.data.assessmentStatus").value("NO_TRANSACTIONS"))
            .andExpect(jsonPath("$.data.sellingRestricted").value(false))
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.reporterId").doesNotExist())
            .andExpect(jsonPath("$.data.evidenceUrls").doesNotExist());
    }
    @Test void reportQueueRequiresStaffPermissionAndSubmissionIsIdempotent() throws Exception {
        var seller = user("SELLER"); var buyer = user("BUYER"); var staff = user("STAFF");
        var post = listing(seller); var requestId = UUID.randomUUID();
        var body = Map.of("requestId", requestId, "postId", post.getId(), "reasonCode", "MISLEADING_DESCRIPTION",
                "description", "Product condition differs from the listing", "evidenceUrls", List.of());
        var result = mvc.perform(post("/api/v1/reports").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("OPEN")).andReturn();
        String reportId = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
        mvc.perform(post("/api/v1/reports").header("Authorization", bearer(buyer))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(reportId));
        mvc.perform(get("/api/v1/staff/reports?status=OPEN").header("Authorization", bearer(staff)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/staff/reports").header("Authorization", bearer(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/" + reportId).header("Authorization", bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sellers/" + seller.getId() + "/reputation"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void completedSalesAreCountedOnceAndNeedFiveEvaluatedOrders() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER");
        for (int i=0;i<5;i++) {
            var order=order(seller,buyer,true); recordGood(order); recordGood(order);
        }
        var data=reputation.get(seller.getId());
        assertEquals(5,data.goodTransactions()); assertEquals(0,data.badTransactions());
        assertEquals("100.00",data.score().toPlainString()); assertEquals("EVALUATED",data.assessmentStatus());
        mvc.perform(get("/api/v1/seller/reputation/me/history?page=0&size=2").header("Authorization",bearer(seller)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].formulaVersion").value("TRANSACTION_SUCCESS_RATE_V3"))
            .andExpect(jsonPath("$.data.items[0].actorId").doesNotExist());
    }
    @Test void confirmedSellerFaultConvertsGoodToBadOnceAndResolutionRetryCannotChangeDecision() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var order=order(seller,buyer,true); recordGood(order);
        UUID first=report(buyer,null,order.getId(),"MISLEADING_DESCRIPTION");
        start(staff,first);
        resolve(staff,first,"CONFIRMED","SELLER").andExpect(status().isOk());
        resolve(staff,first,"CONFIRMED","SELLER").andExpect(status().isOk());
        resolve(staff,first,"DISMISSED","NONE").andExpect(status().isConflict());
        UUID second=report(buyer,null,order.getId(),"COUNTERFEIT"); start(staff,second);
        resolve(staff,second,"CONFIRMED","SELLER").andExpect(status().isOk());
        recordGood(order);
        var data=reputation.get(seller.getId());
        assertEquals(0,data.goodTransactions()); assertEquals(1,data.badTransactions());
        assertEquals(1,data.confirmedIncidents()); assertEquals("0.00",data.score().toPlainString());
        assertEquals(2,eventCount(seller));
        assertEquals("BAD",jdbc.queryForObject("SELECT outcome FROM reputation_order_outcomes WHERE order_id=?",String.class,order.getId()));
    }
    @Test void pendingDismissedAndCarrierFaultReportsDoNotReduceReputation() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var order=order(seller,buyer,true); recordGood(order);
        UUID dismissed=report(buyer,null,order.getId(),"MISLEADING_DESCRIPTION");
        assertEquals(1,reputation.get(seller.getId()).goodTransactions());
        start(staff,dismissed); resolve(staff,dismissed,"DISMISSED","NONE").andExpect(status().isOk());
        UUID carrier=report(buyer,null,order.getId(),"ITEM_NOT_RECEIVED"); start(staff,carrier);
        resolve(staff,carrier,"CONFIRMED","CARRIER").andExpect(status().isOk());
        UUID buyerFault=report(buyer,null,order.getId(),"OTHER"); start(staff,buyerFault);
        resolve(staff,buyerFault,"CONFIRMED","BUYER").andExpect(status().isOk());
        assertEquals(1,reputation.get(seller.getId()).goodTransactions());
        assertEquals(0,reputation.get(seller.getId()).badTransactions()); assertEquals(1,eventCount(seller));
    }
    @Test void confirmedFaultBeforeSettlementDoesNotLaterGainGoodPoints() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var order=order(seller,buyer,false);
        assertThrows(com.secondlife.secondlife.exception.ConflictException.class,()->recordGood(order));
        UUID report=report(buyer,null,order.getId(),"MISLEADING_DESCRIPTION"); start(staff,report);
        resolve(staff,report,"CONFIRMED","SELLER").andExpect(status().isOk());
        assertEquals(0,reputation.get(seller.getId()).evaluatedOrders()); assertNull(reputation.get(seller.getId()).score());
        order.setStatus(OrderStatus.COMPLETED); order.setEscrowStatus(EscrowStatus.RELEASED); orders.saveAndFlush(order);
        recordGood(order);
        assertEquals(0,reputation.get(seller.getId()).goodTransactions()); assertEquals(1,reputation.get(seller.getId()).badTransactions());
    }
    @Test void secondDistinctFraudRestrictsNewSalesAndAdminCanLiftWithAudit() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF"); var admin=user("ADMIN");
        var listing=listing(seller); UUID report=report(buyer,listing.getId(),null,"FRAUD"); start(staff,report);
        resolve(staff,report,"CONFIRMED","SELLER").andExpect(status().isOk());
        var first=reputation.get(seller.getId());
        assertFalse(first.sellingRestricted()); assertEquals(1,first.seriousIncidents());
        assertEquals("SERIOUS_VIOLATION_HISTORY",first.warningCode());
        assertDoesNotThrow(()->reputation.requireSellingAllowed(seller.getId()));
        UUID second=report(buyer,listing(seller).getId(),null,"FRAUD"); start(staff,second);
        resolve(staff,second,"CONFIRMED","SELLER").andExpect(status().isOk());
        var data=reputation.get(seller.getId()); assertTrue(data.sellingRestricted()); assertEquals(2,data.seriousIncidents());
        assertEquals("SELLING_RESTRICTED",data.warningCode()); assertEquals(0,data.evaluatedOrders());
        assertThrows(com.secondlife.secondlife.exception.ForbiddenException.class,()->reputation.requireSellingAllowed(seller.getId()));
        mvc.perform(post("/api/v1/orders").header("Authorization",bearer(buyer)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("postId",listing.getId(),"requestId",UUID.randomUUID(),"shippingQuoteId",UUID.randomUUID()))))
            .andExpect(status().isForbidden());
        String url="/api/v1/admin/sellers/"+seller.getId()+"/reputation/lift-restriction";
        String body=mapper.writeValueAsString(Map.of("reason","Seller remedied the confirmed violation"));
        mvc.perform(post(url).header("Authorization",bearer(staff)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sellingRestricted").value(false))
            .andExpect(jsonPath("$.data.warningCode").value("SERIOUS_VIOLATION_HISTORY"));
        assertEquals(3,eventCount(seller));
        assertDoesNotThrow(()->reputation.requireSellingAllowed(seller.getId()));
    }
    @Test void firstConfirmedListingFraudWarnsWithoutCreatingBadOrders() throws Exception {
        var seller=user("SELLER"); var reporter=user("BUYER"); var staff=user("STAFF");
        UUID id=report(reporter,listing(seller).getId(),null,"FRAUD"); start(staff,id);
        resolve(staff,id,"CONFIRMED","SELLER").andExpect(status().isOk());
        var state=reputation.get(seller.getId());
        assertEquals(1,state.seriousIncidents()); assertEquals(1,state.confirmedIncidents());
        assertEquals(0,state.badTransactions()); assertEquals(0,state.evaluatedOrders());
        assertFalse(state.sellingRestricted()); assertEquals("SERIOUS_VIOLATION_HISTORY",state.warningCode());
        assertDoesNotThrow(()->reputation.requireSellingAllowed(seller.getId()));
    }
    @Test void multipleFraudReportsForSameListingRemainOneIncident() throws Exception {
        var seller=user("SELLER"); var staff=user("STAFF"); var p=listing(seller);
        UUID first=report(user("BUYER"),p.getId(),null,"FRAUD"); start(staff,first);
        resolve(staff,first,"CONFIRMED","SELLER").andExpect(status().isOk());
        UUID second=report(user("BUYER"),p.getId(),null,"FRAUD"); start(staff,second);
        resolve(staff,second,"CONFIRMED","SELLER").andExpect(status().isOk());
        resolve(staff,second,"CONFIRMED","SELLER").andExpect(status().isOk());
        var state=reputation.get(seller.getId());
        assertEquals(1,state.seriousIncidents()); assertEquals(1,state.confirmedIncidents());
        assertFalse(state.sellingRestricted()); assertEquals(1,eventCount(seller));
    }
    @Test void nonFraudViolationDoesNotTurnFirstFraudWarningIntoRestriction() throws Exception {
        var seller=user("SELLER"); var reporter=user("BUYER"); var staff=user("STAFF");
        UUID fraud=report(reporter,listing(seller).getId(),null,"FRAUD"); start(staff,fraud);
        resolve(staff,fraud,"CONFIRMED","SELLER").andExpect(status().isOk());
        UUID other=report(reporter,listing(seller).getId(),null,"OTHER"); start(staff,other);
        resolve(staff,other,"CONFIRMED","SELLER").andExpect(status().isOk());
        var state=reputation.get(seller.getId());
        assertEquals(2,state.confirmedIncidents()); assertEquals(1,state.seriousIncidents());
        assertFalse(state.sellingRestricted());
    }
    @Test void orderFraudEscalationCountsOnceAndSecondOrderTriggersRestriction() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var first=order(seller,buyer,true); recordGood(first);
        UUID other=report(buyer,null,first.getId(),"OTHER"); start(staff,other);
        resolve(staff,other,"CONFIRMED","SELLER").andExpect(status().isOk());
        UUID fraud=report(buyer,null,first.getId(),"FRAUD"); start(staff,fraud);
        resolve(staff,fraud,"CONFIRMED","SELLER").andExpect(status().isOk());
        resolve(staff,fraud,"CONFIRMED","SELLER").andExpect(status().isOk());
        var warning=reputation.get(seller.getId());
        assertEquals(1,warning.seriousIncidents()); assertEquals(1,warning.badTransactions());
        assertFalse(warning.sellingRestricted());
        var second=order(seller,buyer,true); recordGood(second);
        UUID secondFraud=report(buyer,null,second.getId(),"FRAUD"); start(staff,secondFraud);
        resolve(staff,secondFraud,"CONFIRMED","SELLER").andExpect(status().isOk());
        var restricted=reputation.get(seller.getId());
        assertEquals(2,restricted.seriousIncidents()); assertEquals(2,restricted.badTransactions());
        assertTrue(restricted.sellingRestricted());
    }
    @Test void onlyAssignedStaffMayResolveAndCannotReviewOwnReport() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF"); var other=user("STAFF");
        UUID id=report(buyer,listing(seller).getId(),null,"OTHER"); start(staff,id);
        mvc.perform(post("/api/v1/staff/reports/"+id+"/start-review").header("Authorization",bearer(other))).andExpect(status().isConflict());
        resolve(other,id,"CONFIRMED","SELLER").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/staff/reports").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("unprivileged").roles("STAFF")))
            .andExpect(status().isForbidden());
        UUID own=report(staff,listing(seller).getId(),null,"OTHER");
        mvc.perform(post("/api/v1/staff/reports/"+own+"/start-review").header("Authorization",bearer(staff))).andExpect(status().isForbidden());
        resolve(staff,id,"CONFIRMED",null).andExpect(status().isBadRequest());
    }
    @Test void reportValidationOwnershipAndPrivatePagination() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var outsider=user("BUYER"); var listing=listing(seller);
        var order=order(seller,buyer,true);
        var req=new HashMap<String,Object>(); req.put("requestId",UUID.randomUUID()); req.put("reasonCode","OTHER");
        req.put("description","Test report");
        submit(buyer,req).andExpect(status().isBadRequest());
        req.put("postId",listing.getId()); req.put("orderId",order.getId()); submit(buyer,req).andExpect(status().isBadRequest());
        req.remove("postId"); submit(outsider,req).andExpect(status().isForbidden());
        req.remove("orderId"); req.put("postId",listing.getId()); submit(seller,req).andExpect(status().isBadRequest());
        req.put("evidenceUrls",List.of("https://evil.example/image.jpg")); submit(buyer,req).andExpect(status().isBadRequest());
        req.put("evidenceUrls",List.of("https://res.cloudinary.com/wrong-cloud/image/upload/evidence.jpg")); submit(buyer,req).andExpect(status().isBadRequest());
        req.put("evidenceUrls",List.of(uploadEvidence(buyer)));
        var result=submit(buyer,req).andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
        req.put("description","Changed content on same requestId"); submit(buyer,req).andExpect(status().isConflict());
        req.put("requestId",UUID.randomUUID()); submit(buyer,req).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/reports/me?page=0&size=1").header("Authorization",bearer(buyer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/reports/me").header("Authorization",bearer(outsider)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/reports/"+id).header("Authorization",bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sellers/"+UUID.randomUUID()+"/reputation")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/reputation/me/history").header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
    }
    @Test void twoConcurrentReportsForSameSaleCannotCountTheBadOutcomeTwice() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var order=order(seller,buyer,true); recordGood(order);
        UUID a=report(buyer,null,order.getId(),"OTHER"),b=report(buyer,null,order.getId(),"MISLEADING_DESCRIPTION");
        start(staff,a); start(staff,b);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var first=executor.submit(()->{gate.await(); return resolve(staff,a,"CONFIRMED","SELLER").andReturn().getResponse().getStatus();});
            var second=executor.submit(()->{gate.await(); return resolve(staff,b,"CONFIRMED","SELLER").andReturn().getResponse().getStatus();});
            gate.countDown(); assertEquals(200,first.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(200,second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(0,reputation.get(seller.getId()).goodTransactions()); assertEquals(1,reputation.get(seller.getId()).badTransactions());
        assertEquals(1,reputation.get(seller.getId()).confirmedIncidents());
    }
    @Test void scoreAuditCannotBeModifiedOrDeleted() {
        var seller=user("SELLER"); recordGood(order(seller,user("BUYER"),true));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("DELETE FROM seller_reputation_events WHERE seller_id=?",seller.getId()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE seller_reputation_events SET good_after=99 WHERE seller_id=?",seller.getId()));
        assertEquals(1,eventCount(seller));
    }
    @Test void publicCloudinaryUrlsCannotBeUsedAsPrivateReportEvidence() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER");
        submit(buyer,Map.of("requestId",UUID.randomUUID(),"postId",listing(seller).getId(),"reasonCode","OTHER",
            "description","Sensitive report evidence","evidenceUrls",List.of("https://res.cloudinary.com/test-cloud/image/upload/private-screenshot.jpg")))
            .andExpect(status().isBadRequest());
    }
    @Test void privateEvidenceIsOwnerOnlyUntilSubmittedAndThenOnlyReporterOrAuthorizedReviewerCanFetchIt() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var outsider=user("BUYER"); var staff=user("STAFF");
        String image=uploadEvidence(buyer);
        mvc.perform(get(image).header("Authorization",bearer(buyer))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get(image)).andExpect(status().isUnauthorized());
        mvc.perform(get(image).header("Authorization",bearer(staff))).andExpect(status().isForbidden());
        var body=Map.<String,Object>of("requestId",UUID.randomUUID(),"postId",listing(seller).getId(),"reasonCode","OTHER",
                "description","Private screenshot evidence","evidenceUrls",List.of(image));
        submit(outsider,body).andExpect(status().isForbidden());
        submit(buyer,body).andExpect(status().isCreated()); submit(buyer,body).andExpect(status().isCreated());
        mvc.perform(get(image).header("Authorization",bearer(staff))).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3}));
        mvc.perform(get(image).header("Authorization",bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get(image).header("Authorization",bearer(outsider))).andExpect(status().isForbidden());
        submit(buyer,Map.of("requestId",UUID.randomUUID(),"postId",listing(seller).getId(),"reasonCode","OTHER",
            "description","Cannot reuse attached evidence","evidenceUrls",List.of(image))).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_reports WHERE reporter_id=?",Integer.class,buyer.getId()));
    }
    @Test void multipleFaultTypesForOneOrderStillCountAsOneFailedTransaction() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        for(int i=0;i<17;i++) recordGood(order(seller,buyer,true));
        for(int i=0;i<3;i++) {
            var o=order(seller,buyer,true); recordGood(o);
            o.setPaidAt(java.time.Instant.now().minusSeconds(180000));
            o.setHandoverDeadline(java.time.Instant.now().minusSeconds(7200));
            o.setCarrierPickedUpAt(java.time.Instant.now().minusSeconds(3600)); orders.saveAndFlush(o);
            UUID first=report(buyer,null,o.getId(),"MISLEADING_DESCRIPTION"); start(staff,first); resolve(staff,first,"CONFIRMED","SELLER").andExpect(status().isOk());
            UUID late=report(buyer,null,o.getId(),"LATE_HANDOVER"); start(staff,late); resolve(staff,late,"CONFIRMED","SELLER").andExpect(status().isOk());
        }
        var result=reputation.get(seller.getId()); assertEquals(20,result.evaluatedOrders());
        assertEquals(3,result.badTransactions()); assertEquals(17,result.goodTransactions()); assertEquals("85.00",result.score().toPlainString());
        assertEquals(3,result.lateHandoverOrders()); assertEquals(3,result.handoverEvaluatedOrders());
    }
    @Test void latePickupAloneNeverPenalizesSellerAndStaffCanAttributeCarrierFault() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var o=order(seller,buyer,true); o.setPaidAt(java.time.Instant.now().minusSeconds(180000));
        o.setHandoverDeadline(java.time.Instant.now().minusSeconds(7200)); o.setSellerReadyAt(java.time.Instant.now().minusSeconds(8000));
        o.setCarrierPickedUpAt(java.time.Instant.now().minusSeconds(3600)); orders.saveAndFlush(o); recordGood(o);
        UUID late=report(buyer,null,o.getId(),"LATE_HANDOVER"); start(staff,late);
        resolve(staff,late,"CONFIRMED","CARRIER").andExpect(status().isOk());
        assertEquals("100.00",reputation.get(seller.getId()).score().toPlainString());
        assertEquals(0,reputation.get(seller.getId()).lateHandoverOrders());
        mvc.perform(get("/api/v1/orders/"+o.getId()+"/fulfillment").header("Authorization",bearer(buyer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.lateHandoverCandidate").value(true));
        mvc.perform(get("/api/v1/orders/"+o.getId()+"/fulfillment").header("Authorization",bearer(user("BUYER")))).andExpect(status().isForbidden());
    }
    @Test void oldOrOnTimeOrdersCannotBeConfirmedAsLateAndReviewRollsBack() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var o=order(seller,buyer,true); recordGood(o);
        UUID late=report(buyer,null,o.getId(),"LATE_HANDOVER"); start(staff,late);
        resolve(staff,late,"CONFIRMED","SELLER").andExpect(status().isConflict());
        assertEquals("IN_REVIEW",jdbc.queryForObject("SELECT status FROM user_reports WHERE id=?",String.class,late));
        assertEquals(0,reputation.get(seller.getId()).badTransactions());
        o.setHandoverDeadline(java.time.Instant.now()); o.setCarrierPickedUpAt(o.getHandoverDeadline()); orders.saveAndFlush(o);
        resolve(staff,late,"CONFIRMED","SELLER").andExpect(status().isConflict());
    }
    @Test void sellerReadinessUsesServerTimestampAndIsIdempotent() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var o=order(seller,buyer,false);
        o.setPaidAt(java.time.Instant.now()); o.setHandoverDeadline(o.getPaidAt().plusSeconds(172800)); orders.saveAndFlush(o);
        String url="/api/v1/orders/"+o.getId()+"/ready-for-pickup";
        mvc.perform(put(url).header("Authorization",bearer(buyer))).andExpect(status().isForbidden());
        var first=mvc.perform(put(url).header("Authorization",bearer(seller))).andExpect(status().isOk()).andReturn();
        String at=mapper.readTree(first.getResponse().getContentAsString()).path("data").path("sellerReadyAt").asText();
        mvc.perform(put(url).header("Authorization",bearer(seller))).andExpect(status().isOk()).andExpect(jsonPath("$.data.sellerReadyAt").value(at));
        assertEquals(0,reputation.get(seller.getId()).evaluatedOrders());
    }
    @Test void buyerReturnAfterConfirmedLatenessDoesNotBecomeASellerReturnFault() throws Exception {
        var seller=user("SELLER"); var buyer=user("BUYER"); var staff=user("STAFF");
        var o=order(seller,buyer,false); o.setPaidAt(java.time.Instant.now().minusSeconds(180000));
        o.setHandoverDeadline(java.time.Instant.now().minusSeconds(7200)); o.setCarrierPickedUpAt(java.time.Instant.now().minusSeconds(3600)); orders.saveAndFlush(o);
        UUID late=report(buyer,null,o.getId(),"LATE_HANDOVER"); start(staff,late); resolve(staff,late,"CONFIRMED","SELLER").andExpect(status().isOk());
        o.setStatus(OrderStatus.CANCELLED); o.setEscrowStatus(EscrowStatus.REFUNDED); o.setReturnResponsibleParty("BUYER"); orders.saveAndFlush(o);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->reputation.recordClosedOrder(o,staff.getId(),"Buyer changed mind",false));
        assertEquals(0,reputation.get(seller.getId()).sellerReturnFaultOrders());
        assertEquals(0,reputation.get(seller.getId()).nonFulfillmentOrders());
        assertEquals(1,reputation.get(seller.getId()).lateHandoverOrders());
    }

    private String uploadEvidence(User owner) throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","evidence.png","image/png",new byte[]{1,2,3});
        var result=mvc.perform(multipart("/api/v1/reports/evidence").file(file).header("Authorization",bearer(owner)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.storageId").doesNotExist())
            .andExpect(jsonPath("$.data.signedUrl").doesNotExist()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("evidenceUrl").asText();
    }
    private Order order(User seller,User buyer,boolean completed) {
        var o=new Order(); o.setPost(listing(seller)); o.setSeller(seller); o.setBuyer(buyer);
        o.setFinalPrice(new java.math.BigDecimal("1000000"));
        o.setStatus(completed?OrderStatus.COMPLETED:OrderStatus.PROCESSING);
        o.setEscrowStatus(completed?EscrowStatus.RELEASED:EscrowStatus.HELD);
        return orders.saveAndFlush(o);
    }
    private void recordGood(Order order) {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->reputation.recordCompletedSale(order));
    }
    private long eventCount(User seller) { return jdbc.queryForObject("SELECT COUNT(*) FROM seller_reputation_events WHERE seller_id=?",Long.class,seller.getId()); }
    private org.springframework.test.web.servlet.ResultActions submit(User actor,Map<String,Object> body) throws Exception {
        return mvc.perform(post("/api/v1/reports").header("Authorization",bearer(actor)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    private UUID report(User buyer,UUID postId,UUID orderId,String code) throws Exception {
        var body=new HashMap<String,Object>(); body.put("requestId",UUID.randomUUID()); body.put("reasonCode",code);
        body.put("description","Evidence of the reported issue"); if (postId!=null) body.put("postId",postId); else body.put("orderId",orderId);
        var result=submit(buyer,body).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText());
    }
    private void start(User staff,UUID id) throws Exception {
        mvc.perform(post("/api/v1/staff/reports/"+id+"/start-review").header("Authorization",bearer(staff))).andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions resolve(User staff,UUID id,String decision,String party) throws Exception {
        var body=new HashMap<String,Object>(); body.put("decision",decision); body.put("reason","Staff checked evidence"); if (party!=null) body.put("responsibleParty",party);
        return mvc.perform(post("/api/v1/staff/reports/"+id+"/resolve").header("Authorization",bearer(staff))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    private User user(String role) {
        var u = new User("reputation-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        u.addRole(roles.findByCodeWithPermissions(role).orElseThrow()); return users.saveAndFlush(u);
    }
    private Post listing(User seller) {
        var p = new Post(); p.setUser(seller); p.setTitle("Reported product"); p.setStatus("ACTIVE");
        return posts.saveAndFlush(p);
    }
    private String bearer(User u) { return "Bearer " + jwt.generateAccessToken(u); }
}
