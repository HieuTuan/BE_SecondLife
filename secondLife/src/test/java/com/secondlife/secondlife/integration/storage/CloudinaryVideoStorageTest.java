package com.secondlife.secondlife.integration.storage;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.secondlife.secondlife.config.VideoUploadProperties;
import com.secondlife.secondlife.exception.VideoUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryVideoStorageTest {
    private static final String PUBLIC_ID = "report-evidence/videos/00000000-0000-4000-8000-000000000001";
    private static final String VIDEO_URL = "https://res.cloudinary.com/test-cloud/video/authenticated/v123/" + PUBLIC_ID + ".mp4";
    private Cloudinary cloudinary;
    private Api api;
    private CloudinaryVideoStorage storage;

    @BeforeEach
    void setUp() {
        cloudinary = spy(new Cloudinary(ObjectUtils.asMap("cloud_name", "test-cloud", "api_key", "test-key",
                "api_secret", "test-secret", "secure", true)));
        api = mock(Api.class);
        doReturn(api).when(cloudinary).api();
        storage = new CloudinaryVideoStorageImpl(cloudinary, properties("signed-video-preset"));
    }

    @Test
    void signsOnlyServerFixedAuthenticatedUploadParameters() throws Exception {
        long before = Instant.now().getEpochSecond();
        var upload = storage.sign(PUBLIC_ID);
        assertTrue(upload.timestamp() >= before && upload.timestamp() <= Instant.now().getEpochSecond());
        assertEquals("test-key", upload.apiKey());
        assertEquals("test-cloud", upload.cloudName());
        assertEquals("https://api.cloudinary.com/v1_1/test-cloud/video/upload", upload.uploadUrl());
        assertEquals(Map.of("timestamp", Long.toString(upload.timestamp()), "upload_preset", "signed-video-preset",
                "public_id", PUBLIC_ID, "type", "authenticated", "overwrite", "false", "allowed_formats", "mp4,mov,webm"),
                upload.parameters());
        assertEquals(signature(upload.parameters()), upload.signature());
        assertFalse(upload.parameters().containsKey("resource_type"));
        assertFalse(upload.parameters().containsKey("api_secret"));
        assertThrows(UnsupportedOperationException.class, () -> upload.parameters().put("type", "upload"));
    }

    @Test
    void missingPresetDisablesSigningWithoutExposingConfiguration() {
        storage = new CloudinaryVideoStorageImpl(cloudinary, properties(""));
        var error = assertThrows(VideoUploadException.class, () -> storage.sign(PUBLIC_ID));
        assertFalse(error.getMessage().contains("test-secret"));
        assertFalse(error.getMessage().contains("test-key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"other/00000000-0000-4000-8000-000000000001", "report-evidence/videos/../secret",
            "https://example.com/video.mp4", "report-evidence/videos/not-a-uuid"})
    void rejectsReferencesOutsideTheServerOwnedUploadNamespace(String publicId) {
        assertThrows(IllegalArgumentException.class, () -> storage.sign(publicId));
        assertThrows(IOException.class, () -> storage.inspect(publicId));
        assertThrows(IOException.class, () -> storage.playback(publicId, "mp4", Instant.now().plusSeconds(60)));
    }

    @Test
    void inspectsTrustedAdminMetadataWithAnExplicitTimeout() throws Exception {
        respond(validVideo());
        var video = storage.inspect(PUBLIC_ID);
        assertEquals(PUBLIC_ID, video.publicId());
        assertEquals(VIDEO_URL, video.videoUrl());
        assertEquals(2048, video.bytes());
        assertEquals("mp4", video.format());
        assertEquals(2.5, video.duration());
        assertEquals("asset-id", video.assetId());
        assertEquals(123, video.version());
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(api).resource(eq(PUBLIC_ID), options.capture());
        assertEquals("video", options.getValue().get("resource_type"));
        assertEquals("authenticated", options.getValue().get("type"));
        assertEquals(true, options.getValue().get("media_metadata"));
        assertEquals(10, options.getValue().get("timeout"));
        assertEquals(10, options.getValue().get("connect_timeout"));
        assertEquals(10, options.getValue().get("connection_request_timeout"));
    }

    @Test
    void rejectsMetadataForAnotherAssetOrDeliveryType() throws Exception {
        for (var entry : Map.of("public_id", "other-id", "type", "upload", "resource_type", "image").entrySet()) {
            var data = validVideo();
            data.put(entry.getKey(), entry.getValue());
            respond(data);
            assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID), entry.getKey());
        }
    }

    @Test
    void rejectsAudioOnlyResourcesEvenWhenTheProviderCallsThemVideo() throws Exception {
        var data = validVideo();
        data.put("width", 0);
        data.put("height", 0);
        data.put("video", Map.of());
        data.put("audio", Map.of("codec", "aac"));
        respond(data);
        assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID));
    }

    @Test
    void rejectsAnExplicitMissingVideoStream() throws Exception {
        var data = validVideo();
        data.put("video", Map.of());
        respond(data);
        assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID));
    }

    @Test
    void rejectsMissingNonFiniteUnsupportedAndOversizedMetadata() throws Exception {
        Map<String, Object> invalid = Map.of("bytes", 262144001L, "duration", 600.1, "format", "mp3",
                "secure_url", "http://example.com/video", "asset_id", "", "version", 0);
        for (var entry : invalid.entrySet()) {
            var data = validVideo();
            data.put(entry.getKey(), entry.getValue());
            respond(data);
            assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID), entry.getKey());
        }
        for (Object duration : new Object[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, "2.5"}) {
            var data = validVideo();
            data.put("duration", duration);
            respond(data);
            assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID), "duration " + duration);
        }
        for (String key : new String[] {"bytes", "duration", "format", "secure_url", "asset_id", "version"}) {
            var data = validVideo();
            data.remove(key);
            respond(data);
            assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID), key);
        }
    }

    @Test
    void providerFailureIsSanitizedWithoutLeakingItsCause() throws Exception {
        when(api.resource(eq(PUBLIC_ID), anyMap())).thenThrow(new IOException("test-secret in SDK request"));
        var error = assertThrows(IOException.class, () -> storage.inspect(PUBLIC_ID));
        assertFalse(error.getMessage().contains("test-secret"));
        assertEquals(null, error.getCause());
    }

    @Test
    void generatesAuthenticatedPlaybackWithAnEnforcedExpiryUsingTheSdk() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(300);
        URI url = URI.create(storage.playback(PUBLIC_ID, "mp4", expiresAt));
        assertEquals("https", url.getScheme());
        assertEquals("api.cloudinary.com", url.getHost());
        assertEquals("/v1_1/test-cloud/video/download", url.getPath());
        Map<String, String> query = query(url);
        assertEquals(PUBLIC_ID, query.get("public_id"));
        assertEquals("mp4", query.get("format"));
        assertEquals("authenticated", query.get("type"));
        assertEquals("false", query.get("attachment"));
        assertEquals(Long.toString(expiresAt.getEpochSecond()), query.get("expires_at"));
        assertEquals("test-key", query.remove("api_key"));
        String actualSignature = query.remove("signature");
        assertNotNull(actualSignature);
        assertEquals(signature(query), actualSignature);
        assertFalse(url.toString().contains("test-secret"));
    }

    @Test
    void rejectsExpiredPlaybackAndUnsupportedFormats() {
        assertThrows(IOException.class, () -> storage.playback(PUBLIC_ID, "mp4", Instant.now().minusSeconds(1)));
        assertThrows(IOException.class, () -> storage.playback(PUBLIC_ID, "exe", Instant.now().plusSeconds(30)));
    }

    private void respond(Map<String, Object> data) throws Exception {
        when(api.resource(eq(PUBLIC_ID), anyMap())).thenReturn(mock(ApiResponse.class, delegatesTo(data)));
    }

    private static Map<String, Object> validVideo() {
        var data = new HashMap<String, Object>();
        data.put("public_id", PUBLIC_ID);
        data.put("secure_url", VIDEO_URL);
        data.put("resource_type", "video");
        data.put("type", "authenticated");
        data.put("bytes", 2048);
        data.put("format", "mp4");
        data.put("duration", 2.5);
        data.put("width", 1280);
        data.put("height", 720);
        data.put("video", Map.of("codec", "h264"));
        data.put("asset_id", "asset-id");
        data.put("version", 123);
        return data;
    }

    private static VideoUploadProperties properties(String preset) {
        return new VideoUploadProperties(preset, 262144000, 600, 3600, 300, 10, 10000, 6291456);
    }

    private static String signature(Map<String, String> values) throws Exception {
        String payload = new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest((payload + "test-secret").getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new HashMap<>();
        for (String entry : uri.getRawQuery().split("&")) {
            String[] pair = entry.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }
}
