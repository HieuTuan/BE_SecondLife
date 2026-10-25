package com.secondlife.secondlife.integration.storage;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public interface CloudinaryVideoStorage {
    record SignedUpload(String signature, long timestamp, String apiKey, String cloudName, String uploadUrl,
                        Map<String, String> parameters) { }

    record VerifiedVideo(String publicId, String videoUrl, long bytes, String format, double duration,
                         String assetId, long version) { }

    SignedUpload sign(String publicId);

    VerifiedVideo inspect(String publicId) throws IOException;

    String playback(String publicId, String format, Instant expiresAt) throws IOException;
}