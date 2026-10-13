package com.secondlife.secondlife.repository;

import java.time.Instant;
import java.util.UUID;

public interface DueSettlementOrder {
    UUID getId();
    Instant getDeliveredAt();
}
