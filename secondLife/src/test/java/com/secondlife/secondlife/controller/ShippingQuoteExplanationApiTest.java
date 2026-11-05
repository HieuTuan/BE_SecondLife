package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.integration.shipping.LalamoveShippingProvider;
import com.secondlife.secondlife.mapper.ShippingVehicleSelectionMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingPickupAddressRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingCatalogService;
import com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real controller, service, carrier adapter and generated mapper with a fake carrier HTTP server. */
class ShippingQuoteExplanationApiTest {
    private static final String BASE = "https://rest.sandbox.lalamove.com";
    private final ObjectMapper json = new ObjectMapper();
    private final PostRepository posts = mock(PostRepository.class);
    private final ShippingPickupAddressRepository addresses = mock(ShippingPickupAddressRepository.class);
    private final ShippingQuoteRepository quotes = mock(ShippingQuoteRepository.class);
    private final InspectionOrderRepository inspections = mock(InspectionOrderRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ReverseGeocodingService geocoding = mock(ReverseGeocodingService.class);
    private final UUID buyerId = UUID.randomUUID();
    private final Post listing = new Post();
    private final ShippingAddress pickup = new ShippingAddress("Seller", "0912345678", "Pickup",
            new BigDecimal("21.0333"), new BigDecimal("105.7937"));
    private final ShippingAddressRequest delivery = new ShippingAddressRequest("Buyer", "0987654321",
            new BigDecimal("10.870004"), new BigDecimal("106.684601"));
    private MockRestServiceServer carrier;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var seller = new User();
        seller.setId(UUID.randomUUID());
        listing.setId(UUID.randomUUID());
        listing.setUser(seller);
        listing.setTitle("Packed item");
        listing.setStatus("ACTIVE");
        listing.setPrice(new BigDecimal("1400000"));
        listing.setShippingWeight(1700);
        listing.setShippingLength(2);
        listing.setShippingWidth(2);
        listing.setShippingHeight(4);
        when(posts.findById(listing.getId())).thenReturn(Optional.of(listing));
        var storedPickup = new ShippingPickupAddress();
        storedPickup.setAddressJson(json.writeValueAsString(pickup));
        when(addresses.findById(seller.getId())).thenReturn(Optional.of(storedPickup));
        when(geocoding.shipping(delivery)).thenReturn(new ShippingAddress(delivery.name(), delivery.phone(),
                "Delivery", delivery.latitude(), delivery.longitude()));
        when(quotes.save(any(ShippingQuote.class))).thenAnswer(call -> {
            ShippingQuote row = call.getArgument(0);
            row.setId(UUID.randomUUID());
            return row;
        });

        var properties = new LalamoveProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE);
        properties.setKey("pk_test_test");
        properties.setSecret("sk_test_test");
        properties.setMarket("VN");
        properties.setLanguage("vi_VN");
        properties.setTimeoutMs(5000);
        properties.setMaxResponseCharacters(1000000);
        var builder = RestClient.builder();
        var provider = new LalamoveShippingProvider(builder, json, properties);
        carrier = MockRestServiceServer.bindTo(builder).build();
        var selectionMapper = new ShippingVehicleSelectionMapper(
                Mappers.getMapper(ShippingVehicleSelectionMapper.Delegate.class));
        var service = new ShippingQuoteServiceImpl(posts, mock(NegotiationRepository.class), addresses, quotes,
                inspections, shipments, provider, json, geocoding, selectionMapper);
        var current = mock(CurrentUserProvider.class);
        when(current.resolveUserId(any())).thenReturn(buyerId);
        mvc = MockMvcBuilders.standaloneSetup(new ShippingController(mock(ShippingCatalogService.class),
                        service, current, geocoding))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @Test
    void firstSuitableVehicleKeepsExistingQuoteFieldsAndHasNoFallbackWarning() throws Exception {
        catalogue();
        quotation("MOTORCYCLE", "29000", "8300", "m");
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quoteId").isString())
                .andExpect(jsonPath("$.data.leg").value("SELLER_TO_BUYER"))
                .andExpect(jsonPath("$.data.shippingFee").value(29000))
                .andExpect(jsonPath("$.data.productPrice").value(1400000))
                .andExpect(jsonPath("$.data.totalPayable").value(1429000))
                .andExpect(jsonPath("$.data.shippingWeight").value(1700))
                .andExpect(jsonPath("$.data.shippingLength").value(2))
                .andExpect(jsonPath("$.data.shippingWidth").value(2))
                .andExpect(jsonPath("$.data.shippingHeight").value(4))
                .andExpect(jsonPath("$.data.vehicleSelection.preferredServiceType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.data.vehicleSelection.selectedServiceType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.data.vehicleSelection.fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.vehicleSelection.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.data.vehicleSelection.message").doesNotExist());
        carrier.verify();
    }

