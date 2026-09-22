package com.secondlife.secondlife.enums;

public enum ReasonCode {
    // Hard eKYC Failures
    DOCUMENT_EXPIRED("Giấy tờ đã hết hạn sử dụng"),
    DOCUMENT_SUSPECTED_FAKE("Nghi vấn giấy tờ giả mạo hoặc có dấu hiệu can thiệp"),
    FACE_MISMATCH("Khuôn mặt không khớp với ảnh trên giấy tờ tùy thân"),
    LIVENESS_FAILED("Kiểm tra thực thể sống (liveness check) không thành công"),
    UNSUPPORTED_DOCUMENT("Loại giấy tờ không được hỗ trợ bởi hệ thống"),
    CONFIRMED_IDENTITY_MISMATCH("Thông tin định danh xác nhận không khớp"),

    // User-Fixable Uncertain Cases
    IMAGE_TOO_BLURRY("Ảnh chụp bị mờ, không rõ nét. Vui lòng chụp lại rõ ràng hơn."),
    IMAGE_GLARE("Ảnh chụp bị lóa sáng hoặc phản chiếu ánh đèn. Vui lòng chụp ở góc đủ sáng, không lóa."),
    DOCUMENT_NOT_FULLY_VISIBLE("Giấy tờ không hiển thị trọn vẹn trong khung hình. Vui lòng chụp rõ toàn bộ 4 góc."),
    SELFIE_QUALITY_LOW("Chất lượng ảnh chân dung không đạt yêu cầu. Vui lòng chụp lại ảnh chân dung rõ mặt."),
    OCR_LOW_CONFIDENCE_DUE_TO_IMAGE("Độ tin cậy trích xuất thông tin thấp do chất lượng ảnh kém. Vui lòng chụp lại."),
    OCR_LOW_CONFIDENCE("Chất lượng ảnh chưa rõ nét để trích xuất đầy đủ thông tin."),

    // Uncertain Cases Requiring Admin Review
    FACE_MATCH_BORDERLINE("Độ khớp khuôn mặt ở mức ranh giới, cần thẩm định thủ công"),
    DOCUMENT_DATA_INCONSISTENCY("Thông tin trên giấy tờ có sự mâu thuẫn, cần xem xét"),
    OCR_INCONSISTENT("Dữ liệu OCR không đồng nhất với hồ sơ"),
    PROVIDER_MANUAL_REVIEW_REQUIRED("Đơn vị eKYC yêu cầu chuyên viên thẩm định hồ sơ"),
    DUPLICATE_IDENTITY_EXCEPTION("Nghi vấn ngoại lệ trùng khớp danh tính"),
    AMBIGUOUS_IDENTITY_RESULT("Kết quả xác thực danh tính chưa đủ cơ sở kết luận"),

    // Provider Operational Issues
    PROVIDER_TIMEOUT("Hệ thống kết nối đến cổng eKYC quá thời gian phản hồi"),
    PROVIDER_UNAVAILABLE("Cổng xác thực danh tính tạm thời gián đoạn hoạt động"),

    // Risk Engine Reasons
    DUPLICATE_IDENTITY("Số giấy tờ tùy thân đã được liên kết với một tài khoản khác"),
    ACCOUNT_LOCKED("Tài khoản người dùng đang bị khóa"),
    ACCOUNT_DISABLED("Tài khoản người dùng đã bị vô hiệu hóa"),
    PERMANENT_SELLER_BAN("Tài khoản nằm trong danh sách cấm bán hàng vĩnh viễn"),
    REPEATED_EKYC_FAILURES("Phát hiện nhiều lần xác thực thất bại liên tiếp gần đây"),
    RISK_FLAGGED("Hệ thống ghi nhận dấu hiệu rủi ro an toàn tài khoản"),
    MANUAL_REVIEW_REQUIRED("Hồ sơ yêu cầu quản trị viên kiểm tra thủ công"),
    NONE("Hồ sơ hợp lệ, không có cảnh báo");

    private final String description;

    ReasonCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isUserFixable() {
        return switch (this) {
            case IMAGE_TOO_BLURRY,
                 IMAGE_GLARE,
                 DOCUMENT_NOT_FULLY_VISIBLE,
                 SELFIE_QUALITY_LOW,
                 OCR_LOW_CONFIDENCE_DUE_TO_IMAGE,
                 OCR_LOW_CONFIDENCE -> true;
            default -> false;
        };
    }
}
