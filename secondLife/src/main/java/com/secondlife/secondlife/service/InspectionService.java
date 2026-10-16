package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
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
     * KTV hoặc Người bán chốt lịch hẹn đến nhà kiểm định (gói AT_HOME)
     */
    InspectionOrderResponse scheduleAppointment(UUID actorId, UUID orderId, InspectionAppointmentRequest request);

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
     * Người mua xác nhận mua hàng & thanh toán tiền hàng sau khi kiểm định PASSED
     */
    InspectionOrderResponse confirmFinalPurchase(UUID buyerId, UUID orderId);

    /**
     * Inspector xem danh sách đơn kiểm định được phân công cho mình
     */
    List<InspectionOrderResponse> getMyOrders(UUID inspectorId);

    /**
     * Manager xem tất cả đơn (có thể lọc theo status)
     */
    Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable);

    /**
     * Manager phân công nhân viên cho đơn kiểm định
     */
    InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId);
    InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId, UUID managerId);

    /**
     * Inspector bắt đầu tiến hành kiểm định (chuyển trạng thái sang INSPECTING)
     */
    InspectionOrderResponse startInspection(UUID actorId, UUID orderId);

    /**
     * Inspector nộp kết quả kiểm định kèm video bằng chứng và checklist
     */
    InspectionOrderResponse submitResult(UUID orderId, UUID inspectorId, InspectionResultRequest request);

    /**
     * Manager tạo tài khoản nhân viên kiểm định với role INSPECTOR
     */
    void createInspectorAccount(String email, String fullName, String password, UUID managerId);

    /**
     * Manager xem danh sách nhân viên của mình
     */
    List<?> getStaffList(UUID managerId);

    /**
     * Tra cứu và xác thực thông tin tem niêm phong QR
     */
    com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse getSealVerification(String tamperSealId);

    /**
     * Ghi nhận lịch sử các lần quét mã QR tem niêm phong (KTV, Shipper, Buyer)
     */
    com.secondlife.secondlife.dto.response.InspectionSealLogResponse recordSealScan(UUID actorId, com.secondlife.secondlife.dto.request.InspectionSealScanRequest request);

    /**
     * Sinh mã tem niêm phong chuẩn cho đơn kiểm định
     */
    String generateSealForOrder(UUID inspectorId, UUID orderId);
}
