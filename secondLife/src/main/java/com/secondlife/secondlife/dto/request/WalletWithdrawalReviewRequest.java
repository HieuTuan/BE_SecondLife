package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record WalletWithdrawalReviewRequest(
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    String action,
    String adminNote,
    String evidenceUrl,
    String disputeType,
    List<String> evidenceUrls,
    String disputeResolutionOutcome,
    UUID targetStaffId
) {
    public WalletWithdrawalReviewRequest(String action, String adminNote) {
        this(action, adminNote, null, null, List.of(), null, null);
    }

    public WalletWithdrawalReviewRequest(String action, String adminNote, String evidenceUrl,
                                         String disputeType, List<String> evidenceUrls) {
        this(action, adminNote, evidenceUrl, disputeType, evidenceUrls, null, null);
    }
}
