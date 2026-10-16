package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionSealVerificationResponse {
    private String tamperSealId;
    private UUID inspectionOrderId;
    private UUID postId;
    private String productTitle;
    private String productImageUrl;
    private String productModel;
    private String productBrand;
    private Integer conditionScore;
    private String conditionGrade;
    private String inspectionStatus;
    private String inspectionCenterName;
    private String inspectorName;
    private Instant inspectedAt;
    private String currentSealStatus; // INTACT, TAMPERED
    private boolean isOfficialSecondLifeSeal;
    private List<InspectionSealLogResponse> scanHistory;
}
