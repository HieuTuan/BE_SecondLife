package com.secondlife.secondlife.dto.response;
import java.time.Instant;
import java.util.UUID;
public record VideoMetadataResponse(UUID uploadId, UUID evidenceId, String evidenceUrl, String publicId,
        String videoUrl, long bytes, String format, double duration, Instant completedAt) { }
