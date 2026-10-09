package com.secondlife.secondlife.service;
import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.OrderCommissionSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
public interface CommissionService {
    Page<CommissionRuleResponse> list(Pageable pageable);
    CommissionRuleResponse get(UUID id);
    CommissionRuleResponse create(UUID actorId, CommissionRuleRequest request);
    CommissionRuleResponse update(UUID actorId, UUID id, CommissionRuleRequest request);
    CommissionRuleResponse deactivate(UUID actorId, UUID id, String reason);
    Page<CommissionAuditResponse> history(UUID id, Pageable pageable);
    OrderCommissionSnapshot capture(Order order, UUID actorId, String reason);
    OrderCommissionSnapshot requireSnapshot(UUID orderId);
    OrderCommissionResponse describe(OrderCommissionSnapshot snapshot);
}
