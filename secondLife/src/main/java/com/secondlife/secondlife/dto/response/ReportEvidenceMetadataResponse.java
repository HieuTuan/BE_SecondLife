package com.secondlife.secondlife.dto.response;
import java.util.UUID;
public record ReportEvidenceMetadataResponse(UUID evidenceId, String contentType, long size,
        String mediaType, String contentUrl) { }
