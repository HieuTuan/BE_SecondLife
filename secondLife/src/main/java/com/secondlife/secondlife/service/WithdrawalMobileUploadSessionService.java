package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.dto.response.WithdrawalMobileUploadSessionResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WithdrawalMobileUploadSessionService {

    public static final String PURPOSE_STAFF_RECEIPT = "STAFF_RECEIPT";
    public static final String PURPOSE_USER_DISPUTE = "USER_DISPUTE";
    public static final String PURPOSE_USER_EXCEPTION = "USER_EXCEPTION";

    private static final long DEFAULT_MAX_EVIDENCE_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String USER_DISPUTE_FOLDER = "secondlife/withdrawals/disputes";
    private static final String USER_EXCEPTION_FOLDER = "secondlife/withdrawals/exceptions";

    static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration COMPLETED_RETENTION = Duration.ofMinutes(10);

    private final WalletWithdrawalRepository walletWithdrawalRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final MediaService mediaService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.wallet.withdrawal.evidence.max-file-size-bytes:${app.media.max-file-size-bytes:10485760}}")
    private long maxEvidenceFileBytes = DEFAULT_MAX_EVIDENCE_FILE_BYTES;

    private final Map<String, UploadSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByTokenHash = new ConcurrentHashMap<>();

    public WithdrawalMobileUploadSessionService(
            WalletWithdrawalRepository walletWithdrawalRepository,
            UserRepository userRepository,
            WalletService walletService,
            MediaService mediaService
    ) {
        this.walletWithdrawalRepository = walletWithdrawalRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.mediaService = mediaService;
    }

    public long getMaxEvidenceFileBytes() {
        return maxEvidenceFileBytes > 0 ? maxEvidenceFileBytes : DEFAULT_MAX_EVIDENCE_FILE_BYTES;
    }

    void setMaxEvidenceFileBytes(long maxEvidenceFileBytes) {
        if (maxEvidenceFileBytes > 0) {
            this.maxEvidenceFileBytes = maxEvidenceFileBytes;
        }
    }

    @Transactional(readOnly = true)
    public WithdrawalMobileUploadSessionResponse createSession(UUID staffId, UUID withdrawalId) {
        pruneExpiredSessions();

        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu rút tiền với mã: " + withdrawalId));
        if (!"PROCESSING".equals(withdrawal.getStatus())) {
            throw new ConflictException("Chỉ có thể tạo mã QR tải biên lai khi lệnh rút đang ở trạng thái Đang xử lý.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản nhân viên."));
        if (withdrawal.getAcceptedBy() == null
                || (!withdrawal.getAcceptedBy().getId().equals(staffId) && !staff.hasRole("ADMIN"))) {
            throw new ConflictException("Chỉ nhân viên đang tiếp nhận lệnh rút hoặc quản trị viên mới có thể tải ảnh minh chứng.");
        }

        return registerSession(withdrawal, staffId, PURPOSE_STAFF_RECEIPT, 1);
    }

    @Transactional(readOnly = true)
    public WithdrawalMobileUploadSessionResponse createUserSession(
            UUID userId,
            UUID withdrawalId,
            String rawPurpose,
            Integer requestedMaxFiles
    ) {
        pruneExpiredSessions();

        String purpose = normalizeUserPurpose(rawPurpose);
        int maxFiles = requestedMaxFiles == null ? 3 : Math.max(1, Math.min(3, requestedMaxFiles));

        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu rút tiền với mã: " + withdrawalId));
        if (withdrawal.getUser() == null || !withdrawal.getUser().getId().equals(userId)) {
            throw new ConflictException("Bạn không có quyền tạo mã QR tải ảnh cho yêu cầu rút tiền này.");
        }

        validateUserWithdrawalEligibility(withdrawal, purpose);

        return registerSession(withdrawal, userId, purpose, maxFiles);
    }

    @Transactional(readOnly = true)
    public WithdrawalMobileUploadSessionResponse getSessionStatusForStaff(
            UUID staffId,
            UUID withdrawalId,
            String sessionId
    ) {
        pruneExpiredSessions();
        UploadSession session = sessionsById.get(sessionId);
        if (session == null
                || !session.withdrawalId.equals(withdrawalId)
                || !PURPOSE_STAFF_RECEIPT.equals(session.purpose)) {
            throw new NotFoundException("Phiên tải ảnh từ điện thoại không tồn tại hoặc đã hết hạn.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản nhân viên."));
        if (!session.actorId.equals(staffId) && !staff.hasRole("ADMIN")) {
            throw new ConflictException("Bạn không có quyền truy cập phiên tải ảnh này.");
        }

        expireIfNeeded(session);
        return toResponse(session, null);
    }

    @Transactional(readOnly = true)
    public WithdrawalMobileUploadSessionResponse getSessionStatusForUser(
            UUID userId,
            UUID withdrawalId,
            String sessionId
    ) {
        pruneExpiredSessions();
        UploadSession session = sessionsById.get(sessionId);
        if (session == null
                || !session.withdrawalId.equals(withdrawalId)
                || PURPOSE_STAFF_RECEIPT.equals(session.purpose)) {
            throw new NotFoundException("Phiên tải ảnh từ điện thoại không tồn tại hoặc đã hết hạn.");
        }

        if (!session.actorId.equals(userId)) {
            throw new ConflictException("Bạn không có quyền truy cập phiên tải ảnh này.");
        }

        expireIfNeeded(session);
        return toResponse(session, null);
    }

    @Transactional(readOnly = true)
    public WithdrawalMobileUploadSessionResponse getPublicSessionInfo(String rawToken) {
        pruneExpiredSessions();
        UploadSession session = resolveSessionByToken(rawToken);

        if ("PENDING".equals(session.status) && Instant.now().isAfter(session.expiresAt)) {
            session.status = "EXPIRED";
            sessionIdByTokenHash.remove(session.tokenHash);
            throw new BadRequestException("Mã QR tải ảnh đã hết hạn. Vui lòng tạo mã QR mới trên máy tính.");
        }

        if ("PENDING".equals(session.status)) {
            WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(session.withdrawalId).orElse(null);
            if (!isWithdrawalStillEligible(withdrawal, session)) {
                session.status = "EXPIRED";
                sessionIdByTokenHash.remove(session.tokenHash);
                throw new ConflictException("Lệnh rút tiền này không còn ở trạng thái cho phép tải ảnh minh chứng.");
            }
        }

        return toResponse(session, null);
    }

    @Transactional
    public WithdrawalMobileUploadSessionResponse uploadEvidenceWithToken(String rawToken, MultipartFile file) {
        pruneExpiredSessions();
        UploadSession session = resolveSessionByToken(rawToken);

        synchronized (session) {
            if ("UPLOADED".equals(session.status) || session.evidenceUrls.size() >= session.maxFiles) {
                throw new ConflictException(
                        PURPOSE_STAFF_RECEIPT.equals(session.purpose)
                                ? "Mã QR này đã được sử dụng để tải ảnh thành công. Vui lòng tạo mã mới trên máy tính nếu cần tải lại."
                                : "Phiên tải ảnh đã nhận đủ số lượng ảnh tối đa (" + session.maxFiles + " ảnh)."
                );
            }
            if (Instant.now().isAfter(session.expiresAt)) {
                session.status = "EXPIRED";
                sessionIdByTokenHash.remove(session.tokenHash);
                throw new BadRequestException("Mã QR tải ảnh đã hết hạn. Vui lòng tạo mã QR mới trên máy tính.");
            }

            if (PURPOSE_STAFF_RECEIPT.equals(session.purpose)) {
                WalletWithdrawalResponse saved = walletService.uploadWithdrawalEvidence(
                        session.actorId,
                        session.withdrawalId,
                        file
                );
                String url = saved.evidenceUrl();
                session.status = "UPLOADED";
                session.uploadedAt = Instant.now();
                session.evidenceUrl = url;
                if (url != null && !url.isBlank()) {
                    session.evidenceUrls.add(url);
                }
                return toResponse(session, null);
            }

            validateUploadedImageFile(file);
            WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(session.withdrawalId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu rút tiền."));
            if (withdrawal.getUser() == null || !withdrawal.getUser().getId().equals(session.actorId)) {
                throw new ConflictException("Yêu cầu rút tiền không khớp chủ tài khoản.");
            }
            validateUserWithdrawalEligibility(withdrawal, session.purpose);

            String folder = PURPOSE_USER_EXCEPTION.equals(session.purpose)
                    ? USER_EXCEPTION_FOLDER
                    : USER_DISPUTE_FOLDER;
            MediaUploadResponse uploaded = mediaService.uploadImage(file, folder);
            String url = uploaded != null ? uploaded.url() : null;
            if (url == null || url.isBlank()) {
                throw new BadRequestException("Không thể lưu ảnh minh chứng. Vui lòng thử lại.");
            }

            session.evidenceUrls.add(url);
            session.evidenceUrl = url;
            session.uploadedAt = Instant.now();
            if (session.evidenceUrls.size() >= session.maxFiles) {
                session.status = "UPLOADED";
            }
            return toResponse(session, null);
        }
    }

    private WithdrawalMobileUploadSessionResponse registerSession(
            WalletWithdrawal withdrawal,
            UUID actorId,
            String purpose,
            int maxFiles
    ) {
        // Revoke any previous pending session for the same withdrawal, actor, and purpose
        sessionsById.values().removeIf(existing -> {
            boolean match = existing.withdrawalId.equals(withdrawal.getId())
                    && existing.actorId.equals(actorId)
                    && existing.purpose.equals(purpose)
                    && !"UPLOADED".equals(existing.status);
            if (match && existing.tokenHash != null) {
                sessionIdByTokenHash.remove(existing.tokenHash);
            }
            return match;
        });

        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_TTL);

        String requestCode = withdrawal.getRequestCode() != null && !withdrawal.getRequestCode().isBlank()
                ? withdrawal.getRequestCode()
                : withdrawal.getId().toString().substring(0, 8).toUpperCase();
        String bankDisplay = withdrawal.getBankName() != null && !withdrawal.getBankName().isBlank()
                ? withdrawal.getBankName()
                : withdrawal.getBankCode();

        UploadSession session = new UploadSession(
                UUID.randomUUID().toString(),
                tokenHash,
                withdrawal.getId(),
                requestCode,
                purpose,
                maxFiles,
                actorId,
                withdrawal.getAmount(),
                bankDisplay,
                maskAccountNumber(withdrawal.getAccountNumber()),
                withdrawal.getAccountHolderName(),
                now,
                expiresAt
        );
        sessionsById.put(session.sessionId, session);
        sessionIdByTokenHash.put(tokenHash, session.sessionId);

        return toResponse(session, rawToken);
    }

    private static String normalizeUserPurpose(String rawPurpose) {
        if (rawPurpose == null || rawPurpose.isBlank()) {
            return PURPOSE_USER_DISPUTE;
        }
        String normalized = rawPurpose.trim().toUpperCase();
        if (PURPOSE_USER_DISPUTE.equals(normalized) || "DISPUTE".equals(normalized)) {
            return PURPOSE_USER_DISPUTE;
        }
        if (PURPOSE_USER_EXCEPTION.equals(normalized) || "EXCEPTION".equals(normalized)) {
            return PURPOSE_USER_EXCEPTION;
        }
        throw new BadRequestException("Mục đích tải ảnh minh chứng không hợp lệ.");
    }

    private static void validateUserWithdrawalEligibility(WalletWithdrawal withdrawal, String purpose) {
        if (PURPOSE_USER_DISPUTE.equals(purpose)) {
            if (!"TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus())) {
                throw new ConflictException("Chỉ có thể tải ảnh minh chứng báo cáo sự cố khi lệnh rút đang chờ đối soát 48 giờ.");
            }
            if (withdrawal.getDisputeDeadline() != null && Instant.now().isAfter(withdrawal.getDisputeDeadline())) {
                throw new ConflictException("Đã hết thời hạn báo cáo sự cố 48 giờ cho lệnh rút tiền này.");
            }
            return;
        }
        if (PURPOSE_USER_EXCEPTION.equals(purpose)) {
            if (!"COMPLETED".equals(withdrawal.getStatus())) {
                throw new ConflictException("Chỉ có thể tải ảnh minh chứng ngoại lệ khi lệnh rút tiền đã hoàn tất.");
            }
            if (withdrawal.getExceptionRequestedAt() != null) {
                throw new ConflictException("Lệnh rút tiền này đã có yêu cầu hỗ trợ ngoại lệ trước đó.");
            }
        }
    }

    private static boolean isWithdrawalStillEligible(WalletWithdrawal withdrawal, UploadSession session) {
        if (withdrawal == null) return false;
        if (PURPOSE_STAFF_RECEIPT.equals(session.purpose)) {
            return "PROCESSING".equals(withdrawal.getStatus());
        }
        if (withdrawal.getUser() == null || !withdrawal.getUser().getId().equals(session.actorId)) {
            return false;
        }
        if (PURPOSE_USER_DISPUTE.equals(session.purpose)) {
            return "TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus())
                    && (withdrawal.getDisputeDeadline() == null || !Instant.now().isAfter(withdrawal.getDisputeDeadline()));
        }
        if (PURPOSE_USER_EXCEPTION.equals(session.purpose)) {
            return "COMPLETED".equals(withdrawal.getStatus()) && withdrawal.getExceptionRequestedAt() == null;
        }
        return false;
    }

    private void validateUploadedImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Ảnh minh chứng đang trống.");
        }
        long maxBytes = getMaxEvidenceFileBytes();
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Ảnh minh chứng không được vượt quá " + formatFileSizeLabel(maxBytes) + ".");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BadRequestException("Chỉ chấp nhận ảnh định dạng JPEG, PNG hoặc WEBP.");
        }
    }

    private static String formatFileSizeLabel(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) {
            if (Math.abs(mb - Math.rint(mb)) < 1e-6) {
                return String.format(Locale.ROOT, "%.0f MB", mb);
            }
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        long kb = Math.max(1L, Math.round(bytes / 1024.0));
        return kb + " KB";
    }

    private void expireIfNeeded(UploadSession session) {
        if ("PENDING".equals(session.status) && Instant.now().isAfter(session.expiresAt)) {
            session.status = "EXPIRED";
            sessionIdByTokenHash.remove(session.tokenHash);
        }
    }

    private UploadSession resolveSessionByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Thiếu mã xác thực phiên tải ảnh.");
        }
        String tokenHash = sha256Hex(rawToken.trim());
        String sessionId = sessionIdByTokenHash.get(tokenHash);
        if (sessionId == null) {
            throw new NotFoundException("Mã QR tải ảnh không hợp lệ hoặc đã hết hạn.");
        }
        UploadSession session = sessionsById.get(sessionId);
        if (session == null) {
            sessionIdByTokenHash.remove(tokenHash);
            throw new NotFoundException("Mã QR tải ảnh không hợp lệ hoặc đã hết hạn.");
        }
        return session;
    }

    private void pruneExpiredSessions() {
        Instant now = Instant.now();
        sessionsById.entrySet().removeIf(entry -> {
            UploadSession s = entry.getValue();
            boolean shouldRemove;
            if ("UPLOADED".equals(s.status)) {
                Instant retentionLimit = (s.uploadedAt != null ? s.uploadedAt : s.expiresAt).plus(COMPLETED_RETENTION);
                shouldRemove = now.isAfter(retentionLimit);
            } else {
                shouldRemove = now.isAfter(s.expiresAt.plus(Duration.ofMinutes(2)));
            }
            if (shouldRemove && s.tokenHash != null) {
                sessionIdByTokenHash.remove(s.tokenHash);
            }
            return shouldRemove;
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return "—";
        String clean = accountNumber.trim();
        if (clean.length() <= 4) return clean;
        return "••••" + clean.substring(clean.length() - 4);
    }

    private WithdrawalMobileUploadSessionResponse toResponse(UploadSession session, String rawToken) {
        List<String> urlsSnapshot;
        synchronized (session) {
            urlsSnapshot = List.copyOf(session.evidenceUrls);
        }
        return new WithdrawalMobileUploadSessionResponse(
                session.sessionId,
                rawToken,
                session.withdrawalId,
                session.requestCode,
                session.purpose,
                session.maxFiles,
                getMaxEvidenceFileBytes(),
                session.amount,
                session.bankName,
                session.maskedAccountNumber,
                session.accountHolderName,
                session.status,
                session.expiresAt,
                session.uploadedAt,
                session.evidenceUrl,
                urlsSnapshot
        );
    }

    private static final class UploadSession {
        final String sessionId;
        final String tokenHash;
        final UUID withdrawalId;
        final String requestCode;
        final String purpose;
        final int maxFiles;
        final UUID actorId;
        final BigDecimal amount;
        final String bankName;
        final String maskedAccountNumber;
        final String accountHolderName;
        final Instant createdAt;
        final Instant expiresAt;
        final List<String> evidenceUrls = new ArrayList<>();
        volatile String status = "PENDING";
        volatile Instant uploadedAt;
        volatile String evidenceUrl;

        UploadSession(
                String sessionId,
                String tokenHash,
                UUID withdrawalId,
                String requestCode,
                String purpose,
                int maxFiles,
                UUID actorId,
                BigDecimal amount,
                String bankName,
                String maskedAccountNumber,
                String accountHolderName,
                Instant createdAt,
                Instant expiresAt
        ) {
            this.sessionId = sessionId;
            this.tokenHash = tokenHash;
            this.withdrawalId = withdrawalId;
            this.requestCode = requestCode;
            this.purpose = purpose;
            this.maxFiles = maxFiles;
            this.actorId = actorId;
            this.amount = amount;
            this.bankName = bankName;
            this.maskedAccountNumber = maskedAccountNumber;
            this.accountHolderName = accountHolderName;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }
}
