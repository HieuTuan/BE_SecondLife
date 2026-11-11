package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.InspectionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
})
class InspectionOrderListIntegrationTest {
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

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired InspectionService inspections;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider tokens;
    private UUID preListingId;
    private UUID buyerRequestId;
    private String authorization;
    private UUID centerId;

    @BeforeEach
    void fixture() {
        var manager = new User(UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        manager.addRole(roles.findByCode("INSPECTION_CENTER").orElseThrow());
        manager = users.saveAndFlush(manager);
        centerId = manager.getId();
        authorization = "Bearer " + tokens.generateAccessToken(manager);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        for (UUID user : new UUID[]{seller, buyer}) {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,'test-hash')", user, user + "@example.test");
        }
        jdbc.update("INSERT INTO user_profiles(id,user_id,full_name) VALUES (?,?,?)", UUID.randomUUID(), seller, "Center listing seller");
        preListingId = inspection(seller, null, "PRE_LISTING", "PENDING");
        buyerRequestId = inspection(seller, buyer, "BUYER_REQUEST", "WAITING_FEE_PAYMENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/inspector-center/orders", "/api/v1/inspector/orders/all"})
    void bothEndpointsReturn200ForPreListingAndUnassignedBuyerRequests(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", authorization).param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(preListingId.toString())))
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(buyerRequestId.toString())))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + preListingId + "')].buyerConsentZin").value(hasItem(false)))
                .andExpect(jsonPath("$.data.items[?(@.id=='" + preListingId + "')].sellerConsentZin").value(hasItem(false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/inspector-center/orders", "/api/v1/inspector/orders/all"})
    void filteringAndCountQueriesStillWork(String path) throws Exception {
        int total = jdbc.queryForObject("SELECT count(*) FROM inspection_orders WHERE status='PENDING' AND inspection_center_id=?", Integer.class, centerId);
        mvc.perform(get(path).header("Authorization", authorization).param("page", "0").param("size", "1").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(total))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));
    }

    @Test
    void serviceMapsImagesAndNullablePartiesInsideItsReadTransaction() {
        var page = inspections.getAllOrders(null, PageRequest.of(0, 100));
        var preListing = page.stream().filter(order -> order.id().equals(preListingId)).findFirst().orElseThrow();
        assertNull(preListing.buyerId());
        assertNull(preListing.inspectorId());
        assertNull(preListing.inspectorName());
        assertFalse(preListing.buyerConsentZin());
        assertFalse(preListing.sellerConsentZin());
        assertEquals("Center listing seller", preListing.sellerName());
        assertEquals(2, preListing.postImageUrls().size());
        var buyerRequest = page.stream().filter(order -> order.id().equals(buyerRequestId)).findFirst().orElseThrow();
        assertNull(buyerRequest.inspectorId());
        assertEquals(2, buyerRequest.postImageUrls().size());
    }

    @Test
    void detailAndShipmentsReturnTheBookedRouteIncludingLegacyBuyerDelivery() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id, post_id, buyer_id, seller_id, final_price, status, escrow_status,
                                   delivery_address, created_at, updated_at)
                SELECT ?, post_id, buyer_id, seller_id, 1000000, 'PROCESSING', 'HELD',
                       '{"name":"Buyer","phone":"0933333333","address":"Checkout address"}', now(), now()
                FROM inspection_orders WHERE id = ?
                """, purchaseId, buyerRequestId);
        jdbc.update("UPDATE inspection_orders SET order_id = ? WHERE id = ?", purchaseId, buyerRequestId);

        UUID inboundId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shipments(id, inspection_order_id, seller_id, buyer_id, leg, request_id, client_order_code,
                    order_code, status, provider_status, payload, actual_fee, share_link,
                    sender_name, sender_phone, sender_address, receiver_name, receiver_phone, receiver_address,
                    created_at, updated_at)
                SELECT ?, id, seller_id, buyer_id, 'SELLER_TO_CENTER', ?, ?, '3625397522753344417',
                    'DELIVERED', 'COMPLETED', '{}', 32000, 'https://example.test/track/inbound',
                    'Seller', '0911111111', 'Seller pickup', 'Hub Lab', '0922222222', 'Hub address',
                    now() - interval '1 hour', now()
                FROM inspection_orders WHERE id = ?
                """, inboundId, UUID.randomUUID(), "SL" + inboundId, buyerRequestId);
        jdbc.update("""
                INSERT INTO shipments(id, order_id, seller_id, buyer_id, leg, request_id, client_order_code,
                    status, payload, sender_name, sender_phone, sender_address, receiver_name, receiver_phone,
                    receiver_address, created_at, updated_at)
                SELECT ?, id, seller_id, buyer_id, 'CENTER_TO_BUYER', ?, ?, 'ASSIGNED', '{}',
                    'Hub Lab', '0922222222', 'Hub address', 'Buyer', '0933333333', 'Booked delivery', now(), now()
                FROM orders WHERE id = ?
                """, deliveryId, UUID.randomUUID(), "SL" + deliveryId, purchaseId);

        mvc.perform(get("/api/v1/inspection/orders/{id}", buyerRequestId).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerName").value("Seller"))
                .andExpect(jsonPath("$.sellerPhone").value("0911111111"))
                .andExpect(jsonPath("$.sellerAddress").value("Seller pickup"))
                .andExpect(jsonPath("$.buyerName").value("Buyer"))
                .andExpect(jsonPath("$.buyerPhone").value("0933333333"))
                .andExpect(jsonPath("$.buyerAddress").value("Booked delivery"))
                .andExpect(jsonPath("$.inspectionCenterId").value(centerId.toString()))
                .andExpect(jsonPath("$.inspectionCenterName").value("Hub Lab"))
                .andExpect(jsonPath("$.inspectionCenterPhone").value("0922222222"))
                .andExpect(jsonPath("$.inspectionCenterAddress").value("Hub address"));

        mvc.perform(get("/api/v1/inspection-orders/{id}/shipments", buyerRequestId)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(inboundId.toString()))
                .andExpect(jsonPath("$.data[0].shipmentId").value(inboundId.toString()))
                .andExpect(jsonPath("$.data[0].orderCode").value("3625397522753344417"))
                .andExpect(jsonPath("$.data[0].senderAddress").value("Seller pickup"))
                .andExpect(jsonPath("$.data[0].receiverAddress").value("Hub address"))
                .andExpect(jsonPath("$.data[0].actualFee").value(32000))
                .andExpect(jsonPath("$.data[0].providerStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].trackingUrl").value("https://example.test/track/inbound"))
                .andExpect(jsonPath("$.data[1].id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data[1].senderAddress").value("Hub address"))
                .andExpect(jsonPath("$.data[1].receiverAddress").value("Booked delivery"));
    }

    private UUID inspection(UUID seller, UUID buyer, String scope, String status) {
        UUID post = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        jdbc.update("INSERT INTO posts(id,user_id,title,status,price,created_at) VALUES (?,?,'Inspection listing','ACTIVE',1000000,CURRENT_TIMESTAMP)", post, seller);
        for (int position = 0; position < 2; position++) {
            jdbc.update("INSERT INTO post_images(post_id,image_position,image_url) VALUES (?,?,?)", post, position,
                    "https://example.test/listing/" + post + "/" + position);
        }
        jdbc.update("""
                INSERT INTO inspection_orders(id,post_id,seller_id,buyer_id,status,inspection_scope,inspection_center_id,inspection_fee,shipping_fee,created_at)
                VALUES (?,?,?,?,?,?,?,100000,50000,CURRENT_TIMESTAMP)
                """, order, post, seller, buyer, status, scope, centerId);
        return order;
    }
}
