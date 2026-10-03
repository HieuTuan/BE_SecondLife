package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.DepositCreateRequestDTO;
import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.dto.response.DepositResponseDTO;

import java.util.UUID;

public interface DepositService {
    DepositResponseDTO createDepositRequest(UUID userId, DepositCreateRequestDTO requestDTO);
    void processSepayWebhook(SePayWebhookDTO webhookDTO);
}
