package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ChecklistItemResultDto;
import com.secondlife.secondlife.dto.response.InspectionChecklistItemResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionOrderChecklistServiceImpl implements InspectionOrderChecklistService {
    private final InspectionOrderRepository orderRepository;
    private final InspectionChecklistService checklistService;
    private final InspectionGovernanceAccessService access;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getForOrder(UUID actorId, UUID orderId) {
        InspectionOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        access.requireOrderRead(actorId, order);
        if (order.getChecklistSnapshotJson() != null) return snapshot(order);
        UUID categoryId = order.getPost() != null ? order.getPost().getCategoryId() : null;
        return checklistService.getByCategoryId(categoryId);
    }

    @Override
    public List<ChecklistItemResultDto> validateResults(InspectionOrder order, List<ChecklistItemResultDto> results, String outcome) {
        if (order.getChecklistSnapshotJson() == null) return results;
        InspectionChecklistResponse checklist = snapshot(order);
        if (checklist.items() == null || checklist.items().isEmpty()) {
            throw new IllegalStateException("Stored checklist has no criteria");
        }
        Map<UUID, InspectionChecklistItemResponse> allowed = checklist.items().stream()
                .collect(Collectors.toMap(InspectionChecklistItemResponse::id, Function.identity()));
        Map<UUID, ChecklistItemResultDto> submitted = new HashMap<>();
        for (ChecklistItemResultDto result : results == null ? List.<ChecklistItemResultDto>of() : results) {
            if (result == null || result.itemId() == null || !allowed.containsKey(result.itemId())
                    || submitted.putIfAbsent(result.itemId(), result) != null) {
                throw new BadRequestException("Tiêu chí phải thuộc checklist đã chốt, có ID hợp lệ và không được trùng");
            }
            if (result.inspectorNote() != null && result.inspectorNote().length() > 4000) {
                throw new BadRequestException("Ghi chú tiêu chí tối đa 4000 ký tự");
            }
        }
        List<ChecklistItemResultDto> normalized = new ArrayList<>();
        for (InspectionChecklistItemResponse criterion : checklist.items()) {
            ChecklistItemResultDto result = submitted.get(criterion.id());
            if (criterion.mandatory() && result == null) {
                throw new BadRequestException("Chưa đánh giá tiêu chí bắt buộc: " + criterion.itemTitle());
            }
            if (result != null) {
                if ("PASSED".equals(outcome) && criterion.mandatory() && !result.passed()) {
                    throw new BadRequestException("Không thể kết luận PASSED khi tiêu chí bắt buộc không đạt");
                }
                normalized.add(new ChecklistItemResultDto(criterion.id(), criterion.itemTitle(), result.passed(), result.inspectorNote()));
            }
        }
        return List.copyOf(normalized);
    }

    private InspectionChecklistResponse snapshot(InspectionOrder order) {
        return objectMapper.readValue(order.getChecklistSnapshotJson(), InspectionChecklistResponse.class);
    }
}
