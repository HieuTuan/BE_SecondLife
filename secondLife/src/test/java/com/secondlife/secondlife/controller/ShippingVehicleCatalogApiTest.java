package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.impl.ShippingCatalogPolicy;
import com.secondlife.secondlife.service.impl.ShippingCatalogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingVehicleCatalogApiTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ShippingProvider provider = mock(ShippingProvider.class);
    private final ShippingCatalogProperties properties = new ShippingCatalogProperties();
    private MockMvc mvc;

    @BeforeEach void setup() {
        properties.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI,
                ShippingCatalogProperties.City.DANANG, ShippingCatalogProperties.City.HO_CHI_MINH));
        var service = new ShippingCatalogServiceImpl(provider, new ShippingCatalogPolicy(properties, mapper));
        mvc = MockMvcBuilders.standaloneSetup(new ShippingController(service, mock(ShippingQuoteService.class),
                mock(CurrentUserProvider.class), mock(ReverseGeocodingService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(provider.cities()).thenReturn(mapper.readTree(CATALOGUE));
    }

    @Test void returnsApplicationMinimumsAndNormalizedCarrierMaximumsForAllAllowedCities() throws Exception {
        mvc.perform(get("/api/v1/shipping/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.provider").value("LALAMOVE"))
                .andExpect(jsonPath("$.data.minWeightSource").value("APPLICATION"))
                .andExpect(jsonPath("$.data.minDimensionCm").value(1))
                .andExpect(jsonPath("$.data.allowLengthWidthSwap").value(true))
                .andExpect(jsonPath("$.data.keepUpright").value(true))
                .andExpect(jsonPath("$.data.cities.length()").value(2))
                .andExpect(jsonPath("$.data.cities[0].locode").value("VN HAN"))
                .andExpect(jsonPath("$.data.cities[0].name").value("Hanoi"))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].serviceType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].minWeightGram").value(1))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].maxWeightGram").value(30000))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].maxLengthCm").value(50))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].maxWidthCm").value(40))
                .andExpect(jsonPath("$.data.cities[0].vehicles[0].maxHeightCm").value(50))
                .andExpect(jsonPath("$.data.cities[1].vehicles.length()").value(2))
                .andExpect(jsonPath("$.data.cities[1].vehicles[0].description").value("Van for large goods"))
                .andExpect(jsonPath("$.data.cities[1].vehicles[0].maxWeightGram").value(500000))
                .andExpect(jsonPath("$.data.cities[1].vehicles[0].maxLengthCm").value(170))
                .andExpect(jsonPath("$.data.cities[1].vehicles[0].maxWidthCm").value(120))
                .andExpect(jsonPath("$.data.cities[1].vehicles[0].maxHeightCm").value(120))
                .andExpect(jsonPath("$.data.cities[1].vehicles[1].serviceType").value("TRUCK_5000"))
                .andExpect(jsonPath("$.data.cities[1].vehicles[1].maxWeightGram").value(5000000))
                .andExpect(jsonPath("$.data.cities[1].vehicles[1].maxLengthCm").value(600))
                .andExpect(jsonPath("$.data.cities[1].vehicles[1].maxHeightCm").value(220));
    }

    @Test void pickupCoordinatesFilterTheCatalogueToTheSameCityAsAutomaticSelection() throws Exception {
        mvc.perform(get("/api/v1/shipping/vehicles").param("latitude", "10.777").param("longitude", "106.695"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities.length()").value(1))
                .andExpect(jsonPath("$.data.cities[0].locode").value("VN SGN"))
                .andExpect(jsonPath("$.data.cities[0].vehicles.length()").value(2));
    }

    @Test void configuredCitiesRestrictTheNormalizedCatalogue() throws Exception {
        properties.setAllowedCities(List.of(ShippingCatalogProperties.City.HANOI));
        mvc.perform(get("/api/v1/shipping/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities.length()").value(1))
                .andExpect(jsonPath("$.data.cities[0].locode").value("VN HAN"));
        mvc.perform(get("/api/v1/shipping/vehicles").param("latitude", "10.777").param("longitude", "106.695"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cities").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"10.777,", ",106.695", "90.1,106.695", "-90.1,106.695", "10.777,180.1", "10.777,-180.1"})
    void invalidCoordinatePairsFailBeforeCallingTheCarrier(String latitude, String longitude) throws Exception {
        var request = get("/api/v1/shipping/vehicles");
        if (latitude != null) request.param("latitude", latitude);
        if (longitude != null) request.param("longitude", longitude);
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(provider);
    }

    @Test void malformedCoordinateReturnsAFieldError() throws Exception {
        mvc.perform(get("/api/v1/shipping/vehicles").param("latitude", "abc").param("longitude", "106.695"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("latitude"));
        verifyNoInteractions(provider);
    }

    @Test void pickupOutsideSupportedRegionsReturnsAnExplicitError() throws Exception {
        mvc.perform(get("/api/v1/shipping/vehicles").param("latitude", "48.8566").param("longitude", "2.3522"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Điểm lấy hàng nằm ngoài khu vực vận chuyển được hỗ trợ"));
        verifyNoInteractions(provider);
    }

    @Test void missingCityOrEmptyCatalogueReturnsEmptyDataWithoutInventingVehicles() throws Exception {
        when(provider.cities()).thenReturn(mapper.readTree("[]"));
        mvc.perform(get("/api/v1/shipping/vehicles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cities").isEmpty());
        mvc.perform(get("/api/v1/shipping/vehicles").param("latitude", "10.777").param("longitude", "106.695"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cities").isEmpty());
    }

    @Test void malformedCarrierCatalogueReturnsBadGateway() throws Exception {
        when(provider.cities()).thenReturn(mapper.readTree("{}"));
        mvc.perform(get("/api/v1/shipping/vehicles"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void carrierFailureIsNotReportedAsAnUnsupportedParcel() throws Exception {
        when(provider.cities()).thenThrow(new ShippingProviderException("Lalamove is unavailable", 503));
        mvc.perform(get("/api/v1/shipping/vehicles"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Lalamove is unavailable"));
    }

    @Test void existingRawCityEndpointRetainsItsContract() throws Exception {
        mvc.perform(get("/api/v1/shipping/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].services[0].key").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.data[0].services[0].load.unit").value("g"))
                .andExpect(jsonPath("$.data[0].vehicles").doesNotExist());
    }

    private static final String CATALOGUE = """
            [{"locode":"VN HAN","name":"Hanoi","services":[
              {"key":"MOTORCYCLE","load":{"value":"30000","unit":"g"},"dimensions":{
                "length":{"value":"500","unit":"mm"},"width":{"value":"400","unit":"mm"},"height":{"value":"500","unit":"mm"}}}]},
             {"locode":"  vn   sgn ","name":"Ho Chi Minh City","services":[
              {"key":"VAN","description":"Van for large goods","load":{"value":"0.5","unit":"t"},"dimensions":{
                "length":{"value":"1.7","unit":"m"},"width":{"value":"1.2","unit":"m"},"height":{"value":"1.2","unit":"m"}}},
              {"key":"TRUCK_5000","load":{"value":"5000","unit":"kg"},"dimensions":{
                "length":{"value":"600","unit":"cm"},"width":{"value":"220","unit":"cm"},"height":{"value":"220","unit":"cm"}}},
              {"key":"UNKNOWN","load":{"value":"100","unit":"pounds"},"dimensions":{}},
              {"key":"MISSING_DIMENSIONS","load":{"value":"1000","unit":"kg"}},
              {"key":"INVALID-KEY","load":{"value":"1000","unit":"kg"},"dimensions":{}},
              {"key":"NEGATIVE","load":{"value":"-1","unit":"kg"},"dimensions":{}}]},
             {"locode":"TH BKK","name":"Hanoi","services":[]},
             {"locode":"VN VCA","name":"Can Tho","services":[]}]
            """;
}
