package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.NegotiationStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class NegotiationResponseDTO {
    private UUID id;
    private UUID postId;
    private String postTitle;
    private UUID buyerId;
    private UUID sellerId;
    private BigDecimal offeredPrice;
    private NegotiationStatus status;
    private Instant createdAt;
    private Instant expiredAt;
}
