package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record WalletWithdrawalReviewRequest(
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    String action,
    String adminNote,
    String evidenceUrl,
    String disputeType,
    List<String> evidenceUrls
) {
    public WalletWithdrawalReviewRequest(String action, String adminNote) {
        this(action, adminNote, null, null, List.of());
    }
}
