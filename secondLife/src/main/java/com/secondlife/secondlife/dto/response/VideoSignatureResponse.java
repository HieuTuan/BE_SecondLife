package com.secondlife.secondlife.dto.response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public record VideoSignatureResponse(UUID uploadId, String publicId, String signature, long timestamp,
        String apiKey, String cloudName, String uploadUrl, Map<String,String> parameters,
        Instant expiresAt, long maxBytes, int chunkSizeBytes) { }
