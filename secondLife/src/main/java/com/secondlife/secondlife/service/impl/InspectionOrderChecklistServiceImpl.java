package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionOrderChecklistServiceImpl implements InspectionOrderChecklistService {

    private final InspectionOrderRepository orderRepository;
    private final InspectionChecklistService checklistService;

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getForOrder(UUID orderId) {
        InspectionOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        UUID categoryId = order.getPost() != null ? order.getPost().getCategoryId() : null;
        return checklistService.getByCategoryId(categoryId);
    }
}
