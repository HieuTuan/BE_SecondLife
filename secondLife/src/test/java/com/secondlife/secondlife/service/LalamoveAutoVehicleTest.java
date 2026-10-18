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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LalamoveAutoVehicleTest {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String BASE="https://rest.sandbox.lalamove.com";
    private final RestClient.Builder builder=RestClient.builder();
    private final LalamoveShippingProvider provider=new LalamoveShippingProvider(builder,JSON,properties());
    private final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();

    @Test void choosesCheapestFittingVehicleUsingCityCatalogueAndActualQuotes() {
        catalogue(List.of(vehicle("MOTORCYCLE", "30", "kg", "0.5", "0.5", "0.5", "m"),
                vehicle("VAN", "500", "kg", "2", "1", "1", "m")),List.of());
        quotation("MOTORCYCLE","30000"); quotation("VAN","25000");
        var result=assertDoesNotThrow(()->provider.preview(route(10000,40,40,40)));
        assertEquals("VAN",result.path("provider_quote").path("serviceType").asText());
        assertEquals(25000,result.path("total_fee").intValue()); server.verify();
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
    @Test void providerFailureDoesNotReturnAnUnverifiedCheapestPrice() {
        catalogue(List.of(vehicle("MOTORCYCLE","30","kg","1","1","1","m"),vehicle("VAN","100","kg","2","2","2","m")),List.of());
        quotation("MOTORCYCLE","30000");
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
    private void catalogue(List<Map<String,Object>> local,List<Map<String,Object>> foreign) {
        server.expect(requestTo(BASE+"/v3/cities")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
            envelope(List.of(Map.of("locode","VN SGN","name","Ho Chi Minh & Nearby Regions","services",local),
                Map.of("locode","VN HAN","name","Hanoi","services",foreign))),MediaType.APPLICATION_JSON));
    }
    private void quotation(String key,String fee) { quotation(key,key,fee); }
    private void quotation(String key,String returnedKey,String fee) {
        server.expect(requestTo(BASE+"/v3/quotations")).andExpect(method(HttpMethod.POST))
            .andExpect(request->assertEquals(key,JSON.readTree(((MockClientHttpRequest)request).getBodyAsString()).path("data").path("serviceType").asText()))
            .andRespond(withSuccess(envelope(Map.of("quotationId","quote-"+key,"serviceType",returnedKey,"expiresAt",Instant.now().plusSeconds(300).toString(),
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
