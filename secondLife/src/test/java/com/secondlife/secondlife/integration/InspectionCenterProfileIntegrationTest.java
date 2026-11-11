package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.integration.shipping.ShippingProviderRouter;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingPickupAddressRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import com.secondlife.secondlife.repository.InspectionShippingQuoteRepository;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.ReverseGeocodingService;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "spring.jpa.open-in-view=false"
})
class InspectionCenterProfileIntegrationTest {
    private static final String ROOT = "/api/v1/inspector-center/profile";
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
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired InspectionOrderRepository inspections;
    @Autowired ShippingPickupAddressRepository pickups;
    @Autowired ShipmentRepository shipments;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager entityManager;
    @Autowired InspectionCenterProfileService profiles;
    @Autowired InspectionShippingQuoteService inspectionShipping;
    @Autowired InspectionShippingQuoteRepository inspectionQuotes;
    @MockitoBean ShippingProviderRouter carrier;
    @MockitoBean ReverseGeocodingService geocoding;
    @MockitoBean NotificationService notifications;

    private User center;
    private User otherCenter;
    private User seller;
    private User staff;

    @BeforeEach
    void fixture() {
        center = actor("INSPECTION_CENTER");
        otherCenter = actor("INSPECTION_CENTER");
        seller = actor("SELLER");
        staff = actor("STAFF");
    }

