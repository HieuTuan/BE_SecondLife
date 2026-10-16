package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.InspectionSealLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionSealLogResponse {
    private UUID id;
    private String tamperSealId;
    private UUID scannedBy;
    private String scannedByName;
    private String scanStage;
    private String sealCondition;
    private String locationText;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String note;
    private Instant scannedAt;

    public static InspectionSealLogResponse from(InspectionSealLog log) {
        if (log == null) return null;
        String name = null;
        if (log.getScannedBy() != null) {
            if (log.getScannedBy().getProfile() != null && log.getScannedBy().getProfile().getFullName() != null) {
                name = log.getScannedBy().getProfile().getFullName();
            } else {
                name = log.getScannedBy().getEmail();
            }
        }
        return InspectionSealLogResponse.builder()
                .id(log.getId())
                .tamperSealId(log.getTamperSealId())
                .scannedBy(log.getScannedBy() != null ? log.getScannedBy().getId() : null)
                .scannedByName(name)
                .scanStage(log.getScanStage())
                .sealCondition(log.getSealCondition())
                .locationText(log.getLocationText())
                .latitude(log.getLatitude())
                .longitude(log.getLongitude())
                .note(log.getNote())
                .scannedAt(log.getScannedAt())
                .build();
    }
}
