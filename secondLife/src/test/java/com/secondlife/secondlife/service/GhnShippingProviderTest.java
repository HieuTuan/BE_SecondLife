package com.secondlife.secondlife.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.secondlife.secondlife.config.GhnProperties;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.service.shipping.GhnShippingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GhnShippingProviderTest {
    private static final String BASE = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    private static final String SECRET = "test-token-must-never-appear-in-errors";

    @Test
    void newProvincesUseV3AndUnwrapTheGhnData() {
        Module module = module();
        expect(module, HttpMethod.GET, "/v3/master-data/province/all?offset=0&limit=200")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"_id":1000001,"name":"Hồ Chí Minh",
                        "extension_names":["hồ chí minh","hcm"],"type":"province","parent_id":1,"status":1}]}
                        """, MediaType.APPLICATION_JSON));

        JsonNode data = module.provider().provinces();

        assertTrue(data.isArray());
        assertEquals(1000001, data.get(0).path("_id").asInt());
        assertEquals("Hồ Chí Minh", data.get(0).path("name").asText());
        assertFalse(data.has("code"));
        module.server().verify();
    }

    @Test
    void newWardsAreFetchedDirectlyByNewProvinceId() {
        Module module = module();
        expect(module, HttpMethod.GET, "/v3/master-data/ward/all-by-province-id?province_id=1000001&offset=0&limit=200")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"_id":1003646,"name":"Phường Vũng Tàu",
                        "extension_names":["vũng tàu"],"type":"ward","parent_id":1000001,"status":1}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("Phường Vũng Tàu", module.provider().wards(1000001).get(0).path("name").asText());
        module.server().verify();
    }

    @Test
    void legacyProvincesExposeTheCodesUsedByDistrictLookup() {
        Module module = module();
        expect(module, HttpMethod.GET, "/master-data/province")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"ProvinceID":202,
                        "ProvinceName":"Hồ Chí Minh","CountryID":1,"Code":"79","IsEnable":1,
                        "RegionID":3,"NameExtension":["TP.HCM","HCM","Hồ Chí Minh"]}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(202, module.provider().legacyProvinces().get(0).path("ProvinceID").asInt());
        module.server().verify();
    }

    @Test
    void legacyDistrictsUseTheLegacyProvinceCode() {
        Module module = module();
        expect(module, HttpMethod.GET, "/master-data/district?province_id=202")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"DistrictID":3695,
                        "DistrictName":"Thành Phố Thủ Đức","SupportType":3,"CanUpdateCOD":false}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(3695, module.provider().districts(202).get(0).path("DistrictID").asInt());
        module.server().verify();
    }

    @Test
    void legacyWardCodesRemainStringsIncludingLeadingZeroes() {
        Module module = module();
        expect(module, HttpMethod.GET, "/master-data/ward?district_id=1444")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"WardCode":"00308",
                        "WardName":"Phường Bến Nghé","SupportType":3,"CanUpdateCOD":true}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("00308", module.provider().legacyWards(1444).get(0).path("WardCode").asText());
        module.server().verify();
    }

    @Test
    void availableServicesSendTheShopIdInTheBodyAsWellAsTheHeader() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/shipping-order/available-services")
                .andExpect(content().json("{\"shop_id\":92837,\"from_district\":1452,\"to_district\":1444}"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[
                        {"service_id":53320,"short_name":"Hàng nhẹ","service_type_id":2},
                        {"service_id":100039,"short_name":"Hàng nặng","service_type_id":5}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(5, module.provider().services(1452, 1444).get(1).path("service_type_id").asInt());
        module.server().verify();
    }

    @Test
    void feeSendsLegacyCompatibilityCodesAndKeepsTheProviderAmounts() {
        Module module = module();
        Map<String, Object> payload = Map.of("to_district_id", 1444, "to_ward_code", "00308",
                "service_type_id", 2, "weight", 600, "insurance_value", 285000);
        expect(module, HttpMethod.POST, "/v2/shipping-order/fee")
                .andExpect(content().json("""
                        {"to_district_id":1444,"to_ward_code":"00308","service_type_id":2,
                        "weight":600,"insurance_value":285000}
                        """))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"total":20900,"service_fee":20900,
                        "insurance_fee":0,"cod_fee":0,"pick_station_fee":0,"coupon_value":0,"r2s_fee":0,
                        "return_again":0,"document_return":0,"double_check":0,"pick_remote_areas_fee":0,
                        "deliver_remote_areas_fee":0,"cod_failed_fee":0,"change_to_address_fee":0,
                        "change_return_address_fee":0,"return":0}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(20900, module.provider().quote(payload).path("total").asInt());
        module.server().verify();
    }

    @Test
    void leadtimeUsesServiceTypeAndPreservesUnixSeconds() {
        Module module = module();
        Map<String, Object> payload = Map.of("to_district_id", 1444, "to_ward_code", "00308", "service_type_id", 2);
        expect(module, HttpMethod.POST, "/v2/shipping-order/leadtime")
                .andExpect(content().json("{\"to_district_id\":1444,\"to_ward_code\":\"00308\",\"service_type_id\":2}"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"leadtime":1784221199,
                        "leadtime_order":{"from_estimate_date":"2026-07-16T16:59:59Z",
                        "to_estimate_date":"2026-07-16T16:59:59Z"}}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(1784221199L, module.provider().leadtime(payload).path("leadtime").asLong());
        module.server().verify();
    }

    @Test
    void previewUsesTheCreateAddressPayloadAndReturnsTheAuthoritativeFeeAndDeliveryTime() {
        Module module = module();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payment_type_id", 2);
        payload.put("required_note", "KHONGCHOXEMHANG");
        payload.put("to_name", "Test Buyer");
        payload.put("to_phone", "0987654321");
        payload.put("to_address", "72 Lê Thánh Tôn");
        payload.put("to_province_name", "Hồ Chí Minh");
        payload.put("to_ward_name", "Phường Sài Gòn");
        payload.put("is_new_to_address", true);
        payload.put("from_name", "Test Seller");
        payload.put("from_phone", "0332190444");
        payload.put("from_address", "39 Nguyễn Thị Thập");
        payload.put("from_province_name", "Hồ Chí Minh");
        payload.put("from_ward_name", "Phường Tân Phú");
        payload.put("is_new_from_address", true);
        payload.put("return_address", "39 Nguyễn Thị Thập");
        payload.put("return_province_name", "Hồ Chí Minh");
        payload.put("return_ward_name", "Phường Tân Phú");
        payload.put("is_new_return_address", true);
        payload.put("service_type_id", 2);
        payload.put("weight", 600);
        payload.put("length", 25);
        payload.put("width", 20);
        payload.put("height", 8);
        payload.put("insurance_value", 285000);
        payload.put("cod_amount", 285000);
        payload.put("content", "Second-life goods");
        payload.put("items", List.of(Map.of("name", "Second-life goods", "quantity", 1, "weight", 600)));
        expect(module, HttpMethod.POST, "/v2/shipping-order/preview")
                .andExpect(content().json("""
                        {"payment_type_id":2,"required_note":"KHONGCHOXEMHANG",
                        "to_name":"Test Buyer","to_phone":"0987654321","to_address":"72 Lê Thánh Tôn",
                        "to_province_name":"Hồ Chí Minh","to_ward_name":"Phường Sài Gòn","is_new_to_address":true,
                        "from_name":"Test Seller","from_phone":"0332190444","from_address":"39 Nguyễn Thị Thập",
                        "from_province_name":"Hồ Chí Minh","from_ward_name":"Phường Tân Phú","is_new_from_address":true,
                        "return_address":"39 Nguyễn Thị Thập","return_province_name":"Hồ Chí Minh",
                        "return_ward_name":"Phường Tân Phú","is_new_return_address":true,
                        "service_type_id":2,"weight":600,"length":25,"width":20,"height":8,
                        "insurance_value":285000,"cod_amount":285000,"content":"Second-life goods",
                        "items":[{"name":"Second-life goods","quantity":1,"weight":600}]}
                        """, STRICT))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"order_code":"",
                        "fee":{"main_service":20900,"insurance":0,"cod_fee":0,"station_do":0,
                        "station_pu":0,"return":0,"r2s":0,"return_again":0,"coupon":0,
                        "document_return":0,"double_check":0,"double_check_deliver":0,
                        "pick_remote_areas_fee":0,"deliver_remote_areas_fee":0,
                        "pick_remote_areas_fee_return":0,"deliver_remote_areas_fee_return":0,
                        "cod_failed_fee":0,"change_to_address_fee":0,"change_return_address_fee":0},
                        "total_fee":20900,"expected_delivery_time":"2026-07-16T16:59:59Z"}}
                        """, MediaType.APPLICATION_JSON));

        JsonNode preview = module.provider().preview(payload);

        assertEquals(20900, preview.path("total_fee").asInt());
        assertEquals("2026-07-16T16:59:59Z", preview.path("expected_delivery_time").asText());
        assertEquals("", preview.path("order_code").asText());
        module.server().verify();
    }

    @Test
    void createsWithNewAddressNamesAndTheStableClientOrderCode() {
        Module module = module();
        Map<String, Object> payload = Map.of("client_order_code", "SL-123", "is_new_to_address", true,
                "to_province_name", "Hồ Chí Minh", "to_ward_name", "Phường Sài Gòn", "weight", 600);
        expect(module, HttpMethod.POST, "/v2/shipping-order/create")
                .andExpect(content().json("""
                        {"client_order_code":"SL-123","is_new_to_address":true,
                        "to_province_name":"Hồ Chí Minh","to_ward_name":"Phường Sài Gòn","weight":600}
                        """))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"order_code":"LADFYR",
                        "fee":{"main_service":20900,"insurance":0,"cod_fee":0,"station_do":0,
                        "station_pu":0,"return":0,"r2s":0,"return_again":0,"coupon":0,
                        "document_return":0,"double_check":0,"double_check_deliver":0},
                        "total_fee":20900,"expected_delivery_time":"2026-07-15T16:59:59Z"}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("LADFYR", module.provider().create(payload).path("order_code").asText());
        module.server().verify();
    }

    @Test
    void detailUsesTheDocumentedGetQuery() {
        Module module = module();
        expect(module, HttpMethod.GET, "/v2/shipping-order/detail?order_code=LADFYR")
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"order_code":"LADFYR",
                        "client_order_code":"SL-123","status":"ready_to_pick","shop_id":92837}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("ready_to_pick", module.provider().detail("LADFYR").path("status").asText());
        module.server().verify();
    }

    @Test
    void cancelRequiresAnExplicitSuccessfulPerOrderResult() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/switch-status/cancel")
                .andExpect(content().json("{\"order_codes\":[\"LADFYR\"]}"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"order_code":"LADFYR","result":true,"message":"OK"}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(module.provider().cancel("LADFYR").get(0).path("result").asBoolean());
        module.server().verify();
    }

    @Test
    void returnUsesTheReturnStatusEndpoint() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/switch-status/return")
                .andExpect(content().json("{\"order_codes\":[\"LADFYR\"]}"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"order_code":"LADFYR","result":true,"message":"OK"}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(module.provider().returnToSender("LADFYR").get(0).path("result").asBoolean());
        module.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "return"})
    void rejectedMutationIsNotReportedAsSuccess(String operation) {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/switch-status/" + operation)
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":[{"order_code":"LADFYR",
                        "result":false,"message":"Trạng thái đơn hàng không hợp lệ"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(ShippingProviderException.class, () -> {
            if (operation.equals("cancel")) module.provider().cancel("LADFYR");
            else module.provider().returnToSender("LADFYR");
        });
        module.server().verify();
    }

    @Test
    void labelBuildsTheFixedGhnUrlInsteadOfTrustingAProviderUrl() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/a5/gen-token")
                .andExpect(content().json("{\"order_codes\":[\"LADFYR\"]}"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success","data":{"token":"1b744cae-8005-11f1-b50f-ba37616041ec",
                        "url":"https://untrusted.example/label"}}
                        """, MediaType.APPLICATION_JSON));

        JsonNode label = module.provider().label("LADFYR");

        assertEquals("https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=1b744cae-8005-11f1-b50f-ba37616041ec",
                label.path("url").asText());
        assertFalse(label.has("token"));
        module.server().verify();
    }

    @Test
    void labelEncodesTheTokenAsOneQueryValue() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/a5/gen-token")
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"token\":\"abc&redirect=https://untrusted.example\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=abc%26redirect%3Dhttps%3A%2F%2Funtrusted.example",
                module.provider().label("LADFYR").path("url").asText());
        module.server().verify();
    }

    @Test
    void labelWithMissingPrintTokenIsRejected() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/a5/gen-token")
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThrows(ShippingProviderException.class, () -> module.provider().label("LADFYR"));
        module.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"order_code\":\"OTHER\",\"result\":true,\"message\":\"OK\"}]",
            "[{\"order_code\":\"LADFYR\",\"message\":\"OK\"}]"})
    void mutationRequiresResultForTheRequestedOrder(String data) {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/switch-status/cancel")
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":" + data + "}", MediaType.APPLICATION_JSON));

        assertThrows(ShippingProviderException.class, () -> module.provider().cancel("LADFYR"));
        module.server().verify();
    }

    @Test
    void redirectsAreRejectedWithoutFollowingTheLocation() {
        Module module = module();
        expect(module, HttpMethod.GET, "/v3/master-data/province/all?offset=0&limit=200")
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://untrusted.example"));

        assertThrows(ShippingProviderException.class, module.provider()::provinces);
        module.server().verify();
    }

    @Test
    void disabledProviderFailsBeforeSendingAnyRequest() {
        GhnProperties disabled = properties();
        disabled.setEnabled(false);
        Module module = module(disabled);

        assertThrows(ShippingProviderException.class, module.provider()::provinces);
        assertThrows(ShippingProviderException.class, () -> module.provider().create(Map.of()));
        assertThrows(ShippingProviderException.class, () -> module.provider().cancel("LADFYR"));
        module.server().verify();
    }

    @Test
    void missingCredentialsFailBeforeSendingAnyRequest() {
        GhnProperties properties = properties();
        properties.setToken(" ");
        Module module = module(properties);

        assertThrows(ShippingProviderException.class, module.provider()::provinces);
        properties.setToken(SECRET);
        properties.setShopId(0);
        assertThrows(ShippingProviderException.class, module.provider()::provinces);
        module.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://online-gateway.ghn.vn/shiip/public-api", "https://untrusted.example/shiip/public-api",
            "https://online-gateway.ghn.vn.evil.example/shiip/public-api", "https://online-gateway.ghn.vn/other",
            "https://online-gateway.ghn.vn/shiip/public-api?token=secret", "https://user@online-gateway.ghn.vn/shiip/public-api"})
    void rejectsUnapprovedGatewayBeforeSendingSecrets(String baseUrl) {
        GhnProperties properties = properties();
        properties.setBaseUrl(baseUrl);
        Module module = module(properties);

        ShippingProviderException error = assertThrows(ShippingProviderException.class, module.provider()::provinces);

        assertFalse(error.getMessage().contains(baseUrl));
        module.server().verify();
    }

    @Test
    void officialProductionBaseAndTrailingSlashAreSupported() {
        GhnProperties properties = properties();
        properties.setBaseUrl("https://online-gateway.ghn.vn/shiip/public-api/");
        Module module = module(properties);
        module.server().expect(requestTo("https://online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/detail?order_code=LADFYR"))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"order_code\":\"LADFYR\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals("LADFYR", module.provider().detail("LADFYR").path("order_code").asText());
        module.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":400,\"message\":\"secret receiver\",\"data\":{}}",
            "{\"code\":200,\"message\":\"Success\",\"data\":null}", "{\"code\":200}",
            "{\"message\":\"Success\",\"data\":[]}", "{\"code\":\"200\",\"data\":[]}", "not-json"})
    void rejectsUnsuccessfulOrMalformedEnvelopes(String response) {
        Module module = module();
        expect(module, HttpMethod.GET, "/v3/master-data/province/all?offset=0&limit=200")
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ShippingProviderException error = assertThrows(ShippingProviderException.class, module.provider()::provinces);

        assertFalse(error.getMessage().contains(response));
        assertNull(error.getCause());
        module.server().verify();
    }

    @Test
    void httpErrorsAreSanitizedAndMutationsAreNotRetried() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/shipping-order/create")
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"" + SECRET + " receiver-address phone-number\"}"));

        ShippingProviderException error = assertThrows(ShippingProviderException.class,
                () -> module.provider().create(Map.of("client_order_code", "SL-123")));

        assertEquals(502, error.getUpstreamStatus());
        assertFalse(error.getMessage().contains(SECRET));
        assertFalse(error.getMessage().contains("receiver-address"));
        assertNull(error.getCause());
        module.server().verify();
    }

    @Test
    void timeoutIsSanitizedAndCreateIsNotAutomaticallyRetried() {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/shipping-order/create")
                .andRespond(request -> { throw new ResourceAccessException(SECRET, new SocketTimeoutException()); });

        ShippingProviderException error = assertThrows(ShippingProviderException.class,
                () -> module.provider().create(Map.of("client_order_code", "SL-123")));

        assertEquals(504, error.getUpstreamStatus());
        assertFalse(error.getMessage().contains(SECRET));
        assertNull(error.getCause());
        module.server().verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void debugLoggingDoesNotExposeRecipientData(CapturedOutput output) {
        Module module = module();
        expect(module, HttpMethod.POST, "/v2/shipping-order/create")
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"order_code\":\"LADFYR\"}}",
                        MediaType.APPLICATION_JSON));
        Logger logger = (Logger) LoggerFactory.getLogger("org.springframework.web.client.DefaultRestClient");
        Level previousLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            module.provider().create(Map.of("to_name", "private-recipient-marker", "client_order_code", "SL-123"));

            assertFalse(output.getAll().contains("private-recipient-marker"));
            assertFalse(output.getAll().contains(SECRET));
        } finally {
            logger.setLevel(previousLevel);
        }
        module.server().verify();
    }

    @Test
    void rejectsInvalidInputsBeforeMakingRequests() {
        Module module = module();

        assertThrows(ShippingProviderException.class, () -> module.provider().wards(0));
        assertThrows(ShippingProviderException.class, () -> module.provider().districts(-1));
        assertThrows(ShippingProviderException.class, () -> module.provider().legacyWards(0));
        assertThrows(ShippingProviderException.class, () -> module.provider().services(0, 1444));
        assertThrows(ShippingProviderException.class, () -> module.provider().detail(" "));
        assertThrows(ShippingProviderException.class, () -> module.provider().quote(null));
        module.server().verify();
    }

    private static ResponseActions expect(Module module, HttpMethod method, String path) {
        ResponseActions expectation = module.server().expect(requestTo(BASE + path))
                .andExpect(method(method)).andExpect(header("Token", SECRET)).andExpect(header("ShopId", "92837"));
        if (method == HttpMethod.POST) expectation.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        return expectation;
    }

    private static Module module() {
        return module(properties());
    }

    private static Module module(GhnProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        GhnShippingProvider provider = new GhnShippingProvider(builder, JsonMapper.builder().build(), properties);
        return new Module(provider, MockRestServiceServer.bindTo(builder).build());
    }

    private static GhnProperties properties() {
        GhnProperties properties = new GhnProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://dev-online-gateway.ghn.vn/shiip/public-api");
        properties.setTimeoutMs(5000);
        properties.setQuoteTtlSeconds(600);
        properties.setMaxResponseCharacters(4 * 1024 * 1024);
        properties.setWebhookMaxFutureSkewSeconds(300);
        properties.setWebhookSecret("test-webhook-secret");
        properties.setToken(SECRET);
        properties.setShopId(92837);
        return properties;
    }

    private record Module(GhnShippingProvider provider, MockRestServiceServer server) { }
}
