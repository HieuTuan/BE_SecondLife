package com.secondlife.secondlife.service;
import com.secondlife.secondlife.config.*;
import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.service.shipping.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ReverseGeocodingServiceTest {
    private static final String URL="https://api.geoapify.com/v1/geocode/reverse";
    private final ObjectMapper mapper=new ObjectMapper();
    private record Module(ReverseGeocodingService service,MockRestServiceServer server,GeocodingProperties properties) {}
    private Module module() {
        var properties=new GeocodingProperties(); properties.setEnabled(true); properties.setApiKey("private-test-key");
        properties.setBaseUrl(URL); properties.setLanguage("vi"); properties.setTimeoutMs(5000); properties.setMaxResponseCharacters(1048576);
        var cities=new ShippingCatalogProperties();cities.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI,ShippingCatalogProperties.City.DANANG,ShippingCatalogProperties.City.HO_CHI_MINH));
        var builder=RestClient.builder();var service=new ReverseGeocodingService(properties,new ShippingCatalogPolicy(cities,mapper),mapper,builder);
        return new Module(service,MockRestServiceServer.bindTo(builder).build(),properties);
    }
    private BigDecimal lat() { return new BigDecimal("21.0333"); }
    private BigDecimal lng() { return new BigDecimal("105.7937"); }
    private String response(String city,String country,String address,String type) {
        return mapper.writeValueAsString(Map.of("results",List.of(Map.of("formatted",address,"place_id","place-1",
                "result_type",type,"country_code",country.toLowerCase(Locale.ROOT),"state",city))));
    }
    private void reply(Module module,String body) {
        module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?"))).andExpect(method(HttpMethod.GET))
             .andExpect(queryParam("lat","21.0333")).andExpect(queryParam("lon","105.7937"))
            .andExpect(queryParam("lang","vi")).andExpect(queryParam("format","json"))
            .andExpect(queryParam("apiKey","private-test-key")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    @ParameterizedTest @ValueSource(strings={"Hà Nội","Hanoi","Thành phố Đà Nẵng","Da Nang","Hồ Chí Minh","Ho Chi Minh City"})
    void allowedCityIsResolvedUsingStructuredComponents(String city) {
        var module=module();reply(module,response(city,"VN","Địa chỉ do Geoapify xác định","street"));
        var result=module.service.reverse(lat(),lng());
        assertEquals("Địa chỉ do Geoapify xác định",result.address());assertEquals(lat(),result.latitude());assertEquals(lng(),result.longitude());
        assertNotNull(result.city());assertEquals("VN",result.countryCode());module.server.verify();
    }
    @Test void shippingAddressUsesOnlyProviderTextAndPreservesContactsAndInputPoint() {
        var module=module();reply(module,response("Hà Nội","VN","123 Cầu Giấy, Hà Nội, Việt Nam","building"));
        var result=module.service.shipping(new ShippingAddressRequest(" Seller "," 0912345678 ",lat(),lng()));
        assertEquals("123 Cầu Giấy, Hà Nội, Việt Nam",result.address());assertEquals("Seller",result.name());assertEquals("0912345678",result.phone());
        module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"Cần Thơ","Hải Phòng","Hà Nội giả"})
    void citiesOutsideServiceAreaAreRejected(String city) {
        var module=module();reply(module,response(city,"VN","Street, Hà Nội","street"));
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void foreignCountryCannotPassByUsingAnAllowedCityName() {
        var module=module();reply(module,response("Hà Nội","TH","Street","street"));
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void cityIsUsedOnlyWhenStateIsMissing() {
        var module=module();
        reply(module,"{\"results\":[{\"result_type\":\"building\",\"formatted\":\"123 Street, Hà Nội\",\"country_code\":\"vn\",\"city\":\"Hà Nội\"}]}");
        assertEquals(ShippingCatalogProperties.City.HANOI,module.service.reverse(lat(),lng()).city());module.server.verify();
    }
    @Test void allowedCityNameCannotOverrideAnOutsideState() {
        var module=module();
        reply(module,"{\"results\":[{\"result_type\":\"street\",\"formatted\":\"Street\",\"country_code\":\"vn\",\"state\":\"Đồng Nai\",\"city\":\"Ho Chi Minh City\"}]}");
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void amenityLocationCanSupplyAPhysicalPickupAddress() {
        var module=module();reply(module,response("Hà Nội","vn","Tòa nhà, Hà Nội","amenity"));
        assertEquals("Tòa nhà, Hà Nội",module.service.reverse(lat(),lng()).address());module.server.verify();
    }
    @Test void connectionFailureIsSanitizedAndClassifiedAsBadGateway() {
        var module=module();module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?")))
                .andRespond(withException(new java.io.IOException("private-test-key")));
        var error=assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));
        assertEquals(502,error.getUpstreamStatus());assertNull(error.getCause());assertFalse(error.getMessage().contains("private-test-key"));module.server.verify();
    }
    @Test void timeoutIsSanitizedAndClassifiedAsGatewayTimeout() {
        var module=module();module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?")))
                .andRespond(withException(new java.net.SocketTimeoutException("private-test-key")));
        var error=assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));
        assertEquals(504,error.getUpstreamStatus());assertNull(error.getCause());assertFalse(error.getMessage().contains("private-test-key"));module.server.verify();
    }
    @Test void administrativeAreaOnlyIsNotAcceptedAsPickupAddress() {
        var module=module();reply(module,response("Hà Nội","VN","Hà Nội, Việt Nam","city"));
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void noAddressIsABadRequest() {
        var module=module();reply(module,"{\"results\":[]}");
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @ParameterizedTest @ValueSource(ints={401,403,429,500})
    void providerErrorsDoNotExposeSecretOrProviderMessage(int status) {
        var module=module();module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?")))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("private-test-key").contentType(MediaType.APPLICATION_JSON));
        var error=assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));
        assertFalse(error.getMessage().contains("private-test-key"));assertNull(error.getCause());module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","invalid-json","{\"results\":{}}"})
    void malformedProviderDataIsNotStored(String body) {
        var module=module();reply(module,body);
        assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void responseSizeIsBounded() {
        var module=module();module.properties.setMaxResponseCharacters(10);reply(module,response("Hà Nội","VN","123 Street","street"));
        assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void missingConfigurationAndInvalidCoordinatesDoNotSendRequests() {
        var module=module();module.properties.setEnabled(false);
        assertEquals(503,assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng())).getUpstreamStatus());
        module.properties.setEnabled(true);module.properties.setApiKey("");
        assertEquals(503,assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng())).getUpstreamStatus());
        assertThrows(BadRequestException.class,()->module.service.reverse(new BigDecimal("90.1"),lng()));module.server.verify();
    }
    @Test void redirectsAreRejected() {
        var module=module();module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?")))
            .andRespond(withStatus(HttpStatus.FOUND).header("Location","https://untrusted.example"));
        assertThrows(ShippingProviderException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
}
