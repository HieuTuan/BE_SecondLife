package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ConfirmInspectionPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionAppealRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentCounterRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRejectRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyerRespondRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionRejectPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionStartRequest;
import com.secondlife.secondlife.dto.request.InspectionSealScanRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.request.ReviewInspectionAppealRequest;
import com.secondlife.secondlife.dto.response.InspectionCheckInCodeResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionSealLogResponse;
import com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InspectionService {
    /**
     * Người mua tạo yêu cầu mua hàng kèm kiểm định sản phẩm
     */
    InspectionOrderResponse requestInspection(UUID buyerId, InspectionBuyRequest request);

    /**
     * Người bán phản hồi yêu cầu kiểm định (Đồng ý / Từ chối / Đề xuất lại)
     */
    InspectionOrderResponse respondInspection(UUID sellerId, UUID orderId, InspectionSellerRespondRequest request);

    /**
     * Người mua phản hồi đề xuất lại của người bán (Đồng ý / Từ chối)
     */
    InspectionOrderResponse respondInspectionByBuyer(UUID buyerId, UUID orderId, InspectionBuyerRespondRequest request);

    InspectionOrderResponse scheduleAppointment(UUID sellerId, UUID orderId, InspectionAppointmentRequest request);

    InspectionOrderResponse confirmAppointment(UUID actorId, UUID orderId);

    InspectionOrderResponse counterAppointment(UUID actorId, UUID orderId, InspectionAppointmentCounterRequest request);

    InspectionOrderResponse acceptAppointmentCounter(UUID sellerId, UUID orderId);

    InspectionOrderResponse rejectAppointment(UUID actorId, UUID orderId, InspectionAppointmentRejectRequest request);

    /**
     * Xem chi tiết 1 đơn kiểm định theo ID
     */
    InspectionOrderResponse getOrderById(UUID actorId, UUID orderId);

    /**
     * Người mua xem danh sách đơn kiểm định của mình
     */
    Page<InspectionOrderResponse> getBuyerOrders(UUID buyerId, Pageable pageable);

    /**
     * Người bán xem danh sách đơn kiểm định của mình
     */
    Page<InspectionOrderResponse> getSellerOrders(UUID sellerId, Pageable pageable);

    /**
     * Người mua xác nhận hoàn tất mua hàng và thanh toán tiền hàng (sau khi kiểm định PASSED)
     */
    InspectionOrderResponse confirmFinalPurchase(UUID buyerId, UUID orderId);

    /**
     * Người mua xác nhận hoàn tất mua hàng kèm địa chỉ/phương thức mới
     */
    InspectionOrderResponse confirmFinalPurchase(UUID buyerId, UUID orderId, ConfirmInspectionPurchaseRequest request);

    /**
     * Người mua từ chối mua tiếp sau khi có kết quả kiểm định đạt chuẩn (hủy đơn)
     */
    InspectionOrderResponse rejectFinalPurchase(UUID buyerId, UUID orderId, InspectionRejectPurchaseRequest request);

    /**
     * KTV xem danh sách đơn được phân công cho mình
     */
    List<InspectionOrderResponse> getMyOrders(UUID inspectorId);

    /**
     * Quản trị viên/Trung tâm xem toàn bộ đơn kiểm định
     */
    Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable);
    Page<InspectionOrderResponse> getAllOrders(UUID actorId, String status, Pageable pageable);

    /**
     * Phân công KTV cho đơn kiểm định
     */
    InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId);

    /**
     * Manager phân công KTV thuộc trung tâm của mình
     */
    InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId, UUID managerId);

    /**
     * KTV bắt đầu tiến hành kiểm tra (chuyển sang INSPECTING)
     */
    InspectionOrderResponse startInspection(UUID actorId, UUID orderId, InspectionStartRequest request);

    InspectionCheckInCodeResponse getOrCreateHomeCheckInCode(UUID sellerId, UUID orderId);

    /**
     * KTV nhập biên bản kết quả kiểm định chi tiết (PASSED hoặc FAILED)
     */
    InspectionOrderResponse submitResult(UUID orderId, UUID inspectorId, InspectionResultRequest request);

    /**
     * Thêm tài khoản nhân viên kiểm định mới
     */
    void createInspectorAccount(String email, String fullName, String password, UUID managerId);

    /**
     * Manager xem danh sách nhân viên của mình
     */
    List<?> getStaffList(UUID managerId);

    /**
     * Tra cứu và xác thực thông tin tem niêm phong QR
     */
    InspectionSealVerificationResponse getSealVerification(String tamperSealId);

    /**
     * Ghi nhận lịch sử các lần quét mã QR tem niêm phong (KTV, Shipper, Buyer)
     */
    InspectionSealLogResponse recordSealScan(UUID actorId, InspectionSealScanRequest request);

    /**
     * Sinh mã tem niêm phong chuẩn cho đơn kiểm định
     */
    String generateSealForOrder(UUID inspectorId, UUID orderId);

    /**
     * Quét các đơn kiểm định quá hạn phản hồi (24h) chuyển sang CANCELLED
     */
    void processExpiredApprovals();

    void processExpiredFinalPayment(UUID orderId);

    /**
     * Người bán gửi khiếu nại kết quả kiểm định trong vòng 24 giờ
     */
    InspectionOrderResponse submitAppeal(UUID sellerId, UUID orderId, InspectionAppealRequest request);

    /**
     * Người bán chủ động chấp nhận kết quả FAILED sớm không cần chờ 24 giờ
     */
    InspectionOrderResponse acceptFailedResult(UUID sellerId, UUID orderId);

    /**
     * Admin hoặc Quản lý trung tâm duyệt khiếu nại của người bán (UPHELD / OVERTURNED)
     */
    InspectionOrderResponse reviewAppeal(UUID reviewerId, UUID orderId, ReviewInspectionAppealRequest request);

    /**
     * Tiến trình tự động quét các đơn FAILED hết hạn 24 giờ không khiếu nại để trừ điểm uy tín người bán
     */
    void processExpiredAppeals();
}
