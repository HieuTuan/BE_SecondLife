package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InspectionService {
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

    /**
     * Inspector nộp kết quả kiểm định.
     * Nếu PASSED → AI scan lần 2 → ACTIVE
     * Nếu FAILED → REJECTED
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
}