    @Test
    void alternativeQuoteIncludesRealRouteReasonDistanceAndOriginalCarrierPriceEvenFromCache() throws Exception {
        catalogue();
        reject("ERR_OUT_OF_SERVICE_AREA");
        quotation("LD_PICKUP_TRUCK", "6164800", "1498600", "m");
        for (int repeat = 0; repeat < 2; repeat++) {
            request().andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.serviceType").value("LD_PICKUP_TRUCK"))
                    .andExpect(jsonPath("$.data.shippingFee").value(6164800))
                    .andExpect(jsonPath("$.data.totalPayable").value(7564800))
                    .andExpect(jsonPath("$.data.vehicleSelection.preferredServiceType").value("MOTORCYCLE"))
                    .andExpect(jsonPath("$.data.vehicleSelection.selectedServiceType").value("LD_PICKUP_TRUCK"))
                    .andExpect(jsonPath("$.data.vehicleSelection.fallbackUsed").value(true))
                    .andExpect(jsonPath("$.data.vehicleSelection.reasonCode").value("PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA"))
                    .andExpect(jsonPath("$.data.vehicleSelection.providerErrorCode").value("ERR_OUT_OF_SERVICE_AREA"))
                    .andExpect(jsonPath("$.data.vehicleSelection.pickupCity").value("Thành phố Hà Nội"))
                    .andExpect(jsonPath("$.data.vehicleSelection.deliveryCity").value("Thành phố Hồ Chí Minh"))
                    .andExpect(jsonPath("$.data.vehicleSelection.distanceKm").value(1498.6))
                    .andExpect(jsonPath("$.data.vehicleSelection.message").value(containsString("Kiện hàng phù hợp với xe máy")))
                    .andExpect(jsonPath("$.data.vehicleSelection.message").value(containsString("xe bán tải thay thế")));
        }
        var rows = ArgumentCaptor.forClass(ShippingQuote.class);
        verify(quotes, times(2)).save(rows.capture());
        JsonNode saved = json.readTree(rows.getValue().getPayload());
        assertEquals("MOTORCYCLE", saved.path("vehicle_selection").path("preferredServiceType").asText());
        assertEquals("ERR_OUT_OF_SERVICE_AREA", saved.path("vehicle_selection").path("rejectedVehicles").get(0).path("providerErrorCode").asText());
        assertEquals("6164800", saved.path("provider_quote").path("priceBreakdown").path("total").asText());
        carrier.verify();
    }

    @Test
    void allSuitableVehiclesOutsideServiceAreaReturn422WithoutSavingAQuote() throws Exception {
        catalogue();
        reject("ERR_OUT_OF_SERVICE_AREA");
        reject("ERR_OUT_OF_SERVICE_AREA");
        request().andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NO_SUITABLE_VEHICLE_FOR_ROUTE"))
                .andExpect(jsonPath("$.message").value(containsString("từ Thành phố Hà Nội đến Thành phố Hồ Chí Minh")))
                .andExpect(jsonPath("$.path").value("/api/v1/shipping/quotes"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details.preferredServiceType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.details.attemptedServiceTypes[0]").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.details.attemptedServiceTypes[1]").value("LD_PICKUP_TRUCK"))
                .andExpect(jsonPath("$.details.pickupCity").value("Thành phố Hà Nội"))
                .andExpect(jsonPath("$.details.deliveryCity").value("Thành phố Hồ Chí Minh"))
                .andExpect(jsonPath("$.details.providerErrorCode").value("ERR_OUT_OF_SERVICE_AREA"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(quotes, never()).save(any());
        carrier.verify();
    }

    @ParameterizedTest
    @CsvSource({"401,502", "403,502", "429,429", "500,502", "503,503"})
    void technicalFailureAfterVehicleRejectionIsNotMisreportedAsAnUnsupportedRoute(int upstreamStatus, int expectedStatus) throws Exception {
        catalogue();
        reject("ERR_OUT_OF_SERVICE_AREA");
        carrier.expect(requestTo(BASE + "/v3/quotations"))
                .andRespond(withStatus(HttpStatus.valueOf(upstreamStatus)));
        request().andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(quotes, never()).save(any());
        carrier.verify();
    }

    @Test
    void timeoutStopsSelectionWithoutClaimingThatTheRouteIsUnsupported() throws Exception {
        catalogue();
        carrier.expect(requestTo(BASE + "/v3/quotations")).andRespond(request -> {
            throw new SocketTimeoutException("simulated carrier timeout");
        });
        request().andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(quotes, never()).save(any());
        carrier.verify();
    }

    @Test
    void unavailableServiceCanFallBackWithoutClaimingAServiceAreaRestriction() throws Exception {
        catalogue();
        reject("ERR_INVALID_SERVICE_TYPE");
        quotation("LD_PICKUP_TRUCK", "60000", null, null);
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleSelection.fallbackUsed").value(true))
                .andExpect(jsonPath("$.data.vehicleSelection.reasonCode").value("PREFERRED_VEHICLE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.vehicleSelection.message").value(containsString("không khả dụng")))
                .andExpect(jsonPath("$.data.vehicleSelection.message").value(not(containsString("không hỗ trợ loại xe này trên tuyến"))))
                .andExpect(jsonPath("$.data.vehicleSelection.distanceKm").doesNotExist());
        carrier.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ERR_INVALID_SERVICE_TYPE", "ERR_OUT_OF_SERVICE_AREA"})
    void anyUnresolvedInvalidServicePreventsAnUnsupportedRouteConclusion(String lastRejection) throws Exception {
        catalogue();
        reject("ERR_INVALID_SERVICE_TYPE");
        reject(lastRejection);
        request().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("ERR_INVALID_SERVICE_TYPE")))
                .andExpect(jsonPath("$.code").doesNotExist());
        verify(quotes, never()).save(any());
        carrier.verify();
    }

    @Test
    void largerParcelSelectsItsSmallestSuitableVehicleWithoutAFallbackWarning() throws Exception {
        listing.setShippingWeight(40000);
        catalogue();
        quotation("LD_PICKUP_TRUCK", "60000", "10000", "m");
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleSelection.preferredServiceType").value("LD_PICKUP_TRUCK"))
                .andExpect(jsonPath("$.data.vehicleSelection.fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.vehicleSelection.message").doesNotExist());
        carrier.verify();
    }

    @Test
    void explanationUsesTheInspectionCenterAsOriginInsteadOfTheSellerCity() throws Exception {
        var inspection = new InspectionOrder();
        inspection.setId(UUID.randomUUID());
        inspection.setStatus("PASSED");
        var centerShipment = new Shipment();
        centerShipment.setLeg("SELLER_TO_CENTER");
        centerShipment.setStatus("DELIVERED");
        centerShipment.setPayload(json.writeValueAsString(Map.of("to_name", "Center", "to_phone", "0912345678",
                "to_address", "Da Nang center", "to_latitude", "16.0678", "to_longitude", "108.2208")));
        when(inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(listing.getId(), "PASSED"))
                .thenReturn(Optional.of(inspection));
        when(shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspection.getId())).thenReturn(List.of(centerShipment));
        catalogue();
        reject("ERR_OUT_OF_SERVICE_AREA");
        quotation("LD_PICKUP_TRUCK", "1000000", "950", "km");
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leg").value("CENTER_TO_BUYER"))
                .andExpect(jsonPath("$.data.vehicleSelection.pickupCity").value("Thành phố Đà Nẵng"))
                .andExpect(jsonPath("$.data.vehicleSelection.deliveryCity").value("Thành phố Hồ Chí Minh"))
                .andExpect(jsonPath("$.data.vehicleSelection.distanceKm").value(950))
                .andExpect(jsonPath("$.data.vehicleSelection.message").value(not(containsString("Hà Nội"))));
        carrier.verify();
    }

