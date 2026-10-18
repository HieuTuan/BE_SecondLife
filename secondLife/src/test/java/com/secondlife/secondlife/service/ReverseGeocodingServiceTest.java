package com.secondlife.secondlife.service;

import com.secondlife.secondlife.integration.geocoding.GeoapifyGeocodingClient;
import com.secondlife.secondlife.config.*;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.service.impl.ShippingCatalogPolicy;
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
    private record Module(GeoapifyGeocodingClient service,MockRestServiceServer server,GeocodingProperties properties) {}
    private Module module() { return module(null); }
    private Module module(com.secondlife.secondlife.repository.GeocodedLocationCacheRepository cache) {
        var properties=new GeocodingProperties(); properties.setEnabled(true); properties.setApiKey("private-test-key");
        properties.setFallbackBaseUrl("https://nominatim.openstreetmap.org/reverse"); properties.setFallbackUserAgent("SecondLife-Test/1.0");
        properties.setBaseUrl(URL); properties.setLanguage("vi"); properties.setTimeoutMs(5000); properties.setMaxResponseCharacters(1048576);
        var cities=new ShippingCatalogProperties();cities.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI,ShippingCatalogProperties.City.DANANG,ShippingCatalogProperties.City.HO_CHI_MINH));
        var builder=RestClient.builder();var service=new GeoapifyGeocodingClient(properties,new ShippingCatalogPolicy(cities,mapper),mapper,builder,cache);
        return new Module(service,MockRestServiceServer.bindTo(builder).build(),properties);
    }
    private com.secondlife.secondlife.entity.GeocodedLocationCache cached(String address) {
        var row = new com.secondlife.secondlife.entity.GeocodedLocationCache(lat(), lng(), address,
                "HANOI", "VN", "cached-place");
        row.setId(UUID.randomUUID());
        return row;
    }
    @Test void currentCacheEntryAvoidsProviderCall() {
        var cache = org.mockito.Mockito.mock(com.secondlife.secondlife.repository.GeocodedLocationCacheRepository.class);
        var row = cached("12 Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam");
        org.mockito.Mockito.when(cache.findByLatKeyAndLonKey(lat(), lng())).thenReturn(Optional.of(row));
        var module = module(cache);
        var result = module.service.reverse(lat(), lng());
        assertEquals(row.getFormattedAddress(), result.address());
        assertEquals(ShippingCatalogProperties.City.HANOI, result.city());
        module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings = {
            "12 Cầu Giấy, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội, Việt Nam",
            "12 Cầu Giấy, Phường Sài Gòn, Thành phố Hà Nội, Việt Nam",
            "12 Cầu Giấy, Quận Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam"})
    void staleCacheIsRefreshedUsingCurrentWard(String oldAddress) {
        var cache = org.mockito.Mockito.mock(com.secondlife.secondlife.repository.GeocodedLocationCacheRepository.class);
        var row = cached(oldAddress);
        org.mockito.Mockito.when(cache.findByLatKeyAndLonKey(lat(), lng())).thenReturn(Optional.of(row));
        var module = module(cache);
        reply(module, response("Hà Nội", "VN", "12 Cầu Giấy, Hà Nội, Việt Nam", "building"));
        var result = module.service.reverse(lat(), lng());
        assertEquals("12 Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam", result.address());
        var saved = org.mockito.ArgumentCaptor.forClass(com.secondlife.secondlife.entity.GeocodedLocationCache.class);
        org.mockito.Mockito.verify(cache).save(saved.capture());
        assertEquals(row.getId(), saved.getValue().getId());
        assertEquals(result.address(), saved.getValue().getFormattedAddress());
        module.server.verify();
    }
    @Test void cachedForeignCountryDoesNotBypassValidation() {
        var cache = org.mockito.Mockito.mock(com.secondlife.secondlife.repository.GeocodedLocationCacheRepository.class);
        var row = cached("12 Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam"); row.setCountry("TH");
        org.mockito.Mockito.when(cache.findByLatKeyAndLonKey(lat(), lng())).thenReturn(Optional.of(row));
        var module = module(cache);
        reply(module, response("Hà Nội", "VN", "12 Cầu Giấy, Hà Nội", "building"));
        assertEquals("VN", module.service.reverse(lat(), lng()).countryCode());
        module.server.verify();
    }
    @Test void cachedCityCannotAuthorizeAnOutsideCoordinate() {
        var cache = org.mockito.Mockito.mock(com.secondlife.secondlife.repository.GeocodedLocationCacheRepository.class);
        var latitude = new BigDecimal("10.045"); var longitude = new BigDecimal("105.7469");
        org.mockito.Mockito.when(cache.findByLatKeyAndLonKey(latitude.setScale(4), longitude)).thenReturn(Optional.of(
                cached("12 Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam")));
        var module = module(cache);
        reply(module, response("Hà Nội", "VN", "12 Cầu Giấy, Hà Nội", "building"), latitude, longitude);
        assertThrows(BadRequestException.class, () -> module.service.reverse(latitude, longitude));
        module.server.verify();
    }
    private void primaryUnavailable(Module module) {
        module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL + "?")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }
    private void fallbackReply(Module module, String body) {
        module.server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://nominatim.openstreetmap.org/reverse?")))
                .andExpect(queryParam("lat", lat().toPlainString()))
                .andExpect(queryParam("lon", lng().toPlainString()))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    @Test void fallbackAlsoRemovesOldDistrictAndUsesCoordinateBoundary() {
        var module = module(); primaryUnavailable(module);
        fallbackReply(module, """
                {"display_name":"12 Xã Đàn, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội, Việt Nam",
                 "osm_id":123,"address":{"house_number":"12","road":"Xã Đàn","suburb":"Phường Dịch Vọng",
                 "county":"Quận Cầu Giấy","state":"Đồng Nai","country_code":"vn"}}
                """);
        var result = module.service.reverse(lat(), lng());
        assertEquals("12 Xã Đàn, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam", result.address());
        assertEquals("osm-123", result.placeId());
        assertEquals(ShippingCatalogProperties.City.HANOI, result.city());
        module.server.verify();
    }
    @Test void failedFallbackKeepsSanitizedPrimaryError() {
        var module = module(); primaryUnavailable(module);
        fallbackReply(module, "{\"error\":\"private-test-key\"}");
        var error = assertThrows(ShippingProviderException.class, () -> module.service.reverse(lat(), lng()));
        assertEquals(503, error.getUpstreamStatus()); assertNull(error.getCause());
        assertFalse(error.getMessage().contains("private-test-key"));
        module.server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "10.7769,106.7009,Hồ Chí Minh,Phường Bến Nghé,Quận 1,Phường Sài Gòn,Thành phố Hồ Chí Minh",
        "21.0333,105.7937,Hà Nội,Phường Dịch Vọng,Quận Cầu Giấy,Phường Cầu Giấy,Thành phố Hà Nội",
        "16.0544,108.2022,Đà Nẵng,Phường Hòa Thuận Tây,Quận Hải Châu,Phường Hoà Cường,Thành phố Đà Nẵng",
        "15.8801,108.338,Quảng Nam,Phường Minh An,Thành phố Hội An,Phường Hội An,Thành phố Đà Nẵng"
    })
    void resolvesCurrentWardByCoordinatesAndRemovesLegacyAdministrativeText(String lat,String lng,String state,
            String oldWard,String oldDistrict,String newWard,String newCity) {
        var module=module();var latitude=new BigDecimal(lat);var longitude=new BigDecimal(lng);
        String formatted="12 Đường thử nghiệm, "+oldWard+", "+oldDistrict+", "+state+", Việt Nam";
        var body=mapper.writeValueAsString(Map.of("results",List.of(Map.of("formatted",formatted,
            "result_type","building","country_code","vn","state",state,"suburb",oldWard,
            "county",oldDistrict,"housenumber","12","street","Đường thử nghiệm"))));
        reply(module,body,latitude,longitude);
        var result=module.service.reverse(latitude,longitude);
        assertEquals("12 Đường thử nghiệm, "+newWard+", "+newCity+", Việt Nam",result.address());
        assertEquals(latitude,result.latitude());assertEquals(longitude,result.longitude());module.server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "hcm,10.7769,106.7009,Phường Sài Gòn,Thành phố Hồ Chí Minh",
        "hanoi,21.0333,105.7937,Phường Cầu Giấy,Thành phố Hà Nội",
        "danang,16.0544,108.2022,Phường Hoà Cường,Thành phố Đà Nẵng"
    })
    void capturedLiveProviderResponsesProduceCurrentTwoLevelAddresses(String file,String lat,String lng,String ward,String city) throws Exception {
        var module=module();var latitude=new BigDecimal(lat);var longitude=new BigDecimal(lng);
        String captured=new org.springframework.core.io.ClassPathResource("geocoding/captured-"+file+".json")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        reply(module,captured,latitude,longitude);
        var address=module.service.reverse(latitude,longitude).address();
        assertTrue(address.endsWith(ward+", "+city+", Việt Nam"));
        assertFalse(address.contains("Thành phố Thủ Đức"));assertFalse(address.contains("HC 71006"));
        module.server.verify();
    }
    @Test void outsideCoordinateCannotBeAcceptedByClaimingAnAllowedCity() {
        var module=module();var latitude=new BigDecimal("10.045");var longitude=new BigDecimal("105.7469");
        reply(module,response("Hồ Chí Minh","VN","12 Street","building"),latitude,longitude);
        assertThrows(BadRequestException.class,()->module.service.reverse(latitude,longitude));module.server.verify();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"Xã Đàn","Huyện Thanh Quan"})
    void streetNamesThatResembleAdministrativePrefixesRemainPhysicalAddresses(String street) {
        var module=module();
        String body=mapper.writeValueAsString(Map.of("results",List.of(Map.of("formatted",street+", Hà Nội",
            "street",street,"result_type","street","country_code","vn","state","Hà Nội"))));
        reply(module,body);
        assertTrue(module.service.reverse(lat(),lng()).address().startsWith(street+", "));
        module.server.verify();
    }
    private BigDecimal lat() { return new BigDecimal("21.0333"); }
    private BigDecimal lng() { return new BigDecimal("105.7937"); }
    private String response(String city,String country,String address,String type) {
        return mapper.writeValueAsString(Map.of("results",List.of(Map.of("formatted",address,"place_id","place-1",
                "result_type",type,"country_code",country.toLowerCase(Locale.ROOT),"state",city))));
    }
    private void reply(Module module,String body) {
        reply(module,body,lat(),lng());
    }
    private void reply(Module module,String body,BigDecimal latitude,BigDecimal longitude) {
        module.server.expect(requestTo(org.hamcrest.Matchers.startsWith(URL+"?"))).andExpect(method(HttpMethod.GET))
             .andExpect(queryParam("lat",latitude.toPlainString())).andExpect(queryParam("lon",longitude.toPlainString()))
            .andExpect(queryParam("lang","vi")).andExpect(queryParam("format","json"))
            .andExpect(queryParam("apiKey","private-test-key")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    @ParameterizedTest @ValueSource(strings={"Hà Nội","Hanoi","Thành phố Đà Nẵng","Da Nang","Hồ Chí Minh","Ho Chi Minh City"})
    void allowedCityIsResolvedUsingStructuredComponents(String city) {
        var module=module();reply(module,response(city,"VN","Địa chỉ do Geoapify xác định","street"));
        var result=module.service.reverse(lat(),lng());
        assertEquals("Địa chỉ do Geoapify xác định, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam",result.address());assertEquals(lat(),result.latitude());assertEquals(lng(),result.longitude());
        assertNotNull(result.city());assertEquals("VN",result.countryCode());module.server.verify();
    }
    @Test void studentCulturalHousePointReportedUnderLegacyBinhDuongBelongsToHoChiMinh() throws Exception {
        var module=module();
        var latitude=new BigDecimal("10.87368");var longitude=new BigDecimal("106.80174");
        String captured=new org.springframework.core.io.ClassPathResource("geocoding/student-cultural-house.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        reply(module,captured,latitude,longitude);
        var result=module.service.reverse(latitude,longitude);
        assertEquals(ShippingCatalogProperties.City.HO_CHI_MINH,result.city());
        assertEquals(latitude,result.latitude());assertEquals(longitude,result.longitude());
        assertTrue(result.address().endsWith("Phường Đông Hoà, Thành phố Hồ Chí Minh, Việt Nam"));
        assertFalse(result.address().contains("Thành phố Dĩ An"));assertFalse(result.address().contains("Phường Linh Xuân"));
        module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"Bình Dương","Tỉnh Bình Dương","Binh Duong Province","Bà Rịa - Vũng Tàu","Ba Ria-Vung Tau Province"})
    void mergedHoChiMinhProvincesAreRecognizedFromLegacyGeocodingState(String state) {
        var module=module();var latitude=new BigDecimal("10.906");var longitude=new BigDecimal("106.769");
        reply(module,response(state,"VN","Địa chỉ do Geoapify xác định","street"),latitude,longitude);
        assertEquals(ShippingCatalogProperties.City.HO_CHI_MINH,module.service.reverse(latitude,longitude).city());module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"Quảng Nam","Tỉnh Quảng Nam","Quang Nam Province"})
    void mergedDaNangProvinceIsRecognizedFromLegacyGeocodingState(String state) {
        var module=module();var latitude=new BigDecimal("15.8801");var longitude=new BigDecimal("108.338");
        reply(module,response(state,"VN","Địa chỉ do Geoapify xác định","building"),latitude,longitude);
        assertEquals(ShippingCatalogProperties.City.DANANG,module.service.reverse(latitude,longitude).city());module.server.verify();
    }
    @Test void foreignCountryCannotPassByUsingALegacyMergedProvinceName() {
        var module=module();reply(module,response("Bình Dương","TH","Street","street"));
        assertThrows(BadRequestException.class,()->module.service.reverse(lat(),lng()));module.server.verify();
    }
    @Test void shippingAddressUsesOnlyProviderTextAndPreservesContactsAndInputPoint() {
        var module=module();reply(module,response("Hà Nội","VN","123 Cầu Giấy, Hà Nội, Việt Nam","building"));
        var result=module.service.shipping(new ShippingAddressRequest(" Seller "," 0912345678 ",lat(),lng()));
        assertEquals("123 Cầu Giấy, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam",result.address());assertEquals("Seller",result.name());assertEquals("0912345678",result.phone());
        module.server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"Cần Thơ","Hải Phòng","Hà Nội giả","Đồng Nai","Bình Phước","Long An","Bình Dương giả"})
    void citiesOutsideServiceAreaAreRejected(String city) {
        var module=module();var latitude=new BigDecimal("10.045");var longitude=new BigDecimal("105.7469");
        reply(module,response(city,"VN","Street, Hà Nội","street"),latitude,longitude);
        assertThrows(BadRequestException.class,()->module.service.reverse(latitude,longitude));module.server.verify();
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
    @Test void coordinateBoundaryIsAuthoritativeWhenProviderAdministrativeNamesAreStale() {
        var module=module();
        reply(module,"{\"results\":[{\"result_type\":\"street\",\"formatted\":\"Street\",\"country_code\":\"vn\",\"state\":\"Đồng Nai\",\"city\":\"Ho Chi Minh City\"}]}");
        assertEquals(ShippingCatalogProperties.City.HANOI,module.service.reverse(lat(),lng()).city());module.server.verify();
    }
    @Test void amenityLocationCanSupplyAPhysicalPickupAddress() {
        var module=module();reply(module,response("Hà Nội","vn","Tòa nhà, Hà Nội","amenity"));
        assertEquals("Tòa nhà, Phường Cầu Giấy, Thành phố Hà Nội, Việt Nam",module.service.reverse(lat(),lng()).address());module.server.verify();
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
