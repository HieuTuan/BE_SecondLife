package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_seal_logs")
@Getter
@Setter
@NoArgsConstructor
public class InspectionSealLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_order_id", nullable = false)
    private InspectionOrder inspectionOrder;

    @Column(name = "tamper_seal_id", length = 100, nullable = false)
    private String tamperSealId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanned_by")
    private User scannedBy;

    @Column(name = "scan_stage", length = 50, nullable = false)
    private String scanStage; // INITIAL_SEAL, CARRIER_HANDOVER, DELIVERED_BUYER

    @Column(name = "seal_condition", length = 30, nullable = false)
    private String sealCondition; // INTACT, TAMPERED

    @Column(name = "location_text", length = 255)
    private String locationText;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt = Instant.now();

    public InspectionSealLog(InspectionOrder inspectionOrder, String tamperSealId, User scannedBy,
                             String scanStage, String sealCondition, String locationText, String note) {
        this.inspectionOrder = inspectionOrder;
        this.tamperSealId = tamperSealId;
        this.scannedBy = scannedBy;
        this.scanStage = scanStage;
        this.sealCondition = sealCondition;
        this.locationText = locationText;
        this.note = note;
        this.scannedAt = Instant.now();
    }
}
