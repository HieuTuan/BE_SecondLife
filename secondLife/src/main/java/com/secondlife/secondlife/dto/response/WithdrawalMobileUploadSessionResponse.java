package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WithdrawalMobileUploadSessionResponse(
        String sessionId,
        String token,
        UUID withdrawalId,
        String requestCode,
        String purpose,
        int maxFiles,
        long maxFileSizeBytes,
        BigDecimal amount,
        String bankName,
        String maskedAccountNumber,
        String accountHolderName,
        String status,
        Instant expiresAt,
        Instant uploadedAt,
        String evidenceUrl,
        List<String> evidenceUrls
) {
}
