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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositServiceImpl implements DepositService {

    private final DepositRequestRepository depositRequestRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final com.secondlife.secondlife.service.payment.OrderBankPaymentService bankPayments;
    private final com.secondlife.secondlife.service.payment.SePayTransferLedger ledger;

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
        if (!"in".equalsIgnoreCase(webhookDTO.getTransferType())) return;
        if(webhookDTO.getAccountNumber()==null || !bankAccountNumber.trim().equals(webhookDTO.getAccountNumber().trim()))
            throw new com.secondlife.secondlife.exception.BadRequestException("SePay receiving bank account does not match configuration");
        String code=ledger.reference(webhookDTO);
        if(code==null) return;
        var initial=depositRequestRepository.findReferenceByCode(code);
        if(initial.isEmpty()) return;
        if(initial.get().getOrderId()!=null) {
            bankPayments.receive(initial.get().getOrderId(),webhookDTO,code);
            return;
        }

        var request=depositRequestRepository.findByIdForUpdate(initial.get().getId()).orElseThrow();
        if(!ledger.claim(webhookDTO,code)) return;
        walletService.deposit(request.getUser().getId(),webhookDTO.getTransferAmount(),request.getId());
        request.setReceivedAmount(request.getReceivedAmount().add(webhookDTO.getTransferAmount()));
        request.setWalletCreditedAmount(request.getWalletCreditedAmount().add(webhookDTO.getTransferAmount()));
        request.setStatus(DepositStatus.COMPLETED);request.setSepayTransactionId(webhookDTO.getId());
        depositRequestRepository.save(request);
    }
}
