package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.enums.VerificationStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.SellerVerificationMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.SellerVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerVerificationServiceImpl implements SellerVerificationService {

    private final SellerVerificationRepository sellerVerificationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SellerVerificationMapper sellerVerificationMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public SellerVerificationResponse submitVerification(UUID userId, SellerVerificationRequest request) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.hasRole(RoleCode.SELLER.name())) {
            throw new ConflictException("User already has SELLER role");
        }

        boolean hasPending = sellerVerificationRepository.existsByUserIdAndStatus(userId, VerificationStatus.PENDING);
        if (hasPending) {
            throw new ConflictException("A seller verification request is already pending review");
        }

        SellerVerification verification = new SellerVerification(
                user,
                request.verificationType(),
                request.documentNumber().trim(),
                request.documentFrontUrl().trim(),
                request.documentBackUrl().trim()
        );

        SellerVerification saved = sellerVerificationRepository.save(verification);
        log.info("Seller verification submitted by user: {}", user.getEmail());
        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getCurrentVerification(UUID userId) {
        SellerVerification verification = sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                .orElseThrow(() -> new NotFoundException("No seller verification request found for user"));
        return sellerVerificationMapper.toResponse(verification);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SellerVerificationResponse> getAdminVerifications(VerificationStatus status, Pageable pageable) {
        Page<SellerVerification> page = (status != null)
                ? sellerVerificationRepository.findByStatusWithDetails(status, pageable)
                : sellerVerificationRepository.findAllWithDetails(pageable);
        Page<SellerVerificationResponse> dtoPage = page.map(sellerVerificationMapper::toResponse);
        return PageResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getAdminVerificationById(UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Seller verification not found with id: " + verificationId));
        return sellerVerificationMapper.toResponse(verification);
    }

    @Override
    @Transactional
    public SellerVerificationResponse approveVerification(UUID adminId, UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Seller verification not found with id: " + verificationId));

        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Only PENDING verification requests can be approved. Current status: " + verification.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin user not found with id: " + adminId));

        Role sellerRole = roleRepository.findByCodeWithPermissions(RoleCode.SELLER.name())
                .orElseThrow(() -> new IllegalStateException("SELLER role not found"));

        User targetUser = verification.getUser();

        // Atomically update verification status and add SELLER role
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(null);

        if (!targetUser.hasRole(RoleCode.SELLER.name())) {
            targetUser.addRole(sellerRole);
            userRepository.save(targetUser);
        }

        SellerVerification saved = sellerVerificationRepository.save(verification);
        log.info("Admin {} APPROVED seller verification {} for user {}", admin.getEmail(), verificationId, targetUser.getEmail());

        notificationService.sendSellerVerificationApproved(targetUser);

        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SellerVerificationResponse rejectVerification(UUID adminId, UUID verificationId, SellerVerificationReviewRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Seller verification not found with id: " + verificationId));

        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Only PENDING verification requests can be rejected. Current status: " + verification.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin user not found with id: " + adminId));

        verification.setStatus(VerificationStatus.REJECTED);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(request.rejectionReason().trim());

        SellerVerification saved = sellerVerificationRepository.save(verification);
        log.info("Admin {} REJECTED seller verification {} for user {}", admin.getEmail(), verificationId, verification.getUser().getEmail());

        notificationService.sendSellerVerificationRejected(verification.getUser(), request.rejectionReason().trim());

        return sellerVerificationMapper.toResponse(saved);
    }
}
