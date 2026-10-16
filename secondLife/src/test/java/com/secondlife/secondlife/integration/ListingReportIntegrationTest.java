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
class ListingReportIntegrationTest {
    private static final GhnTestDatabase database = new GhnTestDatabase("LISTING_REPORT_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("LISTING_REPORT_TEST_JDBC_URL"); }
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

    @MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockitoBean org.springframework.messaging.simp.SimpMessagingTemplate messages;
    @MockitoBean com.secondlife.secondlife.notification.channel.PushNotificationChannel pushes;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FRAUD","MISLEADING_DESCRIPTION","DUPLICATE_LISTING","OTHER"})
    void nonBuyerCanReportListingUsingOnlyTheFourSupportedReasons(String reason) throws Exception {
        var seller=user("SELLER"); var reporter=user("BUYER"); var post=listing(seller);
        listingReport(reporter,post.getId(),body(reason))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.postId").value(post.getId().toString()))
            .andExpect(jsonPath("$.data.orderId").doesNotExist()).andExpect(jsonPath("$.data.targetType").value("LISTING"))
            .andExpect(jsonPath("$.data.status").value("OPEN"));
        assertEquals(0,reputation.get(seller.getId()).evaluatedOrders());
        assertFalse(reputation.get(seller.getId()).sellingRestricted());
    }
    @Test void counterfeitIsRemovedFromListingReportsIncludingLegacyEndpoint() throws Exception {
        var reporter=user("BUYER"); var post=listing(user("SELLER"));
        listingReport(reporter,post.getId(),body("COUNTERFEIT")).andExpect(status().isBadRequest());
        var legacy=body("COUNTERFEIT"); legacy.put("postId",post.getId());
        submit(reporter,legacy).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM user_reports WHERE post_id=?",Integer.class,post.getId()));
    }
    @Test void duplicateReferenceIsStoredAndChangingItOnRetryIsRejected() throws Exception {
        var reporter=user("BUYER");var seller=user("SELLER");var post=listing(seller);var related=listing(seller);
        var body=body("DUPLICATE_LISTING");body.put("relatedPostId",related.getId());
        var result=listingReport(reporter,post.getId(),body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.relatedPostId").value(related.getId().toString())).andReturn();
        var id=mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
        listingReport(reporter,post.getId(),body).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        body.put("relatedPostId",listing(seller).getId());
        listingReport(reporter,post.getId(),body).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM user_reports WHERE post_id=?",Integer.class,post.getId()));
    }
    @Test void duplicateReferenceCannotBeSelfMissingOrPrivate() throws Exception {
        var reporter=user("BUYER");var seller=user("SELLER");var post=listing(seller);var body=body("DUPLICATE_LISTING");
        body.put("relatedPostId",post.getId());listingReport(reporter,post.getId(),body).andExpect(status().isBadRequest());
        body.put("relatedPostId",UUID.randomUUID());listingReport(reporter,post.getId(),body).andExpect(status().isNotFound());
        var draft=listing(seller);draft.setStatus("DRAFT");posts.saveAndFlush(draft);
        body.put("relatedPostId",draft.getId());listingReport(reporter,post.getId(),body).andExpect(status().isConflict());
        body.put("reasonCode","OTHER");body.put("relatedPostId",post.getId());
        listingReport(reporter,post.getId(),body).andExpect(status().isBadRequest());
    }
    @Test void duplicateListingReasonCannotBeUsedForAnOrder() throws Exception {
        var buyer=user("BUYER");var o=order(user("SELLER"),buyer,true);
        var request=body("DUPLICATE_LISTING");request.put("orderId",o.getId());submit(buyer,request).andExpect(status().isBadRequest());
    }
    @Test void ownUnpublishedAndUnauthenticatedReportsAreRejected() throws Exception {
        var seller=user("SELLER");var reporter=user("BUYER");var p=listing(seller);
        listingReport(seller,p.getId(),body("OTHER")).andExpect(status().isBadRequest());
        p.setStatus("DRAFT");posts.saveAndFlush(p);
        listingReport(reporter,p.getId(),body("OTHER")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/reports/listings/"+p.getId()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body("OTHER")))).andExpect(status().isUnauthorized());
    }
    @Test void createAndReviewNotificationsArePrivateAndIdempotent() throws Exception {
        var staff=user("STAFF");var other=user("BUYER");var seller=user("SELLER");var reporter=user("BUYER");var p=listing(seller);
        var request=body("OTHER");
        var result=listingReport(reporter,p.getId(),request).andExpect(status().isCreated()).andReturn();
        var id=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText());
        assertEquals(1,notificationCount(staff,id)); assertEquals(0,notificationCount(other,id));assertEquals(0,notificationCount(seller,id));
        listingReport(reporter,p.getId(),request).andExpect(status().isCreated());assertEquals(1,notificationCount(staff,id));
        start(staff,id);start(staff,id);assertEquals(1,notificationCount(reporter,id));
        resolve(staff,id,"CONFIRMED","SELLER").andExpect(status().isOk());
        resolve(staff,id,"CONFIRMED","SELLER").andExpect(status().isOk());
        assertEquals(2,notificationCount(reporter,id));
        var state=reputation.get(seller.getId());assertEquals(0,state.badTransactions());assertEquals(0,state.goodTransactions());
        assertEquals(1,state.confirmedIncidents());
        mvc.perform(get("/api/v1/reports/"+id).header("Authorization",bearer(other))).andExpect(status().isForbidden());
    }
    @Test void staffWithoutReviewPermissionAndInactiveStaffDoNotReceiveNotifications() throws Exception {
        var inactive=user("STAFF");inactive.setAccountStatus(AccountStatus.DISABLED);users.saveAndFlush(inactive);
        var limited=user("BUYER");
        var reporter=user("BUYER");var p=listing(user("SELLER"));
        var result=listingReport(reporter,p.getId(),body("FRAUD")).andExpect(status().isCreated()).andReturn();
        var id=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText());
        assertEquals(0,notificationCount(inactive,id));assertEquals(0,notificationCount(limited,id));
        mvc.perform(get("/api/v1/staff/reports?targetType=LISTING").with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF")))
            .andExpect(status().isForbidden());
    }
    @Test void listingQueueFilterDoesNotIncludeOrderDisputesAndIsPrivate() throws Exception {
        var staff=user("STAFF");var seller=user("SELLER");var reporter=user("BUYER");var p=listing(seller);
        listingReport(reporter,p.getId(),body("OTHER")).andExpect(status().isCreated());
        var orderReport=body("OTHER");orderReport.put("orderId",order(seller,reporter,true).getId());
        submit(reporter,orderReport).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/reports/me?targetType=LISTING&page=0&size=10").header("Authorization",bearer(reporter)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.items[0].targetType").value("LISTING"));
        mvc.perform(get("/api/v1/reports/me?targetType=ORDER&page=0&size=10").header("Authorization",bearer(reporter)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/staff/reports?targetType=LISTING&status=OPEN&page=0&size=100").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].orderId",org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/v1/reports/me?targetType=INVALID").header("Authorization",bearer(reporter)))
            .andExpect(status().isBadRequest());
    }
    @Test void reportDescriptionAndPendingDuplicateAreValidated() throws Exception {
        var reporter=user("BUYER");var p=listing(user("SELLER"));var req=body("OTHER");req.put("description","   ");
        listingReport(reporter,p.getId(),req).andExpect(status().isBadRequest());
        req.put("description","x".repeat(2001));listingReport(reporter,p.getId(),req).andExpect(status().isBadRequest());
        req.put("description","Evidence of a listing violation");listingReport(reporter,p.getId(),req).andExpect(status().isCreated());
        req.put("requestId",UUID.randomUUID());listingReport(reporter,p.getId(),req).andExpect(status().isConflict());
    }
    @Test void rejectedReportDoesNotAffectSellerAndOnlyAssignedReviewerCanResolve() throws Exception {
        var reporter=user("BUYER");var seller=user("SELLER");var staff=user("STAFF");var other=user("STAFF");
        var res=listingReport(reporter,listing(seller).getId(),body("FRAUD")).andExpect(status().isCreated()).andReturn();
        var id=UUID.fromString(mapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText());
        start(staff,id);resolve(other,id,"DISMISSED","NONE").andExpect(status().isForbidden());
        resolve(staff,id,"DISMISSED","NONE").andExpect(status().isOk());
        assertEquals(0,reputation.get(seller.getId()).confirmedIncidents());assertFalse(reputation.get(seller.getId()).sellingRestricted());
    }
    @Test void listingEvidenceStaysPrivateAndMustBelongToReporter() throws Exception {
        var reporter=user("BUYER");var stranger=user("BUYER");var staff=user("STAFF");var seller=user("SELLER");var p=listing(seller);
        var storageId="private-listing-evidence-"+UUID.randomUUID();
        org.mockito.Mockito.when(evidenceStorage.upload(org.mockito.ArgumentMatchers.any())).thenReturn(
            new ReportEvidenceStorage.StoredImage(storageId, "image/png",3));
        org.mockito.Mockito.when(evidenceStorage.read(storageId)).thenReturn(new byte[]{1,2,3});
        var uploaded=mvc.perform(multipart("/api/v1/reports/evidence").file(
            new org.springframework.mock.web.MockMultipartFile("file","evidence.png","image/png",new byte[]{1,2,3}))
            .header("Authorization",bearer(reporter))).andExpect(status().isCreated()).andReturn();
        var evidenceUrl=mapper.readTree(uploaded.getResponse().getContentAsString()).path("data").path("evidenceUrl").asText();
        var req=body("FRAUD");req.put("evidenceUrls",List.of(evidenceUrl));
        listingReport(stranger,p.getId(),req).andExpect(status().isForbidden());
        listingReport(reporter,p.getId(),req).andExpect(status().isCreated());
        mvc.perform(get(evidenceUrl).header("Authorization",bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get(evidenceUrl).header("Authorization",bearer(seller))).andExpect(status().isForbidden());
        mvc.perform(get(evidenceUrl).header("Authorization",bearer(staff))).andExpect(status().isOk());
        req=body("OTHER");req.put("evidenceUrls",List.of("https://res.cloudinary.com/public.jpg"));
        listingReport(reporter,p.getId(),req).andExpect(status().isBadRequest());
    }
    @Test void legacyCounterfeitListingHistoryRemainsReadableAfterMigration() throws Exception {
        var reporter=user("BUYER");var seller=user("SELLER");var p=listing(seller);var id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,reason_code,description,evidence_urls,status,created_at,updated_at)
            VALUES (?,?,?,?,?,'COUNTERFEIT','Historical report','[]'::jsonb,'OPEN',now(),now())
            """,id,reporter.getId(),UUID.randomUUID(),seller.getId(),p.getId());
        mvc.perform(get("/api/v1/reports/"+id).header("Authorization",bearer(reporter)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.reasonCode").value("COUNTERFEIT"))
            .andExpect(jsonPath("$.data.targetType").value("LISTING"));
    }
    @Test void notificationsAreAvailableThroughExistingNotificationApi() throws Exception {
        var staff=user("STAFF");var reporter=user("BUYER");var p=listing(user("SELLER"));
        var res=listingReport(reporter,p.getId(),body("OTHER")).andExpect(status().isCreated()).andReturn();
        var id=mapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        mvc.perform(get("/api/v1/notifications?type=REPORT&page=0&size=10").header("Authorization",bearer(staff)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].referenceId",org.hamcrest.Matchers.hasItem(id)))
            .andExpect(jsonPath("$.items[0].type").value("REPORT"));
    }
    @Test void listingNotificationsUseOnlyInAppChannel() throws Exception {
        var staff=user("STAFF");var reporter=user("BUYER");var p=listing(user("SELLER"));
        org.mockito.Mockito.clearInvocations(mail,pushes,messages);
        var res=listingReport(reporter,p.getId(),body("OTHER")).andExpect(status().isCreated()).andReturn();
        var id=UUID.fromString(mapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText());
        start(staff,id);resolve(staff,id,"DISMISSED","NONE").andExpect(status().isOk());
        assertEquals(1,notificationCount(staff,id));assertEquals(2,notificationCount(reporter,id));
        org.mockito.Mockito.verifyNoInteractions(mail,pushes);
        org.mockito.Mockito.verify(messages,org.mockito.Mockito.atLeastOnce()).convertAndSendToUser(
            org.mockito.ArgumentMatchers.eq(reporter.getId().toString()),org.mockito.ArgumentMatchers.eq("/queue/notifications"),
            org.mockito.ArgumentMatchers.any(Object.class));
    }
    private Map<String,Object> body(String reason) {
        var req=new HashMap<String,Object>();req.put("requestId",UUID.randomUUID());req.put("reasonCode",reason);
        req.put("description","Evidence of a listing violation");req.put("evidenceUrls",List.of());return req;
    }
    private org.springframework.test.web.servlet.ResultActions listingReport(User user,UUID postId,Map<String,Object> body) throws Exception {
        return mvc.perform(post("/api/v1/reports/listings/"+postId).header("Authorization",bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    private long notificationCount(User user,UUID reportId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND reference_id=? AND type='REPORT'",Long.class,user.getId(),reportId);
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
