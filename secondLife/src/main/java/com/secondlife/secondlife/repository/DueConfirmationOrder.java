package com.secondlife.secondlife.repository;
import java.time.Instant;
import java.util.UUID;
public interface DueConfirmationOrder {
    UUID getId();
    Instant getDeadline();
}
