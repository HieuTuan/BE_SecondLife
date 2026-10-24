package com.secondlife.secondlife.service;

import java.util.UUID;

public interface VideoVerificationService {
    void acquire(UUID ownerId, UUID uploadId);
}
