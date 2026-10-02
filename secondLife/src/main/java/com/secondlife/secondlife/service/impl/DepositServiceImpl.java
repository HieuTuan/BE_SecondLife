package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.DepositCreateRequestDTO;
import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.dto.response.DepositResponseDTO;
import com.secondlife.secondlife.entity.DepositRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.DepositStatus;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.DepositRequestRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.DepositService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositServiceImpl implements DepositService {

    private final DepositRequestRepository depositRequestRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    @org.springframework.beans.factory.annotation.Value("${app.sepay.bank-account-name}")
    private String bankAccountName;

    @org.springframework.beans.factory.annotation.Value("${app.sepay.bank-account-number}")
    private String bankAccountNumber;

    @org.springframework.beans.factory.annotation.Value("${app.sepay.bank-name}")
    private String bankName;

    @Override
    @Transactional
    public DepositResponseDTO createDepositRequest(UUID userId, DepositCreateRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        DepositRequest depositRequest = new DepositRequest();
        depositRequest.setUser(user);
        depositRequest.setAmount(requestDTO.getAmount());

        // Generate a unique 6-digit code with prefix "SL"
        String code;
        SecureRandom random = new SecureRandom();
        do {
            int num = 100000 + random.nextInt(900000);
            code = "SCL" + num;
        } while (depositRequestRepository.existsByCode(code));

        depositRequest.setCode(code);
        depositRequestRepository.save(depositRequest);

        DepositResponseDTO response = new DepositResponseDTO();
        response.setId(depositRequest.getId());
        response.setAmount(depositRequest.getAmount());
        response.setCode(depositRequest.getCode());
        response.setStatus(depositRequest.getStatus());
        response.setBankAccountName(bankAccountName);
        response.setBankAccountNumber(bankAccountNumber);
        response.setBankName(bankName);
        response.setCreatedAt(depositRequest.getCreatedAt());

        return response;
    }

    @Override
    @Transactional
    public void processSepayWebhook(SePayWebhookDTO webhookDTO) {
        // Prevent duplicate processing
        if (depositRequestRepository.existsBySepayTransactionId(webhookDTO.getId())) {
            log.info("Transaction {} already processed", webhookDTO.getId());
            return;
        }

        if (!"in".equalsIgnoreCase(webhookDTO.getTransferType())) {
            log.info("Transaction {} is not 'in' type", webhookDTO.getId());
            return;
        }

        // SePay extracts the matching code, e.g., SL123456
        String code = webhookDTO.getCode();
        if (code == null || code.isEmpty()) {
            log.info("No code found in transaction {}", webhookDTO.getId());
            return;
        }

        Optional<DepositRequest> optionalDeposit = depositRequestRepository.findByCode(code);
        if (optionalDeposit.isEmpty()) {
            log.info("Deposit request with code {} not found", code);
            return;
        }

        DepositRequest depositRequest = optionalDeposit.get();

        if (depositRequest.getStatus() == DepositStatus.COMPLETED) {
            log.info("Deposit request {} already completed", code);
            return;
        }

        // Call WalletService to deposit the actual amount received
        walletService.deposit(
                depositRequest.getUser().getId(),
                webhookDTO.getTransferAmount(),
                depositRequest.getId());

        depositRequest.setStatus(DepositStatus.COMPLETED);
        depositRequest.setSepayTransactionId(webhookDTO.getId());
        depositRequestRepository.save(depositRequest);
        log.info("Successfully processed deposit {} for user {}", code, depositRequest.getUser().getId());
    }
}
