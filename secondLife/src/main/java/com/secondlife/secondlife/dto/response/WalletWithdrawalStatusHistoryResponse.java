package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WalletWithdrawalStatusHistoryResponse(String oldStatus, String newStatus, UUID actorId,
                                                    String action, String reason, Instant createdAt) { }
