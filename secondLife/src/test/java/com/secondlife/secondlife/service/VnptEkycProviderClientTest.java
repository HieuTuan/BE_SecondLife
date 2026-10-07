package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.VnptEkycProperties;
import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.service.ekyc.impl.VnptEkycProviderClient;
import com.secondlife.secondlife.service.ekyc.vnpt.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VnptEkycProviderClientTest {
    private static final String BASE = "https://api.idg.vnpt.vn";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
    private static final String OCR = "{\"message\":\"IDG-00000000\",\"statusCode\":200,\"object\":{\"msg\":\"OK\",\"id\":\"001\",\"name\":\"Example User\",\"birth_day\":\"01/01/2000\",\"type_id\":5,\"general_warning\":[]}}";
    private static final String CARD = "{\"message\":\"IDG-00000000\",\"object\":{\"liveness\":\"success\",\"fake_print_photo\":false}}";
    private static final String FACE = "{\"message\":\"IDG-00000000\",\"object\":{\"liveness\":\"success\",\"blur_face\":\"no\",\"multiple_faces_details\":{\"multiple_face_1\":false}}}";
    private static final String MASK = "{\"message\":\"IDG-00000000\",\"object\":{\"masked\":\"no\"}}";

    @Test
    void tokenIsCachedAndRefreshedBeforeExpiry() {
        Module module = module();
        expectToken(module.server(), "first-token");
        expectToken(module.server(), "second-token");
        assertEquals("first-token", module.tokens().getToken());
        assertEquals("first-token", module.tokens().getToken());
        ReflectionTestUtils.setField(module.tokens(), "expiresAtMillis", System.currentTimeMillis() + 10_000);
        assertEquals("second-token", module.tokens().getToken());
        module.server().verify();
    }

    @Test
    void configuredRefreshMarginRefreshesBeforeTheOldMargin() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        expectToken(server, "first-token");
        expectToken(server, "second-token");
        new ApplicationContextRunner()
                .withBean(VnptEkycProperties.class, () -> properties())
                .withBean("vnptRestTemplate", RestTemplate.class, () -> rest)
                .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(VnptTokenService.class)
                .withPropertyValues("app.ekyc.provider=VNPT", "app.ekyc.vnpt.token-refresh-margin-millis=120000")
                .run(context -> {
                    VnptTokenService tokens = context.getBean(VnptTokenService.class);
                    assertEquals("first-token", tokens.getToken());
                    ReflectionTestUtils.setField(tokens, "expiresAtMillis", System.currentTimeMillis() + 90_000);
                    assertEquals("second-token", tokens.getToken());
                });
        server.verify();
    }

    @Test
    void configuredFileLimitRejectsAnOtherwiseValidJpeg() {
        new ApplicationContextRunner()
                .withBean(VnptHttpClient.class, () -> mock(VnptHttpClient.class))
                .withBean(VnptFileClient.class)
                .withPropertyValues("app.ekyc.provider=VNPT", "app.ekyc.vnpt.max-image-bytes=4")
                .run(context -> {
                    byte[] oversized = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x00};
                    assertThrows(com.secondlife.secondlife.exception.BadRequestException.class,
                            () -> context.getBean(VnptFileClient.class).validateImage(oversized));
                    assertDoesNotThrow(() -> context.getBean(VnptFileClient.class).validateImage(JPEG));
                });
    }

    @Test
    void configuredDownloadLimitRequestsImageResubmission() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        server.expect(requestTo("https://res.cloudinary.com/test-cloud/image/upload/front.jpg"))
                .andRespond(withSuccess(new byte[5], MediaType.IMAGE_JPEG));
        new ApplicationContextRunner()
                .withBean(VnptEkycOrchestrator.class, () -> mock(VnptEkycOrchestrator.class))
                .withBean("vnptRestTemplate", RestTemplate.class, () -> rest)
                .withBean(VnptEkycProviderClient.class)
                .withPropertyValues("app.ekyc.provider=VNPT", "app.cloudinary.cloud-name=test-cloud",
                        "app.ekyc.vnpt.max-image-bytes=4")
                .run(context -> {
                    var result = context.getBean(VnptEkycProviderClient.class).verify(new EkycRequest(
                            VerificationType.CITIZEN_ID, "001",
                            "https://res.cloudinary.com/test-cloud/image/upload/front.jpg",
                            "https://res.cloudinary.com/test-cloud/image/upload/back.jpg",
                            "https://res.cloudinary.com/test-cloud/image/upload/selfie.jpg", "session", "request-token"));
                    assertEquals(EkycStatus.UNCERTAIN, result.status());
                    assertEquals(ReasonCode.IMAGE_NOT_ACCESSIBLE, result.reasonCode());
                });
        server.verify();
    }

    @Test
    void service401InvalidatesTokenAndRetriesOnce() {
        Module module = module();
        expectToken(module.server(), "first-token");
        module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andExpect(header("Authorization", "Bearer first-token"))
                .andExpect(header("Token-id", "dummy-token-id"))
                .andExpect(header("Token-key", "dummy-token-key"))
                .andExpect(header("mac-address", "TEST1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken(module.server(), "second-token");
        module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andExpect(header("Authorization", "Bearer second-token"))
                .andRespond(withSuccess(OCR, MediaType.APPLICATION_JSON));
        assertEquals("IDG-00000000", module.http().postJson("/ai/v1/web/ocr/id", Map.of()).path("message").asText());
        module.server().verify();
    }

    @Test
    void second401StopsAfterOneRetry() {
        Module module = module();
        expectToken(module.server(), "first-token");
        module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken(module.server(), "second-token");
        module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(VnptAuthenticationException.class,
                () -> module.http().postJson("/ai/v1/web/ocr/id", Map.of()));
        module.server().verify();
    }

    @Test
    void uploadExtractsHashFromMultipartResponse() {
        Module module = module();
        expectToken(module.server(), "cached-token");
        module.server().expect(requestTo(BASE + "/file-service/v1/addFile"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"file\"; filename=\"front.jpg\"")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andRespond(withSuccess("{\"message\":\"IDG-00000000\",\"object\":{\"hash\":\"front-hash\"}}",
                        MediaType.APPLICATION_JSON));
        assertEquals("front-hash", module.files().upload(JPEG, "front"));
        module.server().verify();
    }

    @Test
    void rejectsInvalidImageWithoutSendingItToVnpt() {
        Module module = module();
        assertThrows(com.secondlife.secondlife.exception.BadRequestException.class,
                () -> module.files().upload(new byte[]{1, 2, 3, 4}, "front"));
        module.server().verify();
    }

    @Test
    void malformedTokenResponseFailsWithoutUsingIt() {
        Module module = module();
        module.server().expect(requestTo(BASE + "/auth/oauth/token"))
                .andRespond(withSuccess("{\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        assertThrows(VnptAuthenticationException.class, module.tokens()::getToken);
        module.server().verify();
    }

    @Test
    void tokenTimeoutIsReportedAsProviderTimeout() {
        Module module = module();
        module.server().expect(requestTo(BASE + "/auth/oauth/token"))
                .andRespond(request -> { throw new ResourceAccessException(
                        "connection timed out", new SocketTimeoutException()); });
        VnptApiException error = assertThrows(VnptApiException.class, module.tokens()::getToken);
        assertEquals(504, error.getUpstreamStatus());
        module.server().verify();
    }

    @Test
    void upload400IsReportedWithoutRetrying() {
        Module module = module();
        expectToken(module.server(), "cached-token");
        module.server().expect(requestTo(BASE + "/file-service/v1/addFile"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        VnptUploadException error = assertThrows(VnptUploadException.class,
                () -> module.files().upload(JPEG, "front"));
        assertEquals(400, error.getUpstreamStatus());
        module.server().verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void doesNotLogCredentialsOrAccessToken(CapturedOutput output) {
        Logger restLogger = (Logger) LoggerFactory.getLogger(RestTemplate.class);
        Level previous = restLogger.getLevel();
        restLogger.setLevel(Level.DEBUG);
        try {
            Module module = module();
            expectToken(module.server(), "dummy-access-token-marker");
            module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN));
            assertThrows(VnptAuthenticationException.class,
                    () -> module.http().postJson("/ai/v1/web/ocr/id", Map.of()));
            assertFalse(output.getAll().contains("dummy-client-secret"));
            assertFalse(output.getAll().contains("dummy-token-key"));
            assertFalse(output.getAll().contains("dummy-access-token-marker"));
            module.server().verify();
        } finally {
            restLogger.setLevel(previous);
        }
    }

    @Test
    void mapsOcrLivenessAndCompareFields() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();
        VnptResults.Ocr ocr = VnptResponseMapper.envelope(mapper.readTree(OCR), "ocr",
                VnptResponseMapper::ocr).object();
        assertEquals("001", ocr.id());
        assertEquals("Example User", ocr.name());
        assertEquals(5, ocr.typeId());
        assertEquals("01/01/2000", ocr.birthDay());
        assertEquals("success", VnptResponseMapper.envelope(mapper.readTree(CARD), "card",
                VnptResponseMapper::card).object().liveness());
        assertEquals(false, VnptResponseMapper.envelope(mapper.readTree(FACE), "face",
                VnptResponseMapper::face).object().multipleFacesDetails().multipleFace1());
        var compare = VnptResponseMapper.envelope(mapper.readTree(compare("MATCH", 71)), "compare",
                VnptResponseMapper::compare).object();
        assertEquals("MATCH", compare.msg());
        assertEquals(71.0, compare.prob());
    }

    @Test
    void orchestratorUploadsThreeImagesOnceAndAcceptsMatchWithoutInventedThreshold() {
        Module module = module();
        expectPipeline(module.server(), "MATCH", 71);
        VnptResults.Verification result = module.orchestrator().verify(JPEG, JPEG, JPEG,
                "IOS_model_os_device_sdk_id_time", "request-identifier", -1);
        assertTrue(result.verified());
        assertEquals("PASSED", result.reasonCode());
        assertEquals("MATCH", result.faceCompare().object().msg());
        module.server().verify();
    }

    @Test
    void orchestratorRejectsNoMatchAndMask() {
        Module module = module();
        expectPipeline(module.server(), "NOMATCH", 99);
        VnptResults.Verification result = module.orchestrator().verify(JPEG, JPEG, JPEG,
                "IOS_model_os_device_sdk_id_time", "request-identifier", -1);
        assertFalse(result.verified());
        assertEquals("FACE_MISMATCH", result.reasonCode());
        module.server().verify();

        EkycVerificationPolicy policy = new EkycVerificationPolicy();
        ObjectMapper mapper = JsonMapper.builder().build();
        try {
            var ocr = VnptResponseMapper.envelope(mapper.readTree(OCR), "ocr", VnptResponseMapper::ocr);
            var card = VnptResponseMapper.envelope(mapper.readTree(CARD), "card", VnptResponseMapper::card);
            var face = VnptResponseMapper.envelope(mapper.readTree(FACE), "face", VnptResponseMapper::face);
            var mask = VnptResponseMapper.envelope(mapper.readTree("{\"message\":\"IDG-00000000\",\"object\":{\"masked\":\"yes\"}}"),
                    "mask", VnptResponseMapper::mask);
            var compare = VnptResponseMapper.envelope(mapper.readTree(compare("MATCH", 99)),
                    "compare", VnptResponseMapper::compare);
            assertEquals("FACE_MASKED", policy.decide(ocr, card, face, mask, compare).reasonCode());
        } catch (Exception ex) { throw new AssertionError(ex); }
    }

    @Test
    void sellerAdapterUsesNewPipelineWithoutCompareGeneralThreshold() throws Exception {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        for (String name : new String[]{"front", "back", "selfie"}) {
            server.expect(requestTo("https://res.cloudinary.com/test-cloud/image/upload/" + name + ".jpg"))
                    .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        }
        VnptEkycOrchestrator orchestrator = mock(VnptEkycOrchestrator.class);
        ObjectMapper mapper = JsonMapper.builder().build();
        var ocr = VnptResponseMapper.envelope(mapper.readTree(OCR), "ocr", VnptResponseMapper::ocr);
        var compare = VnptResponseMapper.envelope(mapper.readTree(compare("MATCH", 71)),
                "compare", VnptResponseMapper::compare);
        when(orchestrator.verify(any(byte[].class), any(byte[].class), any(byte[].class),
                anyString(), anyString(), eq(-1)))
                .thenReturn(new VnptResults.Verification(UUID.randomUUID(), true, "PASSED",
                        ocr, null, null, null, compare));
        VnptEkycProviderClient adapter = new VnptEkycProviderClient(orchestrator, rest, "test-cloud", 10_485_760L);
        EkycRequest request = new EkycRequest(VerificationType.CITIZEN_ID, "001",
                "https://res.cloudinary.com/test-cloud/image/upload/front.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/back.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/selfie.jpg",
                "IOS_model_os_device_sdk_id_time", "request-identifier");

        var result = adapter.verify(request);

        assertEquals(EkycStatus.PASSED, result.status());
        assertEquals(0.71, result.faceMatchScore());
        server.verify();
        verify(orchestrator, times(1)).verify(any(byte[].class), any(byte[].class),
                any(byte[].class), eq("IOS_model_os_device_sdk_id_time"), eq("request-identifier"), eq(-1));
    }

    @Test
    void sellerAdapterRequestsResubmissionWhenVnptContextIsMissing() {
        VnptEkycProviderClient adapter = new VnptEkycProviderClient(
                mock(VnptEkycOrchestrator.class), new RestTemplate(), "test-cloud", 10_485_760L);
        var result = adapter.verify(new EkycRequest(VerificationType.CITIZEN_ID, "001",
                "https://res.cloudinary.com/test-cloud/image/upload/front.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/back.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/selfie.jpg"));
        assertEquals(EkycStatus.UNCERTAIN, result.status());
        assertEquals(ReasonCode.EKYC_CONTEXT_MISSING, result.reasonCode());
    }

    @Test
    void zuulGatewayRejectionMapsTo400AndRejectedCode() {
        Module module = module();
        expectToken(module.server(), "cached-token");
        module.server().expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andRespond(withSuccess("{\"dataSign\":\"xyz\",\"logID\":\"074a4601-3816-4357-9db0-a3528b8cf4ba-Zuulserver\"}",
                        MediaType.APPLICATION_JSON));
        VnptApiException error = assertThrows(VnptApiException.class,
                () -> module.http().postJson("/ai/v1/web/ocr/id", Map.of()));
        assertEquals(400, error.getUpstreamStatus());
        assertEquals("ZUUL_GATEWAY_REJECTED", error.getProviderCode());
        module.server().verify();
    }

    @Test
    void sellerAdapterMaps400ToProviderRequestRejected() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        for (String name : new String[]{"front", "back", "selfie"}) {
            server.expect(requestTo("https://res.cloudinary.com/test-cloud/image/upload/" + name + ".jpg"))
                    .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        }
        VnptEkycOrchestrator orchestrator = mock(VnptEkycOrchestrator.class);
        when(orchestrator.verify(any(), any(), any(), anyString(), anyString(), anyInt()))
                .thenThrow(new VnptApiException("rejected", 400, "/ai/v1/web/ocr/id", "ZUUL_GATEWAY_REJECTED"));
        VnptEkycProviderClient adapter = new VnptEkycProviderClient(orchestrator, rest, "test-cloud", 10_485_760L);
        EkycRequest request = new EkycRequest(VerificationType.CITIZEN_ID, "001",
                "https://res.cloudinary.com/test-cloud/image/upload/front.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/back.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/selfie.jpg",
                "session-123", "req-token");

        var result = adapter.verify(request);

        assertEquals(EkycStatus.PROVIDER_ERROR, result.status());
        assertEquals(ReasonCode.PROVIDER_REQUEST_REJECTED, result.reasonCode());
        server.verify();
    }

    @Test
    void sellerAdapterNormalizesClientSessionToVnptPattern() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        for (String name : new String[]{"front", "back", "selfie"}) {
            server.expect(requestTo("https://res.cloudinary.com/test-cloud/image/upload/" + name + ".jpg"))
                    .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        }
        VnptEkycOrchestrator orchestrator = mock(VnptEkycOrchestrator.class);
        when(orchestrator.verify(any(), any(), any(), argThat(s -> s != null && s.startsWith("ANDROID_Web_1.0_Device_1.0.0_WEBSDK123_")), anyString(), anyInt()))
                .thenThrow(new VnptApiException("rejected", 400, "/ai/v1/web/ocr/id", "ZUUL_GATEWAY_REJECTED"));
        VnptEkycProviderClient adapter = new VnptEkycProviderClient(orchestrator, rest, "test-cloud", 10_485_760L);
        EkycRequest request = new EkycRequest(VerificationType.CITIZEN_ID, "001",
                "https://res.cloudinary.com/test-cloud/image/upload/front.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/back.jpg",
                "https://res.cloudinary.com/test-cloud/image/upload/selfie.jpg",
                "WEB-SDK-123", "req-token");

        var result = adapter.verify(request);
        assertEquals(ReasonCode.PROVIDER_REQUEST_REJECTED, result.reasonCode());
        server.verify();
    }

    private void expectPipeline(MockRestServiceServer server, String match, int probability) {
        expectToken(server, "cached-token");
        for (String hash : new String[]{"front-hash", "back-hash", "selfie-hash"}) {
            server.expect(requestTo(BASE + "/file-service/v1/addFile"))
                    .andRespond(withSuccess("{\"message\":\"IDG-00000000\",\"object\":{\"hash\":\"" + hash + "\"}}",
                            MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo(BASE + "/ai/v1/web/ocr/id"))
                .andExpect(jsonPath("$.img_front").value("front-hash"))
                .andExpect(jsonPath("$.img_back").value("back-hash"))
                .andExpect(jsonPath("$.type").value(-1))
                .andExpect(jsonPath("$.validate_postcode").value(true))
                .andExpect(jsonPath("$.crop_param").value("0,0"))
                .andRespond(withSuccess(OCR, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/ai/v1/web/card/liveness"))
                .andExpect(jsonPath("$.img").value("front-hash"))
                .andExpect(jsonPath("$.token").value("request-identifier"))
                .andExpect(jsonPath("$.crop_param").value("0,0"))
                .andRespond(withSuccess(CARD, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/ai/v1/face/liveness"))
                .andExpect(jsonPath("$.img").value("selfie-hash"))
                .andRespond(withSuccess(FACE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/ai/v1/web/face/mask"))
                .andExpect(jsonPath("$.img").value("selfie-hash"))
                .andRespond(withSuccess(MASK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/ai/v1/web/face/compare"))
                .andExpect(jsonPath("$.img_front").value("front-hash"))
                .andExpect(jsonPath("$.img_face").value("selfie-hash"))
                .andRespond(withSuccess(compare(match, probability), MediaType.APPLICATION_JSON));
    }

    private String compare(String match, int probability) {
        return "{\"message\":\"IDG-00000000\",\"object\":{\"msg\":\"" + match
                + "\",\"prob\":" + probability + ",\"multiple_faces\":false}}";
    }

    private void expectToken(MockRestServiceServer server, String value) {
        server.expect(requestTo(BASE + "/auth/oauth/token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andExpect(jsonPath("$.client_id").value("dummy-client-id"))
                .andRespond(withSuccess("{\"access_token\":\"" + value + "\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    private Module module() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rest);
        ObjectMapper mapper = JsonMapper.builder().build();
        VnptEkycProperties props = properties();
        VnptTokenService tokens = new VnptTokenService(props, rest, mapper, 45_000L);
        VnptHttpClient http = new VnptHttpClient(props, tokens, rest, mapper);
        VnptFileClient files = new VnptFileClient(http, 10_485_760L);
        VnptEkycOrchestrator orchestrator = new VnptEkycOrchestrator(tokens, files,
                new VnptOcrClient(http), new VnptCardLivenessClient(http),
                new VnptFaceLivenessClient(http), new VnptMaskFaceClient(http),
                new VnptFaceCompareClient(http), new EkycVerificationPolicy());
        return new Module(server, tokens, http, files, orchestrator);
    }

    private VnptEkycProperties properties() {
        return new VnptEkycProperties(BASE, "dummy-client-id", "dummy-client-secret",
                "dummy-token-id", "dummy-token-key", "TEST1", 1000);
    }

    private record Module(MockRestServiceServer server, VnptTokenService tokens,
                          VnptHttpClient http, VnptFileClient files,
                          VnptEkycOrchestrator orchestrator) { }
}
