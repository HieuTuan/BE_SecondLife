package com.secondlife.secondlife.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="shipment_events") @Getter @Setter @NoArgsConstructor
public class ShipmentEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID shipmentId;
    @Column(nullable=false) private String eventKey;
    @Column(nullable=false) private String type;
    private String status;
    @Column(nullable=false) private Instant occurredAt;
    @Column(columnDefinition="TEXT") private String reason;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
}
