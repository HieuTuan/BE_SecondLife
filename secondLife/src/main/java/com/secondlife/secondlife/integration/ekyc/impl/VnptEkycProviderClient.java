package com.secondlife.secondlife.integration.ekyc.impl;

import com.secondlife.secondlife.dto.request.EkycRequest;
import com.secondlife.secondlife.dto.response.EkycResult;
import com.secondlife.secondlife.dto.response.VnptResults;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.integration.ekyc.EkycProviderClient;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptApiException;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptAuthenticationException;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptCompareException;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptEkycOrchestrator;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptLivenessException;
import com.secondlife.secondlife.integration.ekyc.vnpt.VnptOcrException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptEkycProviderClient implements EkycProviderClient {
    private static final String PROVIDER_NAME = "VNPT_EKYC";

    private final VnptEkycOrchestrator orchestrator;
    private final RestTemplate restTemplate;
    private final String cloudName;
    private final long maxImageBytes;

    public VnptEkycProviderClient(VnptEkycOrchestrator orchestrator,
                                  @Qualifier("vnptRestTemplate") RestTemplate restTemplate,
                                  @Value("${app.cloudinary.cloud-name}") String cloudName,
                                  @Value("${app.ekyc.vnpt.max-image-bytes}") long maxImageBytes) {
        this.orchestrator = orchestrator;
        this.restTemplate = restTemplate;
        this.cloudName = cloudName;
        this.maxImageBytes = maxImageBytes;
    }

    @Override
    public String getProviderName() { return PROVIDER_NAME; }

    @Override
    public EkycResult verify(EkycRequest request) {
        String reference = "VNPT-" + UUID.randomUUID().toString().substring(0, 8);
        if (request.documentType() == VerificationType.BUSINESS_LICENSE) {
            return EkycResult.fail(ReasonCode.UNSUPPORTED_DOCUMENT, PROVIDER_NAME, reference,
                    null, null, null,
                    "Loại giấy tờ Giấy phép kinh doanh chưa hỗ trợ xác thực eKYC tự động. Vui lòng chọn CCCD/CMND hoặc Hộ chiếu.");
        }
        if (request.selfieUrl() == null || request.selfieUrl().isBlank()) {
            return EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW, PROVIDER_NAME,
                    reference, "Thiếu ảnh chân dung (selfie). Vui lòng chụp hoặc tải lên ảnh chân dung rõ mặt.");
        }
        if (request.clientSession() == null || request.clientSession().isBlank()
                || request.token() == null || request.token().isBlank()) {
            return EkycResult.uncertain(ReasonCode.EKYC_CONTEXT_MISSING, PROVIDER_NAME,
                    reference, "Thiếu phiên xác thực thiết bị VNPT. Vui lòng tải lại trang và gửi lại hồ sơ.");
        }
        try {
            byte[] front = downloadImage(request.documentFrontUrl());
            byte[] back = downloadImage(request.documentBackUrl());
            byte[] selfie = downloadImage(request.selfieUrl());
            int type = request.documentType() == VerificationType.PASSPORT ? 5 : -1;
            String clientSession = normalizeClientSession(request.clientSession());
            String token = request.token() != null ? request.token().trim() : null;
            VnptResults.Verification result = orchestrator.verify(front, back, selfie,
                    clientSession, token, type);

            Double faceScore = normalizedScore(result.faceCompare() != null && result.faceCompare().object() != null
                    ? result.faceCompare().object().prob() : null);
            Double livenessScore = null;
            if (result.faceLiveness() != null && result.faceLiveness().object() != null) {
                livenessScore = normalizedScore(result.faceLiveness().object().livenessProb());
                if (livenessScore == null && "true".equalsIgnoreCase(result.faceLiveness().object().liveness())) {
                    livenessScore = 0.98;
                } else if (livenessScore == null && "false".equalsIgnoreCase(result.faceLiveness().object().liveness())) {
                    livenessScore = 0.15;
                }
            }
            Double docScore = null;
            if (result.cardLiveness() != null && result.cardLiveness().object() != null) {
                var cardObj = result.cardLiveness().object();
                if (cardObj.fakeLivenessProb() != null) {
                    docScore = 1.0 - normalizedScore(cardObj.fakeLivenessProb());
                } else if (Boolean.TRUE.equals(cardObj.fakeLiveness()) || Boolean.TRUE.equals(cardObj.fakePrintPhoto())) {
                    docScore = 0.25;
                } else if ("true".equalsIgnoreCase(cardObj.liveness())) {
                    docScore = 0.96;
                } else {
                    docScore = 0.85;
                }
            }

            String extractedId = result.ocr().object().id();
            if (request.documentNumber() != null && extractedId != null
                    && !request.documentNumber().replaceAll("\\s+", "")
                    .equalsIgnoreCase(extractedId.replaceAll("\\s+", ""))) {
                return EkycResult.fail(ReasonCode.CONFIRMED_IDENTITY_MISMATCH, PROVIDER_NAME, reference,
                        faceScore, livenessScore, docScore,
                        buildIdentityMismatchMessage(request.documentNumber(), extractedId));
            }
            Integer detectedType = result.ocr().object().typeId();
            if (detectedType != null && ((type == 5 && detectedType != 2)
                    || (type == -1 && detectedType != 0 && detectedType != 1
                    && detectedType != 5 && detectedType != 6))) {
                return EkycResult.fail(ReasonCode.UNSUPPORTED_DOCUMENT, PROVIDER_NAME, reference,
                        faceScore, livenessScore, docScore,
                        "Ảnh tải lên không đúng loại giấy tờ bạn đã chọn ("
                                + friendlyVerificationType(request.documentType())
                                + "). Vui lòng chọn đúng loại giấy tờ hoặc tải lại ảnh phù hợp.");
            }
            if (!result.verified()) {
                return switch (result.reasonCode()) {
                    case "CARD_LIVENESS_FAILED" -> EkycResult.fail(ReasonCode.DOCUMENT_SUSPECTED_FAKE,
                            PROVIDER_NAME, reference, faceScore, livenessScore, docScore != null ? docScore : 0.20,
                            "Kiểm tra phôi giấy tờ thật không đạt (" + cardFailureDetail(result.cardLiveness())
                                    + "). Vui lòng chụp trực tiếp thẻ gốc, không chụp qua màn hình hoặc bản in.");
                    case "FACE_LIVENESS_FAILED" -> EkycResult.fail(ReasonCode.LIVENESS_FAILED,
                            PROVIDER_NAME, reference, faceScore, livenessScore != null ? livenessScore : 0.15, docScore,
                            "Kiểm tra thực thể sống khuôn mặt không đạt (" + faceFailureDetail(result.faceLiveness())
                                    + "). Vui lòng chụp ảnh chân dung trực tiếp rõ nét.");
                    case "FACE_MISMATCH" -> EkycResult.fail(ReasonCode.FACE_MISMATCH,
                            PROVIDER_NAME, reference, faceScore != null ? faceScore : 0.30, livenessScore, docScore,
                            buildFaceMismatchMessage(result.faceCompare(), faceScore));
                    case "FACE_MASKED" -> EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW,
                            PROVIDER_NAME, reference, faceScore, livenessScore, docScore,
                            "Khuôn mặt trong ảnh chân dung bị che khuất (khẩu trang/kính râm/vật cản). Vui lòng chụp lại ảnh rõ mặt.");
                    default -> EkycResult.uncertain(ReasonCode.OCR_LOW_CONFIDENCE,
                            PROVIDER_NAME, reference, faceScore, livenessScore, docScore,
                            "VNPT không trích xuất được đầy đủ số giấy tờ từ ảnh mặt trước. Vui lòng chụp lại ảnh rõ nét, đủ sáng và không lóa.");
                };
            }
            List<String> generalWarnings = result.ocr().object().generalWarning();
            if (!generalWarnings.isEmpty()) {
                if (isImageQualityWarnings(generalWarnings)) {
                    return EkycResult.uncertain(ReasonCode.DOCUMENT_NOT_FULLY_VISIBLE,
                            PROVIDER_NAME, reference, faceScore, livenessScore, docScore,
                            buildImageWarningMessage(generalWarnings));
                }
                return EkycResult.uncertain(ReasonCode.DOCUMENT_DATA_INCONSISTENCY,
                        PROVIDER_NAME, reference, faceScore, livenessScore, docScore,
                        "Giấy tờ có cảnh báo bất thường từ VNPT ("
                                + formatWarnings(generalWarnings)
                                + "). Hồ sơ được chuyển quản trị viên kiểm tra.");
            }
            return EkycResult.pass(PROVIDER_NAME, reference,
                    faceScore != null ? faceScore : 0.95,
                    livenessScore != null ? livenessScore : 0.98,
                    docScore != null ? docScore : 0.95);
        } catch (InvalidImageException | BadRequestException ex) {
            String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Không thể đọc hoặc tải ảnh xác thực. Vui lòng tải lại ảnh JPEG/PNG rõ nét.";
            return EkycResult.uncertain(ReasonCode.IMAGE_NOT_ACCESSIBLE,
                    PROVIDER_NAME, reference, null, null, null, msg);
        } catch (VnptAuthenticationException ex) {
            log.warn("VNPT authentication failed: endpoint={}, status={}, ref={}",
                    ex.getEndpoint(), ex.getUpstreamStatus(), reference);
            return EkycResult.providerError(ReasonCode.PROVIDER_AUTH_FAILED,
                    PROVIDER_NAME, reference, "VNPT authentication failed");
        } catch (VnptApiException ex) {
            log.warn("VNPT API failed: endpoint={}, status={}, code={}, ref={}",
                    ex.getEndpoint(), ex.getUpstreamStatus(), ex.getProviderCode(), reference);
            if (ex.getUpstreamStatus() >= 400 && ex.getUpstreamStatus() < 500) {
                EkycResult userFixable = mapUserFixableRejection(ex, reference);
                if (userFixable != null) {
                    return userFixable;
                }
                return EkycResult.providerError(ReasonCode.PROVIDER_REQUEST_REJECTED, PROVIDER_NAME, reference,
                        buildProviderErrorMessage(ex, true));
            }
            ReasonCode reason = ex.getUpstreamStatus() == 504
                    ? ReasonCode.PROVIDER_TIMEOUT
                    : ReasonCode.PROVIDER_UNAVAILABLE;
            return EkycResult.providerError(reason, PROVIDER_NAME, reference,
                    buildProviderErrorMessage(ex, false));
        } catch (ResourceAccessException ex) {
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE,
                    PROVIDER_NAME, reference, "Không thể tải ảnh eKYC từ kho lưu trữ để gửi sang VNPT");
        }
    }

    private EkycResult mapUserFixableRejection(VnptApiException ex, String reference) {
        String code = ex.getProviderCode();
        if (code == null || !code.startsWith("IDG-") || "IDG-00000000".equals(code)) {
            return null;
        }
        String detail = ex.getProviderDetail();
        String detailSuffix = detail != null && !detail.isBlank() ? ": " + detail : "";
        if ("IDG-00010003".equals(code) || ex instanceof VnptCompareException) {
            String message = "IDG-00010003".equals(code)
                    ? "Không tìm thấy khuôn mặt rõ nét trên ảnh mặt trước CCCD hoặc ảnh chân dung (mã IDG-00010003"
                    + (detail != null && !detail.isBlank() ? " - " + detail : "")
                    + "). Vui lòng chụp và cập nhật lại ảnh rõ mặt."
                    : "Không thể đối chiếu khuôn mặt giữa CCCD và ảnh chân dung (mã " + code
                    + (detail != null && !detail.isBlank() ? " - " + detail : "")
                    + "). Vui lòng chụp và cập nhật lại ảnh.";
            return EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW, PROVIDER_NAME, reference, message);
        }
        if (ex instanceof VnptOcrException) {
            return EkycResult.uncertain(ReasonCode.DOCUMENT_NOT_FULLY_VISIBLE, PROVIDER_NAME, reference,
                    "Ảnh CCCD chưa hợp lệ hoặc không đọc được thông tin giấy tờ (mã " + code + detailSuffix
                            + "). Vui lòng chụp lại ảnh nằm ngang, đủ sáng và rõ 4 góc thẻ.");
        }
        if (ex instanceof VnptLivenessException) {
            return EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW, PROVIDER_NAME, reference,
                    "Ảnh CCCD hoặc ảnh chân dung chưa đạt chất lượng kiểm tra (mã " + code + detailSuffix
                            + "). Vui lòng chụp và cập nhật lại ảnh rõ nét.");
        }
        return null;
    }

    private String buildProviderErrorMessage(VnptApiException ex, boolean rejected) {
        String step = friendlyEndpointLabel(ex.getEndpoint());
        StringBuilder sb = new StringBuilder(rejected ? "VNPT từ chối ở bước " : "Lỗi cổng VNPT ở bước ");
        sb.append(step).append(" (HTTP ").append(ex.getUpstreamStatus());
        String code = ex.getProviderCode();
        if (code != null && !code.isBlank()
                && !"UNAVAILABLE".equals(code)
                && !"REJECTED_BY_PROVIDER".equals(code)) {
            sb.append(", mã ").append(code);
        }
        sb.append(")");
        String detail = ex.getProviderDetail();
        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }

    private String friendlyEndpointLabel(String endpoint) {
        if (endpoint == null) {
            return "xác thực eKYC";
        }
        if (endpoint.contains("/file-service/v1/addFile")) {
            return "tải ảnh lên VNPT";
        }
        if (endpoint.contains("/ocr/id")) {
            return "đọc OCR giấy tờ CCCD";
        }
        if (endpoint.contains("/card/liveness")) {
            return "kiểm tra phôi CCCD thật";
        }
        if (endpoint.contains("/face/liveness")) {
            return "kiểm tra thực thể sống chân dung";
        }
        if (endpoint.contains("/face/mask")) {
            return "kiểm tra che khuôn mặt";
        }
        if (endpoint.contains("/face/compare")) {
            return "đối chiếu khuôn mặt";
        }
        return endpoint;
    }

    private String normalizeClientSession(String session) {
        return VnptEkycOrchestrator.normalizeClientSession(session);
    }

    private String buildIdentityMismatchMessage(String inputNumber, String ocrNumber) {
        String normalizedInput = inputNumber == null ? "" : inputNumber.replaceAll("\\s+", "");
        String normalizedOcr = ocrNumber == null ? "" : ocrNumber.replaceAll("\\s+", "");
        String hint;
        if (normalizedInput.length() != normalizedOcr.length()) {
            hint = "bạn nhập " + normalizedInput.length() + " ký tự, nhưng ảnh mặt trước đọc được "
                    + normalizedOcr.length() + " ký tự";
        } else {
            String inputTail = tail4(normalizedInput);
            String ocrTail = tail4(normalizedOcr);
            if (!inputTail.equalsIgnoreCase(ocrTail)) {
                hint = "4 ký tự cuối bạn nhập là " + inputTail + ", trên ảnh đọc được là " + ocrTail;
            } else {
                int diffs = 0;
                for (int i = 0; i < normalizedInput.length(); i++) {
                    if (Character.toUpperCase(normalizedInput.charAt(i)) != Character.toUpperCase(normalizedOcr.charAt(i))) {
                        diffs++;
                    }
                }
                hint = "sai khác " + diffs + " ký tự so với số VNPT đọc được trên ảnh";
            }
        }
        return "Số giấy tờ bạn nhập không khớp với số trên ảnh mặt trước CCCD/Hộ chiếu (" + hint
                + "). Vui lòng kiểm tra lại số đã nhập hoặc chụp lại ảnh mặt trước sắc nét hơn.";
    }

    private String tail4(String value) {
        if (value == null || value.isEmpty()) {
            return "—";
        }
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    private boolean isImageQualityWarnings(List<String> warnings) {
        return warnings.stream().allMatch(this::isImageQualityWarning);
    }

    private boolean isImageQualityWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            return false;
        }
        String normalized = warning.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("mat_goc")
                || normalized.contains("khong_ro")
                || normalized.contains("mo")
                || normalized.contains("choi")
                || normalized.contains("loa")
                || normalized.contains("input_khong_hop_le")
                || normalized.contains("corner")
                || normalized.contains("blur")
                || normalized.contains("glare");
    }

    private String buildImageWarningMessage(List<String> warnings) {
        return "Ảnh CCCD chưa đạt chuẩn hiển thị theo cảnh báo VNPT ("
                + formatWarnings(warnings)
                + "). Vui lòng chụp lại ảnh thẻ nằm ngang, rõ đủ 4 góc và không bị mờ/lóa.";
    }

    private String formatWarnings(List<String> warnings) {
        return warnings.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(this::translateVnptWarning)
                .collect(Collectors.joining("; "));
    }

    private String translateVnptWarning(String warning) {
        String normalized = warning.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "anh_dau_vao_mat_goc" -> "ảnh đầu vào bị mất góc";
            case "anh_dau_vao_khong_ro" -> "ảnh đầu vào không rõ nét";
            case "anh_dau_vao_mo" -> "ảnh đầu vào bị mờ";
            case "anh_dau_vao_choi_sang", "anh_dau_vao_loa_sang" -> "ảnh đầu vào bị lóa sáng";
            case "input_khong_hop_le" -> "ảnh đầu vào không hợp lệ";
            default -> warning.trim();
        };
    }

    private String friendlyVerificationType(VerificationType type) {
        if (type == null) {
            return "CCCD / CMND";
        }
        return switch (type) {
            case CITIZEN_ID -> "CCCD / CMND";
            case PASSPORT -> "Hộ chiếu";
            case BUSINESS_LICENSE -> "Giấy phép kinh doanh";
        };
    }

    private String cardFailureDetail(VnptResults.Envelope<VnptResults.CardLiveness> cardEnv) {
        if (cardEnv == null || cardEnv.object() == null) {
            return "không kiểm tra được phôi thẻ";
        }
        var card = cardEnv.object();
        if (Boolean.TRUE.equals(card.fakePrintPhoto())) {
            return "phát hiện ảnh chụp lại từ bản in / giấy tờ photo";
        }
        if (Boolean.TRUE.equals(card.fakeLiveness())) {
            return "nghi vấn chụp qua màn hình hoặc ảnh không phải thẻ thật";
        }
        if (card.livenessMsg() != null && !card.livenessMsg().isBlank()) {
            return card.livenessMsg();
        }
        return "trạng thái liveness=" + card.liveness();
    }

    private String faceFailureDetail(VnptResults.Envelope<VnptResults.FaceLiveness> faceEnv) {
        if (faceEnv == null || faceEnv.object() == null) {
            return "không kiểm tra được thực thể sống";
        }
        var face = faceEnv.object();
        var multi = face.multipleFacesDetails();
        if (multi != null && (Boolean.TRUE.equals(multi.multipleFace1()) || Boolean.TRUE.equals(multi.multipleFace2()))) {
            return "phát hiện nhiều khuôn mặt trong ảnh chân dung";
        }
        if (face.livenessMsg() != null && !face.livenessMsg().isBlank()) {
            return face.livenessMsg();
        }
        return "ảnh chân dung nghi vấn chụp qua màn hình hoặc ảnh tĩnh";
    }

    private String buildFaceMismatchMessage(VnptResults.Envelope<VnptResults.FaceCompare> compareEnv, Double faceScore) {
        if (compareEnv != null && compareEnv.object() != null) {
            var compare = compareEnv.object();
            var multi = compare.multipleFacesDetails();
            if (Boolean.TRUE.equals(compare.multipleFaces())
                    || (multi != null && (Boolean.TRUE.equals(multi.multipleFace1()) || Boolean.TRUE.equals(multi.multipleFace2())))) {
                return "Phát hiện nhiều khuôn mặt trong ảnh khi đối chiếu. Vui lòng chỉ chụp 1 người trong khung hình.";
            }
        }
        String scoreText = faceScore != null ? " (độ tương đồng " + Math.round(faceScore * 100) + "%)" : "";
        return "Khuôn mặt trong ảnh chân dung không trùng khớp với ảnh trên CCCD/Hộ chiếu" + scoreText
                + ". Vui lòng dùng đúng ảnh chân dung của chủ giấy tờ.";
    }

    private byte[] downloadImage(String imageUrl) {
        URI uri;
        try { uri = URI.create(imageUrl); }
        catch (RuntimeException ex) { throw new InvalidImageException("URL ảnh eKYC không hợp lệ."); }
        String allowedPath = "/" + cloudName + "/image/upload/";
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null
                || !uri.getPath().startsWith(allowedPath)) {
            throw new InvalidImageException("Ảnh eKYC phải được tải lên từ kho lưu trữ hợp lệ của hệ thống.");
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
            byte[] bytes = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            if (!response.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0
                    || bytes.length > maxImageBytes || type == null
                    || (!MediaType.IMAGE_JPEG.isCompatibleWith(type)
                    && !MediaType.IMAGE_PNG.isCompatibleWith(type))) {
                throw new InvalidImageException("Ảnh eKYC không hợp lệ (chỉ hỗ trợ JPEG/PNG và không vượt quá dung lượng cho phép).");
            }
            return bytes;
        } catch (RestClientResponseException ex) {
            throw new InvalidImageException("Không thể tải ảnh eKYC từ kho lưu trữ. Vui lòng tải lại ảnh mới.");
        }
    }

    private Double normalizedScore(Double probability) {
        if (probability == null || !Double.isFinite(probability) || probability < 0) return null;
        return probability > 1 ? probability / 100 : probability;
    }

    private static class InvalidImageException extends RuntimeException {
        private InvalidImageException(String message) {
            super(message);
        }
    }
}