    @Test
    void seededCenterWithOnlyNameCanReadIncompleteProfile() throws Exception {
        mvc.perform(get(ROOT).header("Authorization", auth(center)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(center.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Inspection center"))
                .andExpect(jsonPath("$.data.shippingAddressConfigured").value(false));
        assertNull(center.getProfile().getLatitude());
    }

    @Test
    void updatePersistsContactsCoordinatesAndPreservesAccountAndPersonalFields() throws Exception {
        center.getProfile().setAvatarUrl("https://example.test/avatar.png");
        String email = center.getEmail();
        String password = center.getPasswordHash();
        UUID centerId = center.getId();
        UUID otherId = otherCenter.getId();
        Map<String, Object> body = profileBody();
        body.put("id", otherId);
        body.put("name", "  Trung tâm Quận 7  ");

        update(center, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trung tâm Quận 7"))
                .andExpect(jsonPath("$.data.address").value("1059 Nguyễn Văn Linh, Tân Phong, Quận 7, Thành phố Hồ Chí Minh"))
                .andExpect(jsonPath("$.data.latitude").value(10.73))
                .andExpect(jsonPath("$.data.longitude").value(106.705))
                .andExpect(jsonPath("$.data.shippingAddressConfigured").value(true));
        entityManager.clear();
        User reloaded = users.findById(centerId).orElseThrow();
        assertEquals(email, reloaded.getEmail());
        assertEquals(password, reloaded.getPasswordHash());
        assertEquals("https://example.test/avatar.png", reloaded.getProfile().getAvatarUrl());
        assertEquals("02838229999", reloaded.getProfile().getPhone());
        assertEquals(10.73, reloaded.getProfile().getLatitude());
        assertNotNull(reloaded.getProfile().getUpdatedAt());
        assertNull(users.findById(otherId).orElseThrow().getProfile().getLatitude());
        mvc.perform(get(ROOT).header("Authorization", auth(reloaded)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Trung tâm Quận 7"));
        verifyNoInteractions(carrier, geocoding);
    }

    @Test
    void districtIsOptionalAndAnEditReplacesCoordinates() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        Map<String, Object> changed = profileBody();
        changed.put("district", null);
        changed.put("latitude", 10.731);
        changed.put("longitude", 106.706);
        changed.put("phone", "+842838229999");
        update(center, changed).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(10.731))
                .andExpect(jsonPath("$.data.longitude").value(106.706))
                .andExpect(jsonPath("$.data.address").value("1059 Nguyễn Văn Linh, Tân Phong, Thành phố Hồ Chí Minh"));
    }

    @Test
    void profileCanBeCreatedForAccountWithoutProfile() throws Exception {
        User missing = new User(UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        missing.addRole(roles.findByCode("INSPECTION_CENTER").orElseThrow());
        missing = users.saveAndFlush(missing);
        mvc.perform(get(ROOT).header("Authorization", auth(missing)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shippingAddressConfigured").value(false));
        update(missing, profileBody()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingAddressConfigured").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "phone", "province", "ward", "streetAddress", "latitude", "longitude"})
    void missingRequiredFieldIsRejected(String field) throws Exception {
        Map<String, Object> body = profileBody();
        body.put(field, null);
        update(center, body).andExpect(status().isBadRequest());
        assertNull(center.getProfile().getLatitude());
    }

    @ParameterizedTest
    @CsvSource({"latitude,91", "longitude,181", "phone,abc", "name, ''", "province,Hà Nội"})
    void invalidValuesOrProvinceGpsMismatchAreRejected(String field, String value) throws Exception {
        Map<String, Object> body = profileBody();
        body.put(field, value);
        update(center, body).andExpect(status().isBadRequest());
        verifyNoInteractions(carrier, geocoding);
    }

    @Test
    void coordinatesOutsideSupportedServiceAreaAreRejected() throws Exception {
        Map<String, Object> body = profileBody();
        body.put("latitude", 0);
        body.put("longitude", 0);
        update(center, body).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "INSPECTOR", "STAFF", "ADMIN"})
    void otherRolesCannotAccessCenterSelfProfile(String role) throws Exception {
        User outsider = actor(role);
        mvc.perform(get(ROOT).header("Authorization", auth(outsider))).andExpect(status().isForbidden());
        update(outsider, profileBody()).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(put(ROOT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profileBody())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void availableCentersExposeAddressesWithoutNPlusOneQueriesOrPrivateAccountFields() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        UUID configuredId = center.getId();
        UUID emptyId = otherCenter.getId();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            entityManager.flush();
            entityManager.clear();
            statistics.clear();
            var options = profiles.getAvailableCenters();
            assertEquals(1, statistics.getPrepareStatementCount());
            assertTrue(options.stream().filter(value -> value.id().equals(configuredId)).findFirst().orElseThrow().shippingAddressConfigured());
            assertFalse(options.stream().filter(value -> value.id().equals(emptyId)).findFirst().orElseThrow().shippingAddressConfigured());
            JsonNode list = json.readTree(mvc.perform(get("/api/v1/inspection/centers").header("Authorization", auth(seller)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
            for (JsonNode option : list) {
                assertTrue(option.has("id") && option.has("name") && option.has("shippingAddressConfigured"));
                assertFalse(option.has("email") || option.has("passwordHash"));
            }
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void carrierQuotationAndBookingUseSavedCenterGpsAndRetryKeepsOldRoute() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        mockCarrier();
        UUID requestId = UUID.randomUUID();
        Map<String, Object> request = shipmentBody(requestId);
        JsonNode first = json.readTree(book(order, seller, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotedFee").value(27000))
                .andReturn().getResponse().getContentAsString()).path("data");
        UUID shipmentId = UUID.fromString(first.path("shipmentId").asText());
        String savedPayload = shipments.findById(shipmentId).orElseThrow().getPayload();

        Map<String, Object> changed = profileBody();
        changed.put("latitude", 10.731);
        changed.put("phone", "0912345678");
        update(center, changed).andExpect(status().isOk());
        book(order, staff, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipmentId").value(shipmentId.toString()));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.captor();
        verify(carrier, times(1)).preview(payload.capture());
        verify(carrier, times(1)).create(anyMap());
        JsonNode sent = json.valueToTree(payload.getValue());
        assertEquals(0, new BigDecimal("10.73").compareTo(sent.path("to_latitude").decimalValue()));
        assertEquals(0, new BigDecimal("106.705").compareTo(sent.path("to_longitude").decimalValue()));
        assertEquals("02838229999", sent.path("to_phone").asText());
        assertEquals("Trung tâm Quận 7", sent.path("to_name").asText());
        assertEquals(0, new BigDecimal("10.77").compareTo(sent.path("from_latitude").decimalValue()));
        assertEquals(savedPayload, shipments.findById(shipmentId).orElseThrow().getPayload());
        verifyNoInteractions(geocoding);
    }

    @Test
    void cannotOverrideAssignedCenterWithAnArbitraryDestination() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        Map<String, Object> request = shipmentBody(UUID.randomUUID());
        request.put("toAddress", Map.of("name", "Other center", "phone", "0912345678", "latitude", 21.03, "longitude", 105.85));
        book(inspection(center), staff, request).andExpect(status().isConflict());
        verifyNoInteractions(carrier, geocoding);
    }

    @Test
    void unconfiguredCenterDoesNotFallBackToFakeOrClientSuppliedCoordinates() throws Exception {
        Map<String, Object> request = shipmentBody(UUID.randomUUID());
        request.put("toAddress", Map.of("name", "Other center", "phone", "0912345678", "latitude", 21.03, "longitude", 105.85));
        book(inspection(center), staff, request).andExpect(status().isConflict());
        verifyNoInteractions(carrier, geocoding);
    }

    @Test
    void legacyInspectionStillRequiresExplicitStaffVerifiedDestination() throws Exception {
        book(inspection(null), staff, shipmentBody(UUID.randomUUID())).andExpect(status().isBadRequest());
        verifyNoInteractions(carrier);
    }

    @Test
    void legacyExplicitDestinationIsStillAccepted() throws Exception {
        InspectionOrder order = inspection(null);
        Map<String, Object> request = shipmentBody(UUID.randomUUID());
        request.put("toAddress", Map.of("name", "Legacy center", "phone", "0912345678", "latitude", 10.73, "longitude", 106.705));
        when(geocoding.shipping(any())).thenReturn(new ShippingAddress("Legacy center", "0912345678", "Verified address",
                new BigDecimal("10.73"), new BigDecimal("106.705")));
        mockCarrier();
        book(order, staff, request).andExpect(status().isOk());
        verify(geocoding).shipping(any());
        verify(carrier).create(anyMap());
    }

    @Test
    void homeCheckInPersistsOnceAndKeepsCodeOutOfOrderDetails() throws Exception {
        InspectionOrder order = inspection(null);
        order.setOrderType(InspectionOrderType.AT_HOME);
        order.setStatus("SCHEDULED");
        order.setAppointmentScheduledAt(Instant.now().plusSeconds(300));
        User inspector = assignInspector(order, center);
        String path = "/api/v1/inspection/orders/" + order.getId();
        String start = "/api/v1/inspector/orders/" + order.getId() + "/start";

        var issued = mvc.perform(post(path + "/check-in-code").header("Authorization", auth(seller)))
                .andExpect(status().isOk()).andReturn();
        UUID code = UUID.fromString(json.readTree(issued.getResponse().getContentAsString())
                .path("data").path("code").asText());
        entityManager.flush();
        entityManager.clear();
        assertEquals(code, inspections.findById(order.getId()).orElseThrow().getHomeCheckInCode());
        mvc.perform(get(path).header("Authorization", auth(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeCheckInCode").doesNotExist())
                .andExpect(jsonPath("$.customerStatus").value("SCHEDULED"));
        mvc.perform(post(start).header("Authorization", auth(inspector)))
                .andExpect(status().isBadRequest());

        var scan = json.writeValueAsString(Map.of("checkInCode", code));
        mvc.perform(post(start).header("Authorization", auth(inspector))
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTING"))
                .andExpect(jsonPath("$.homeCheckedInAt").isNotEmpty());
        entityManager.flush();
        entityManager.clear();
        var checkedIn = inspections.findById(order.getId()).orElseThrow();
        assertNotNull(checkedIn.getHomeCheckedInAt());
        assertNull(checkedIn.getHomeCheckInCode());
    }

    @Test
    void centerResultPersistsPhotosWithoutNewSeal() throws Exception {
        InspectionOrder order = inspection(center);
        order.setInspectionScope(InspectionScope.PRE_LISTING);
        order.setStatus("INSPECTING");
        User inspector = assignInspector(order, center);
        String photo = "https://res.cloudinary.com/test-cloud/image/upload/v123/secondlife/inspections/proofs/"
                + "781527ed-cfd8-4573-bb08-ad981683c50e.jpg";
        var body = Map.of("status", "FAILED", "note", "Không đạt", "tamperSealId", "unused",
                "evidencePhotoUrls", List.of(photo));

        mvc.perform(post("/api/v1/inspector/orders/" + order.getId() + "/result")
                        .header("Authorization", auth(inspector))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidencePhotoUrls[0]").value(photo))
                .andExpect(jsonPath("$.tamperSealId").doesNotExist());
        entityManager.flush();
        entityManager.clear();
        assertEquals(List.of(photo), inspections.findById(order.getId()).orElseThrow().getEvidencePhotoUrls());
    }

    @Test
    void assignedCenterCanReadSellerToCenterShipment() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        mockCarrier();
        book(order, seller, shipmentBody(UUID.randomUUID())).andExpect(status().isOk());

        readShipments(order, center).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].leg").value("SELLER_TO_CENTER"))
                .andExpect(jsonPath("$.data[0].providerStatus").value("ASSIGNING_DRIVER"));
    }

    @Test
    void explicitCenterTakesPrecedenceOverAssignedInspectorsManager() throws Exception {
        InspectionOrder order = inspection(otherCenter);
        assignInspector(order, center);

        readShipments(order, center).andExpect(status().isForbidden());
        readShipments(order, otherCenter).andExpect(status().isOk());
    }

    @Test
    void legacyInspectionShipmentIsReadableByAssignedInspectorsCenter() throws Exception {
        InspectionOrder order = inspection(null);
        assignInspector(order, center);
        Map<String, Object> request = shipmentBody(UUID.randomUUID());
        request.put("toAddress", Map.of("name", "Legacy center", "phone", "0912345678",
                "latitude", 10.73, "longitude", 106.705));
        when(geocoding.shipping(any())).thenReturn(new ShippingAddress("Legacy center", "0912345678", "Verified address",
                new BigDecimal("10.73"), new BigDecimal("106.705")));
        mockCarrier();
        book(order, staff, request).andExpect(status().isOk());

        readShipments(order, center).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].leg").value("SELLER_TO_CENTER"))
                .andExpect(jsonPath("$.data[0].providerStatus").value("ASSIGNING_DRIVER"));
        readShipments(order, otherCenter).andExpect(status().isForbidden());
    }

    @Test
    void legacyInspectionWithoutAnIdentifiedCenterIsForbiddenToCenter() throws Exception {
        InspectionOrder order = inspection(null);
        readShipments(order, center).andExpect(status().isForbidden());

        assignInspector(order, null);
        readShipments(order, center).andExpect(status().isForbidden());
        readShipments(order, staff).andExpect(status().isOk());
        readShipments(order, seller).andExpect(status().isOk());
    }

    @Test
    void centerProfileUpdateDoesNotGrantShipmentBookingPermissions() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        book(inspection(center), center, shipmentBody(UUID.randomUUID())).andExpect(status().isForbidden());
        verify(carrier, never()).create(anyMap());
    }

    @Test
    void quotesUseActualOutboundAndEstimatedReturnWithoutBooking() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        liveQuotes();
        var quote = inspectionShipping.quote(order.getPost().getId(), center.getId(), null);
        assertEquals(0, new BigDecimal("27000").compareTo(quote.outbound().shippingFee()));
        assertEquals(0, new BigDecimal("32400").compareTo(quote.returnReserve().shippingFee()));
        assertEquals("SELLER_TO_CENTER", quote.outbound().leg());
        assertEquals("CENTER_TO_SELLER", quote.returnReserve().leg());
        assertNull(quote.returnReserve().providerQuotationId());
        assertTrue(quote.expiresAt().isAfter(Instant.now()));
        ArgumentCaptor<Map<String, Object>> payloads = ArgumentCaptor.captor();
        verify(carrier, times(1)).preview(payloads.capture());
        assertEquals(new BigDecimal("10.77"), payloads.getAllValues().getFirst().get("from_latitude"));
        assertEquals(new BigDecimal("10.73"), payloads.getAllValues().getFirst().get("to_latitude"));
        assertEquals(1700, payloads.getValue().get("weight"));
        verify(carrier, never()).create(anyMap());
        var row = inspectionQuotes.findById(quote.id()).orElseThrow();
        assertEquals(order.getPost().getId(), row.getPostId());
        var reused = inspectionShipping.quote(order.getPost().getId(), center.getId(), quote.id());
        assertEquals(quote.id(), reused.id());
        verify(carrier, times(1)).preview(anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "parcel", "center", "price", "pickup"})
    void changedOrExpiredQuotesAreRejectedWithoutAnotherCarrierRequest(String change) throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        liveQuotes();
        var quote = inspectionShipping.quote(order.getPost().getId(), center.getId(), null);
        if (change.equals("expired")) inspectionQuotes.findById(quote.id()).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1));
        if (change.equals("parcel")) order.getPost().setShippingWeight(3000);
        if (change.equals("price")) order.getPost().setPrice(new BigDecimal("600000"));
        if (change.equals("center")) center.getProfile().setLatitude(10.731);
        if (change.equals("pickup")) pickups.findById(seller.getId()).orElseThrow().setAddressJson(json.writeValueAsString(
                new ShippingAddress("Seller", "0912345678", "Changed pickup", new BigDecimal("10.771"), new BigDecimal("106.69"))));
        entityManager.flush();
        assertThrows(ConflictException.class, () -> inspectionShipping.quote(order.getPost().getId(), center.getId(), quote.id()));
        verify(carrier, times(1)).preview(anyMap());
    }

    @Test
    void anotherCentersQuoteCannotBeUsedForThisRoute() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        update(otherCenter, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        liveQuotes();
        var quote = inspectionShipping.quote(order.getPost().getId(), center.getId(), null);
        assertThrows(ConflictException.class, () -> inspectionShipping.quote(order.getPost().getId(), otherCenter.getId(), quote.id()));
        verify(carrier, times(1)).preview(anyMap());
    }

    @Test
    void invalidCarrierFeeDoesNotBecomeAZeroOrDefaultShippingFee() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.preview(anyMap())).thenReturn(json.valueToTree(Map.of("total_fee", -1,
                "expires_at", Instant.now().plusSeconds(600).toString(),
                "provider_quote", Map.of("serviceType", "MOTORCYCLE", "quotationId", "invalid"))));
        assertThrows(ShippingProviderException.class, () -> inspectionShipping.quote(order.getPost().getId(), center.getId(), null));
        verify(carrier, times(1)).preview(anyMap());
        verify(carrier, never()).create(anyMap());
    }

    @Test
    void rateLimitIsPropagatedWithoutFallbackOrAnotherQuoteCall() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.preview(anyMap())).thenThrow(new ShippingProviderException("Rate limit", 429));
        var error = assertThrows(ShippingProviderException.class,
                () -> inspectionShipping.quote(order.getPost().getId(), center.getId(), null));
        assertEquals(429, error.getUpstreamStatus());
        verify(carrier, times(1)).preview(anyMap());
    }

    @Test
    void paidShippingBudgetCapsFreshBookingAndDoesNotChargeMore() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        liveQuotes();
        var quote = inspectionShipping.quote(order.getPost().getId(), center.getId(), null);
        order.setShippingFee(quote.outbound().shippingFee());
        order.setReturnShippingReserveFee(quote.returnReserve().shippingFee());
        inspectionShipping.bind(order, quote);
        inspections.saveAndFlush(order);
        assertEquals(quote.id(), order.getInspectionShippingQuoteId());
        when(carrier.forProvider("LALAMOVE")).thenReturn(carrier);
        when(carrier.preview(anyMap())).thenReturn(json.valueToTree(Map.of("total_fee", 28000,
                "provider_quote", Map.of("serviceType", "MOTORCYCLE", "quotationId", "new-price"))));
        book(order, staff, shipmentBody(UUID.randomUUID())).andExpect(status().isConflict());
        verify(carrier, never()).create(anyMap());
    }

    @Test
    void buyerMustObtainAQuoteBeforeCreatingCenterInspection() throws Exception {
        User buyer = actor("BUYER");
        mvc.perform(post("/api/v1/inspection/requests").header("Authorization", auth(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "postId", UUID.randomUUID(), "orderType", "AT_CENTER", "feePayer", "BUYER_PAYS", "buyerConsentZin", true))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        "inspectionShippingQuoteId là bắt buộc; lấy báo giá Lalamove kiểm định trước khi tạo yêu cầu AT_CENTER"));
        verifyNoInteractions(carrier);
    }

    @Test
    void shippingQuoteCannotBeBoundToTwoDifferentInspectionOrders() throws Exception {
        update(center, profileBody()).andExpect(status().isOk());
        InspectionOrder order = inspection(center);
        liveQuotes();
        var quote = inspectionShipping.quote(order.getPost().getId(), center.getId(), null);
        order.setShippingFee(quote.outbound().shippingFee());
        order.setReturnShippingReserveFee(quote.returnReserve().shippingFee());
        inspectionShipping.bind(order, quote);
        inspections.saveAndFlush(order);
        InspectionOrder another = new InspectionOrder();
        another.setPost(order.getPost());
        another.setInspectionCenter(center);
        another.setShippingFee(order.getShippingFee());
        another.setReturnShippingReserveFee(order.getReturnShippingReserveFee());
        assertThrows(ConflictException.class, () -> inspectionShipping.bind(another, quote));
        verify(carrier, times(1)).preview(anyMap());
    }

    private void liveQuotes() {
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.preview(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(0);
            int fee = "Seller".equals(payload.get("from_name")) ? 27000 : 39000;
            return json.valueToTree(Map.of("total_fee", fee, "expires_at", Instant.now().plusSeconds(600).toString(),
                    "provider_quote", Map.of("quotationId", UUID.randomUUID().toString(), "serviceType", "MOTORCYCLE")));
        });
    }

    private ResultActions update(User user, Map<String, Object> body) throws Exception {
        return mvc.perform(put(ROOT).header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions book(InspectionOrder inspection, User user, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/inspection-orders/" + inspection.getId() + "/shipments")
                .header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions readShipments(InspectionOrder inspection, User user) throws Exception {
        return mvc.perform(get("/api/v1/inspection-orders/" + inspection.getId() + "/shipments")
                .header("Authorization", auth(user)));
    }

    private User assignInspector(InspectionOrder order, User manager) {
        User inspector = actor("INSPECTOR");
        inspector.setManager(manager);
        users.saveAndFlush(inspector);
        order.setInspector(inspector);
        inspections.saveAndFlush(order);
        return inspector;
    }

    private Map<String, Object> profileBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Trung tâm Quận 7");
        body.put("phone", "02838229999");
        body.put("province", "Thành phố Hồ Chí Minh");
        body.put("district", "Quận 7");
        body.put("ward", "Tân Phong");
        body.put("streetAddress", "1059 Nguyễn Văn Linh");
        body.put("latitude", new BigDecimal("10.7300"));
        body.put("longitude", new BigDecimal("106.7050"));
        return body;
    }

    private Map<String, Object> shipmentBody(UUID requestId) {
        return new LinkedHashMap<>(Map.of("requestId", requestId, "leg", "SELLER_TO_CENTER"));
    }

    private User actor(String role) {
        User user = new User(UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        user.addRole(roles.findByCode(role).orElseThrow());
        user.setProfile(new UserProfile(user, "Inspection center", null, null));
        return users.saveAndFlush(user);
    }

    private InspectionOrder inspection(User selectedCenter) {
        Category category = new Category();
        category.setName("Appliance " + UUID.randomUUID());
        category = categories.saveAndFlush(category);
        Post listing = new Post();
        listing.setTitle("Rice cooker");
        listing.setCategoryId(category.getId());
        listing.setUser(seller);
        listing.setPrice(new BigDecimal("500000"));
        listing.setShippingWeight(1700);
        listing.setShippingLength(25);
        listing.setShippingWidth(20);
        listing.setShippingHeight(15);
        listing = posts.saveAndFlush(listing);
        ShippingPickupAddress pickup = new ShippingPickupAddress();
        pickup.setUserId(seller.getId());
        pickup.setAddressJson(json.writeValueAsString(new ShippingAddress("Seller", "0912345678", "Seller location",
                new BigDecimal("10.77"), new BigDecimal("106.69"))));
        pickups.saveAndFlush(pickup);
        InspectionOrder order = new InspectionOrder();
        order.setPost(listing);
        order.setSeller(seller);
        order.setInspectionCenter(selectedCenter);
        order.setOrderType(InspectionOrderType.AT_CENTER);
        order.setStatus("CONFIRMED_PENDING_DISPATCH");
        return inspections.saveAndFlush(order);
    }

    private void mockCarrier() {
        when(carrier.code()).thenReturn("LALAMOVE");
        when(carrier.forProvider("LALAMOVE")).thenReturn(carrier);
        when(carrier.preview(anyMap())).thenReturn(json.valueToTree(Map.of("total_fee", 27000,
                "provider_quote", Map.of("quotationId", "test-quote", "serviceType", "MOTORCYCLE",
                        "expiresAt", Instant.now().plusSeconds(300).toString()))));
        when(carrier.create(anyMap())).thenReturn(json.valueToTree(Map.of("order_code", UUID.randomUUID().toString(),
                "status", "ASSIGNING_DRIVER", "total_fee", 27000)));
    }

    private String auth(User user) { return "Bearer " + tokens.generateAccessToken(user); }
}
