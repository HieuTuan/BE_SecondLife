package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record ReportEvidenceUploadResponse(UUID evidenceId, String evidenceUrl, String contentType, long size) { }
