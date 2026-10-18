package com.secondlife.secondlife.service;

import java.time.Instant;
import java.util.UUID;

public interface OrderAutoReleaseService {
    Instant dueCutoff();

    boolean releaseIfDue(UUID id);

}
