package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record InspectionAppealRequest(
    @NotBlank(message = "Lý do khiếu nại không được để trống")
    String reason,
    List<String> evidenceUrls,
    List<UUID> contestedItemIds,
    String note
) {
}

