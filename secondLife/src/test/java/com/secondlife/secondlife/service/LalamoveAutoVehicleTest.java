package com.secondlife.secondlife.service;

import com.secondlife.secondlife.integration.shipping.LalamoveShippingProvider;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LalamoveAutoVehicleTest {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String BASE="https://rest.sandbox.lalamove.com";
    private final Clock clock=mock(Clock.class);
    private final Instant now=Instant.now();
    private final RestClient.Builder builder=RestClient.builder();
    private final LalamoveShippingProvider provider=new LalamoveShippingProvider(builder,JSON,properties(),clock);
    private final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();

    @org.junit.jupiter.api.BeforeEach void setClock() { when(clock.instant()).thenReturn(now); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"100000,220,120,100", "1000000,500,210,205", "5000000,600,220,220"})
    void quotesHeavyAndLargeParcelsUsingTheActualVehicleCapacity(int weight,int length,int width,int height) {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","50","40","50","cm"),
                vehicle("TRUCK_5000","5","t","6","2.2","2.2","m")),List.of());
        quotation("TRUCK_5000","250000");
        var payload=assertDoesNotThrow(()->route(weight,length,width,height));
        var result=provider.preview(payload);
        assertEquals("TRUCK_5000",result.path("provider_quote").path("serviceType").asText());
        assertEquals(250000,result.path("total_fee").intValue());
        server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"5000001,600,220,220", "5000000,601,220,220",
            "5000000,600,221,220", "5000000,600,220,221"})
    void parcelExceedingAnyVehicleCapacityReturnsUnsupportedInTheHttpResponse(int weight,int length,int width,int height) throws Exception {
        catalogue(List.of(vehicle("TRUCK_5000","5","t","6","2.2","2.2","m")),List.of());
        assertUnsupportedResponse(assertDoesNotThrow(()->route(weight,length,width,height)),
                "Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng. "
                +"Giới hạn từng loại xe tại điểm lấy hàng (dài x rộng x cao): TRUCK_5000: tối đa 5000 kg, 600 x 220 x 220 cm");
        server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ERR_OUT_OF_SERVICE_AREA","ERR_INVALID_SERVICE_TYPE"})
    void unavailableHeavyVehicleRouteReturnsUnsupportedInTheHttpResponse(String code) throws Exception {
        catalogue(List.of(vehicle("TRUCK_5000","5","t","6","2.2","2.2","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"errors\":[{\"id\":\""+code+"\"}]}"));
        assertUnsupportedResponse(assertDoesNotThrow(()->route(1000000,500,210,205)),
                "Lalamove chưa hỗ trợ tuyến giao hàng này với các loại xe phù hợp");
        server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,20,15,10", "600,-1,15,10", "600,20,0,10", "600,20,15,-1"})
    void invalidMeasurementsNeverReachTheCarrier(int weight,int length,int width,int height) {
        assertThrows(BadRequestException.class,()->provider.preview(route(weight,length,width,height)));
        server.verify();
    }
    private void assertUnsupportedResponse(Map<String,Object> payload,String message) throws Exception {
        var quotes=mock(ShippingQuoteService.class);
        when(quotes.quote(any(),any())).thenAnswer(invocation -> { provider.preview(payload); return null; });
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.secondlife.secondlife.controller.ShippingController(mock(ShippingCatalogService.class),quotes,
                        mock(com.secondlife.secondlife.security.CurrentUserProvider.class),mock(ReverseGeocodingService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/shipping/quotes")
                .contentType("application/json").content("""
                {"postId":"1c2dd5e3-36db-420b-b313-9ed606b017a9",
                 "deliveryAddress":{"name":"Buyer","phone":"0987654321","latitude":10.781,"longitude":106.700}}
                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value(message))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path").value("/api/v1/shipping/quotes"));
    }
    @Test void unsupportedParcelResponseListsVerifiedLocalVehicleLimitsOnly() throws Exception {
        var unverified=vehicle("UNKNOWN","10","unknown","1","1","1","m");
        catalogue(List.of(vehicle("MOTORCYCLE","30000","g","500","400","500","mm"),unverified,
                vehicle("VAN","0.5","t","1.7","1.2","1.2","m")),
                List.of(vehicle("FOREIGN_TRUCK","10","t","10","3","3","m")));
        assertUnsupportedResponse(route(10000,180,130,130),
                "Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng. "
                +"Giới hạn từng loại xe tại điểm lấy hàng (dài x rộng x cao): "
                +"MOTORCYCLE: tối đa 30 kg, 50 x 40 x 50 cm; VAN: tối đa 500 kg, 170 x 120 x 120 cm");
        server.verify();
    }
    @Test void quotesOnlyTheSmallestFittingVehicleRegardlessOfCatalogueOrder() {
        catalogue(List.of(vehicle("VAN", "0.5", "t", "2", "1", "1", "m"),
                vehicle("MOTORCYCLE", "30000", "g", "0.5", "0.5", "0.5", "m")),List.of());
        quotation("MOTORCYCLE","30000");
        var result=assertDoesNotThrow(()->provider.preview(route(10000,40,40,40)));
        assertEquals("MOTORCYCLE",result.path("provider_quote").path("serviceType").asText());
        assertEquals(30000,result.path("total_fee").intValue()); server.verify();
    }
    @Test void duplicateAutomaticPreviewsReuseTheQuotationWithoutAnyMoreCarrierRequests() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        var first=provider.preview(route(10000,40,40,40));
        var duplicate=provider.preview(route(10000,40,40,40));
        assertEquals(first,duplicate);
        assertNotSame(first,duplicate);
        server.verify();
    }
    @Test void rateLimitStopsImmediatelyWithoutTryingLargerVehicles() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m"),
            vehicle("VAN","100","kg","2","2","2","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
            .body("{\"message\":\"ERR_RATE_LIMIT_EXCEEDED\"}"));
        var error=assertThrows(LalamoveRequestException.class,()->provider.preview(route(10000,40,40,40)));
        assertEquals(429,error.getUpstreamStatus());
        assertEquals("ERR_RATE_LIMIT_EXCEEDED",error.getErrorCode());
        server.verify();
    }
    @Test void excludesOverloadedOversizedAndForeignCityVehicles() {
        catalogue(List.of(vehicle("TOO_LIGHT","5000","g","200","200","200","cm"),
                vehicle("TOO_SMALL","100","kg","20","20","20","cm"),
                vehicle("VAN","1","t","2000","1000","1000","mm")),
                List.of(vehicle("FOREIGN_CHEAP","100","kg","3","3","3","m")));
        quotation("VAN","50000");
        assertEquals("VAN",provider.preview(route(10000,100,60,60)).path("provider_quote").path("serviceType").asText());
        server.verify();
    }
    @Test void lengthAndWidthMaySwapButPackageMustRemainUpright() {
        catalogue(List.of(vehicle("WRONG_HEIGHT","100","kg","100","100","20","cm"),
                vehicle("VAN","100","kg","60","100","80","cm")),List.of());
        quotation("VAN","50000");
        assertEquals("VAN",provider.preview(route(10000,100,60,80)).path("provider_quote").path("serviceType").asText()); server.verify();
    }
    @Test void rejectsNoFittingVehicleAndNeverFallsBackToDefaultMotorcycle() {
        catalogue(List.of(vehicle("MOTORCYCLE","5","kg","40","40","40","cm")),List.of());
        assertThrows(BadRequestException.class,()->provider.preview(route(10000,100,60,80))); server.verify();
    }
    @Test void missingOrInvalidParcelFailsBeforeCallingLalamove() {
        var missing=route(10000,40,40,40);missing.remove("weight");
        assertThrows(BadRequestException.class,()->provider.preview(missing));
        var invalid=route(10000,40,40,40);invalid.put("height",0);
        assertThrows(BadRequestException.class,()->provider.preview(invalid)); server.verify();
    }
    @Test void unverifiedCapacityAndUnknownUnitsCannotBeSelected() {
        var unknown=vehicle("UNKNOWN","10","unknown","1","1","1","m");
        var missing=new HashMap<>(vehicle("MISSING","100","kg","1","1","1","m"));missing.remove("dimensions");
        catalogue(List.of(unknown,missing),List.of());
        assertThrows(BadRequestException.class,()->provider.preview(route(10000,40,40,40))); server.verify();
    }
    @Test void unavailableVehicleIsSkippedButAuthenticationFailureIsNotHidden() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m"),vehicle("VAN","100","kg","2","2","2","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .body("{\"errors\":[{\"id\":\"ERR_OUT_OF_SERVICE_AREA\"}]}"));
        quotation("VAN","50000");assertEquals(50000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());server.verify();
    }
    @Test void authenticationFailureIsNotHiddenByTryingAnotherVehicle() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m"),vehicle("VAN","100","kg","2","2","2","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(ShippingProviderException.class,()->provider.preview(route(10000,40,40,40)));server.verify();
    }
    @Test void allOutOfServiceQuotesReturnAnExplicitUnsupportedRoute() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .body("{\"errors\":[{\"id\":\"ERR_OUT_OF_SERVICE_AREA\"}]}"));
        assertThrows(BadRequestException.class,()->provider.preview(route(10000,40,40,40)));server.verify();
    }
    @Test void rejectsQuotationForADifferentVehicle() {
        catalogue(List.of(vehicle("VAN","100","kg","1","1","1","m")),List.of());
        quotation("VAN","MOTORCYCLE","30000");
        assertThrows(ShippingProviderException.class,()->provider.preview(route(10000,40,40,40)));server.verify();
    }
    @Test void refreshRejectsMismatchedVehicleInsteadOfDowngradingPaidShipment() {
        var route=route(10000,40,40,40);route.put("provider_quote",JSON.valueToTree(Map.of("serviceType","VAN")));
        quotation("VAN","MOTORCYCLE","12000");
        assertThrows(ShippingProviderException.class,()->provider.preview(route));server.verify();
    }
    @Test void refreshUsesPaidVehicleWithoutSelectingAnotherVehicle() {
        var route=route(10000,40,40,40);route.put("provider_quote",JSON.valueToTree(Map.of("serviceType","VAN")));
        quotation("VAN","45000");
        assertEquals("VAN",provider.preview(route).path("provider_quote").path("serviceType").asText());server.verify();
    }
    @Test void cacheExpiresAfterThirtySeconds() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","31000");
        assertEquals(30000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());
        when(clock.instant()).thenReturn(now.plusSeconds(29));
        assertEquals(30000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        assertEquals(31000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());
        server.verify();
    }
    @Test void cacheNeverOutlivesTheProviderQuotation() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","MOTORCYCLE","30000",now.plusSeconds(5));
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","31000");
        provider.preview(route(10000,40,40,40));
        when(clock.instant()).thenReturn(now.plusSeconds(5));
        assertEquals(31000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());
        server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"from_latitude","to_longitude","weight","length","width","height"})
    void changedRouteOrParcelCannotReuseCachedQuotation(String field) {
        for(int i=0;i<2;i++) {
            catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
            quotation("MOTORCYCLE",Integer.toString(30000+i*1000));
        }
        provider.preview(route(10000,40,40,40));
        var changed=route(10000,40,40,40);
        changed.put(field,switch(field) {
            case "from_latitude" -> new BigDecimal("10.778");
            case "to_longitude" -> new BigDecimal("106.701");
            case "weight" -> 10001;
            default -> 41;
        });
        assertEquals(31000,provider.preview(changed).path("total_fee").intValue());
        server.verify();
    }
    @Test void cachedResultsCannotBeModifiedByCallers() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        var first=provider.preview(route(10000,40,40,40));
        ((tools.jackson.databind.node.ObjectNode)first.path("provider_quote")).put("serviceType","VAN");
        assertEquals("MOTORCYCLE",provider.preview(route(10000,40,40,40)).path("provider_quote").path("serviceType").asText());
        server.verify();
    }
    @Test void failedRequestsAreNotCached() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        server.expect(requestTo(BASE+"/v3/quotations")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        assertThrows(LalamoveRequestException.class,()->provider.preview(route(10000,40,40,40)));
        assertEquals(30000,provider.preview(route(10000,40,40,40)).path("total_fee").intValue());
        server.verify();
    }
    @Test void bookingAndPaidVehicleRefreshAlwaysRequestFreshQuotations() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","31000");
        quotation("MOTORCYCLE","32000");
        var preview=provider.preview(route(10000,40,40,40));
        var booking=route(10000,40,40,40);booking.put("client_order_code","SL-inspection");
        assertEquals(31000,provider.preview(booking).path("total_fee").intValue());
        var refresh=route(10000,40,40,40);refresh.put("provider_quote",preview.path("provider_quote"));
        assertEquals(32000,provider.preview(refresh).path("total_fee").intValue());
        server.verify();
    }
    @Test void simultaneousDuplicatePreviewsMakeOnlyOneCarrierQuotation() throws Exception {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m")),List.of());
        quotation("MOTORCYCLE","30000");
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<Future<JsonNode>>();
            for(int i=0;i<4;i++) calls.add(executor.submit(()->{
                start.await();return provider.preview(route(10000,40,40,40));
            }));
            start.countDown();
            for(var call:calls) assertEquals(30000,call.get(5,TimeUnit.SECONDS).path("total_fee").intValue());
        }
        server.verify();
    }
    private void catalogue(List<Map<String,Object>> local,List<Map<String,Object>> foreign) {
        server.expect(requestTo(BASE+"/v3/cities")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
            envelope(List.of(Map.of("locode","VN SGN","name","Ho Chi Minh & Nearby Regions","services",local),
                Map.of("locode","VN HAN","name","Hanoi","services",foreign))),MediaType.APPLICATION_JSON));
    }
    private void quotation(String key,String fee) { quotation(key,key,fee); }
    private void quotation(String key,String returnedKey,String fee) {
        quotation(key,returnedKey,fee,now.plusSeconds(300));
    }
    private void quotation(String key,String returnedKey,String fee,Instant expiry) {
        server.expect(requestTo(BASE+"/v3/quotations")).andExpect(method(HttpMethod.POST))
            .andExpect(request->assertEquals(key,JSON.readTree(((MockClientHttpRequest)request).getBodyAsString()).path("data").path("serviceType").asText()))
            .andRespond(withSuccess(envelope(Map.of("quotationId","quote-"+key,"serviceType",returnedKey,"expiresAt",expiry.toString(),
                "priceBreakdown",Map.of("total",fee,"currency","VND"),"stops",List.of(Map.of("stopId","from"),Map.of("stopId","to")))),MediaType.APPLICATION_JSON));
    }
    private static Map<String,Object> vehicle(String key,String weight,String weightUnit,String l,String w,String h,String unit) {
        return Map.of("key",key,"load",Map.of("value",weight,"unit",weightUnit),"dimensions",Map.of(
            "length",Map.of("value",l,"unit",unit),"width",Map.of("value",w,"unit",unit),"height",Map.of("value",h,"unit",unit)));
    }
    private static String envelope(Object data) { return JSON.writeValueAsString(Map.of("data",data)); }
    private static Map<String,Object> route(int weight,int l,int w,int h) {
        var from=new ShippingAddress("Seller","0912345678","Pickup",new BigDecimal("10.777"),new BigDecimal("106.695"));
        var to=new ShippingAddress("Buyer","0987654321","Delivery",new BigDecimal("10.781"),new BigDecimal("106.700"));
        return ShippingPayloads.create(from,to,new ShippingParcel(weight,l,w,h),"Appliance",new BigDecimal("1000000"));
    }
    private static LalamoveProperties properties() {
        var p=new LalamoveProperties();p.setEnabled(true);p.setBaseUrl(BASE);p.setKey("pk_test_test");p.setSecret("sk_test_test");
        p.setMarket("VN");p.setLanguage("vi_VN");p.setTimeoutMs(5000);p.setMaxResponseCharacters(1000000);return p;
    }
}
