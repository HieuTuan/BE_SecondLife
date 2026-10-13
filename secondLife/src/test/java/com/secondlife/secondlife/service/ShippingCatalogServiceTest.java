package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.service.shipping.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShippingCatalogServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ShippingProvider provider=mock(ShippingProvider.class);
    private ShippingCatalogService service;
    @BeforeEach void setup() {
        var properties=new ShippingCatalogProperties(); properties.setAllowedCities(List.of(
                ShippingCatalogProperties.City.HANOI,ShippingCatalogProperties.City.DANANG,ShippingCatalogProperties.City.HO_CHI_MINH));
        service=new ShippingCatalogService(provider,new ShippingCatalogPolicy(properties,mapper));
    }
    @Test void citiesKeepOnlyAllowedVietnamLocodesAndRetainServiceMetadata() {
        when(provider.cities()).thenReturn(mapper.readTree("""
                [{"locode":"VN HAN","name":"Hanoi & Nearby Regions (Sandbox)","services":[{"key":"VAN"}]},
                 {"locode":"VN DAD","name":"Da Nang"},{"locode":"VN SGN","name":"Ho Chi Minh City"},
                 {"locode":"VN VCA","name":"Can Tho"},{"locode":"TH BKK","name":"Hanoi"}]
                """));
        var result=service.cities(); assertEquals(3,result.size()); assertEquals("VAN",result.get(0).path("services").get(0).path("key").asText());
        assertEquals("VN DAD",result.get(1).path("locode").asText());
    }
    @Test void providerMissingDaNangDoesNotInventAThirdCity() {
        when(provider.cities()).thenReturn(mapper.readTree("[{\"locode\":\"VN HAN\"},{\"locode\":\"VN SGN\"}]"));
        assertEquals(2,service.cities().size());
    }
    @Test void lalamoveProvincesUseFilteredCityCatalogueWithoutGhnIds() {
        when(provider.code()).thenReturn("LALAMOVE");
        when(provider.cities()).thenReturn(mapper.readTree("[{\"locode\":\"VN HAN\",\"name\":\"Hanoi\"},{\"locode\":\"VN VCA\",\"name\":\"Can Tho\"}]"));
        assertEquals(1,service.provinces().size());
    }
    @Test void malformedUpstreamCatalogueIsAnErrorRatherThanAnUnrestrictedResult() {
        when(provider.cities()).thenReturn(mapper.readTree("{}"));
        assertThrows(ShippingProviderException.class,() -> service.cities());
    }
    @Test void configuredSubsetAlsoRestrictsCatalogues() {
        var properties=new ShippingCatalogProperties(); properties.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI));
        var restricted=new ShippingCatalogService(provider,new ShippingCatalogPolicy(properties,mapper));
        when(provider.cities()).thenReturn(mapper.readTree("[{\"locode\":\"VN HAN\"},{\"locode\":\"VN SGN\"}]"));
        assertEquals(1,restricted.cities().size());
    }
    @Test void legacyGeocodingProvinceNamesDoNotExpandCarrierCityCatalogue() {
        when(provider.cities()).thenReturn(mapper.readTree("""
            [{"locode":"VN SGN","name":"Ho Chi Minh City"}, {"name":"Bình Dương"},
             {"name":"Quảng Nam"}, {"locode":"VN XXX","name":"Bà Rịa - Vũng Tàu"}]
            """));
        assertEquals(1,service.cities().size());
    }
    @Test void administrativeAliasesRespectConfiguredAllowedCities() {
        var properties=new ShippingCatalogProperties(); properties.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI));
        var policy=new ShippingCatalogPolicy(properties,mapper);
        assertNull(policy.allowedAdministrativeCity("Bình Dương"));
        assertNull(policy.allowedAdministrativeCity("Bà Rịa - Vũng Tàu"));
        assertNull(policy.allowedAdministrativeCity("Quảng Nam"));
        assertEquals(ShippingCatalogProperties.City.HANOI,policy.allowedAdministrativeCity("Hà Nội"));
    }
}
