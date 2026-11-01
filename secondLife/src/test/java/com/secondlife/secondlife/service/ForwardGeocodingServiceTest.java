package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.GeocodingProperties;
import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.integration.geocoding.GeoapifyGeocodingClient;
import com.secondlife.secondlife.service.impl.ReverseGeocodingServiceImpl;
import com.secondlife.secondlife.service.impl.ShippingCatalogPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ForwardGeocodingServiceTest {
    private static final String URL = "https://api.geoapify.com/v1/geocode/search";
    private MockRestServiceServer server;
    private ReverseGeocodingService service;

    @BeforeEach
    void setup() {
        var properties = new GeocodingProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-secret");
        properties.setTimeoutMs(1000);
        properties.setMaxResponseCharacters(4096);
        var cities = new ShippingCatalogProperties();
        cities.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI,
                ShippingCatalogProperties.City.DANANG, ShippingCatalogProperties.City.HO_CHI_MINH));
        var mapper = new ObjectMapper();
        var builder = RestClient.builder();
        service = new ReverseGeocodingServiceImpl(
                new GeoapifyGeocodingClient(properties, new ShippingCatalogPolicy(cities, mapper), mapper, builder));
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private void reply(String response) {
        server.expect(requestTo(startsWith(URL + "?"))).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @Test
    void usesVietnamFilterAndGeometryLongitudeLatitudeOrder() {
        server.expect(requestTo(startsWith(URL + "?")))
                .andExpect(request -> assertEquals("12 Cầu Giấy, Hà Nội",
                        URLDecoder.decode(UriComponentsBuilder.fromUri(request.getURI()).build()
                                .getQueryParams().getFirst("text"), StandardCharsets.UTF_8)))
                .andExpect(queryParam("filter", "countrycode:vn"))
                .andExpect(queryParam("limit", "1"))
                .andExpect(queryParam("lang", "vi"))
                .andRespond(withSuccess("""
                        {"features":[{"geometry":{"coordinates":[105.7937,21.0333]},
                        "properties":{"formatted":"12 Cầu Giấy, Hà Nội","place_id":"place-1","country_code":"vn"}}]}
                        """, MediaType.APPLICATION_JSON));
        var result = service.geocode(" 12 Cầu Giấy, Hà Nội ");
        assertEquals(new BigDecimal("21.0333"), result.latitude());
        assertEquals(new BigDecimal("105.7937"), result.longitude());
        assertEquals(ShippingCatalogProperties.City.HANOI, result.city());
        assertEquals("place-1", result.placeId());
        server.verify();
    }

    @Test
    void fallsBackToPropertiesAndInputTextWithoutTrustingProviderCityName() {
        reply("""
                {"features":[{"properties":{"lat":10.7769,"lon":106.7009,
                "formatted":" ","city":"Hà Nội","country_code":"vn"}}]}
                """);
        var result = service.geocode("Điểm nhận hàng");
        assertEquals("Điểm nhận hàng", result.address());
        assertEquals(ShippingCatalogProperties.City.HO_CHI_MINH, result.city());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"features\":[]}", "{\"features\":[{}]}",
            "{\"features\":[{\"properties\":{\"lat\":\"NaN\",\"lon\":105}}]}", "not-json",
            "{\"features\":[{\"properties\":{\"lat\":91,\"lon\":105}}]}",
            "{\"features\":[{\"properties\":{\"lat\":10.045,\"lon\":105.7469}}]}",
            "{\"features\":[{\"properties\":{\"lat\":21.0333,\"lon\":105.7937,\"country_code\":\"th\"}}]}"})
    void rejectsMissingMalformedOrUnsupportedLocations(String response) {
        reply(response);
        assertThrows(BadRequestException.class, () -> service.geocode("Địa chỉ thử nghiệm"));
        server.verify();
    }

    @Test
    void sanitizesProviderFailure() {
        server.expect(requestTo(startsWith(URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("test-secret").contentType(MediaType.APPLICATION_JSON));
        var error = assertThrows(BadRequestException.class, () -> service.geocode("Địa chỉ thử nghiệm"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void sanitizesConnectionFailure() {
        server.expect(requestTo(startsWith(URL))).andRespond(withException(new IOException("test-secret")));
        var error = assertThrows(BadRequestException.class, () -> service.geocode("Địa chỉ thử nghiệm"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void rejectsInvalidInputWithoutCallingProvider() {
        assertThrows(BadRequestException.class, () -> service.geocode(null));
        assertThrows(BadRequestException.class, () -> service.geocode("   "));
        assertThrows(BadRequestException.class, () -> service.geocode("a".repeat(501)));
        server.verify();
    }
}