    @ParameterizedTest
    @CsvSource({"-10,m", "abc,m", "100,mi"})
    void invalidOrUnknownCarrierDistanceIsNotInvented(String distance, String unit) throws Exception {
        catalogue();
        quotation("MOTORCYCLE", "29000", distance, unit);
        request().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleSelection.distanceKm").doesNotExist());
        carrier.verify();
    }

    private ResultActions request() throws Exception {
        return mvc.perform(post("/api/v1/shipping/quotes").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("postId", listing.getId(), "deliveryAddress", delivery))));
    }

    private void catalogue() {
        var services = List.of(vehicle("LD_PICKUP_TRUCK", "500"), vehicle("MOTORCYCLE", "30"));
        carrier.expect(requestTo(BASE + "/v3/cities")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json.writeValueAsString(Map.of("data", List.of(
                        Map.of("locode", "VN HAN", "services", services),
                        Map.of("locode", "VN DAD", "services", services)))), MediaType.APPLICATION_JSON));
    }

    private Map<String, Object> vehicle(String key, String maxKg) {
        return Map.of("key", key, "load", Map.of("value", maxKg, "unit", "kg"),
                "dimensions", Map.of("length", Map.of("value", "40", "unit", "cm"),
                        "width", Map.of("value", "40", "unit", "cm"),
                        "height", Map.of("value", "40", "unit", "cm")));
    }

    private void reject(String code) {
        carrier.expect(requestTo(BASE + "/v3/quotations")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body(json.writeValueAsString(Map.of("errors", List.of(Map.of("id", code))))));
    }

    private void quotation(String service, String fee, String distance, String unit) {
        var nativeQuote = json.createObjectNode().put("serviceType", service).put("quotationId", "quote-" + service)
                .put("expiresAt", Instant.now().plusSeconds(600).toString());
        nativeQuote.set("priceBreakdown", json.valueToTree(Map.of("total", fee, "currency", "VND")));
        nativeQuote.set("stops", json.valueToTree(List.of(Map.of("stopId", "from"), Map.of("stopId", "to"))));
        if (distance != null) nativeQuote.set("distance", json.valueToTree(Map.of("value", distance, "unit", unit)));
        carrier.expect(requestTo(BASE + "/v3/quotations")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertEquals(service, json.readTree(((MockClientHttpRequest) request).getBodyAsString())
                        .path("data").path("serviceType").asText()))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(json.writeValueAsString(Map.of("data", nativeQuote))));
    }
}
