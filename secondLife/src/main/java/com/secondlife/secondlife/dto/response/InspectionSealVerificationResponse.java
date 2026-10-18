package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InspectionSealVerificationResponse(
    String tamperSealId,
    UUID inspectionOrderId,
    UUID postId,
    String productTitle,
    String productImageUrl,
    String productModel,
    String productBrand,
    Integer conditionScore,
    String conditionGrade,
    String inspectionStatus,
    String inspectionCenterName,
    String inspectorName,
    Instant inspectedAt,
    String currentSealStatus, // INTACT, TAMPERED
    @JsonProperty("officialSecondLifeSeal")
    boolean isOfficialSecondLifeSeal,
    List<InspectionSealLogResponse> scanHistory
) {
}
