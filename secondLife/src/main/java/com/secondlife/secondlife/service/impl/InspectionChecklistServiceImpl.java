package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionChecklistItemRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistItemResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.InspectionChecklistItem;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionChecklistItemRepository;
import com.secondlife.secondlife.repository.InspectionChecklistTemplateRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionChecklistServiceImpl implements InspectionChecklistService {

    private final InspectionChecklistTemplateRepository templateRepository;
    private final InspectionChecklistItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getAllTemplates() {
        return templateRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getById(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findByIdWithItems(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        return toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getByCategoryId(UUID categoryId) {
        if (categoryId != null) {
            List<InspectionChecklistTemplate> list = templateRepository.findByCategoryWithItems(categoryId);
            if (!list.isEmpty()) {
                return toResponse(list.get(0));
            }
        }
        List<InspectionChecklistTemplate> general = templateRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (!general.isEmpty()) {
            return toResponse(templateRepository.findByIdWithItems(general.get(0).getId()).orElse(general.get(0)));
        }
        return InspectionChecklistResponse.builder()
                .title("Checklist kiểm định chung")
                .description("Checklist mặc định")
                .items(List.of(
                        InspectionChecklistItemResponse.builder()
                                .itemTitle("Ngoại quan và ốc vít")
                                .criteriaDescription("Kiểm tra vết xước, móp méo, dấu vết cạy mở tháo ốc")
                                .mandatory(true)
                                .orderIndex(1)
                                .build(),
                        InspectionChecklistItemResponse.builder()
                                .itemTitle("Chức năng hoạt động chính")
                                .criteriaDescription("Khởi động, cắm sạc/nguồn, hoạt động các tính năng cốt lõi")
                                .mandatory(true)
                                .orderIndex(2)
                                .build()
                ))
                .build();
    }

    @Override
    @Transactional
    public InspectionChecklistResponse createTemplate(UUID creatorId, InspectionChecklistTemplateRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("Creator not found"));

        InspectionChecklistTemplate template = new InspectionChecklistTemplate();
        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());
        template.setCreatedBy(creator);
        template.setActive(true);

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId()).orElse(null);
            template.setCategory(category);
        }

        InspectionChecklistTemplate saved = templateRepository.save(template);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<InspectionChecklistItem> items = new ArrayList<>();
            int idx = 1;
            for (InspectionChecklistItemRequest itemReq : request.getItems()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(saved);
                item.setItemTitle(itemReq.getItemTitle());
                item.setCriteriaDescription(itemReq.getCriteriaDescription());
                item.setMandatory(itemReq.isMandatory());
                item.setOrderIndex(itemReq.getOrderIndex() > 0 ? itemReq.getOrderIndex() : idx++);
                items.add(item);
            }
            itemRepository.saveAll(items);
            saved.setItems(items);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionChecklistResponse updateTemplate(UUID templateId, InspectionChecklistTemplateRequest request) {
        InspectionChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));

        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId()).orElse(null);
            template.setCategory(category);
        }

        if (request.getItems() != null) {
            itemRepository.deleteAll(template.getItems());
            template.getItems().clear();

            List<InspectionChecklistItem> items = new ArrayList<>();
            int idx = 1;
            for (InspectionChecklistItemRequest itemReq : request.getItems()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(template);
                item.setItemTitle(itemReq.getItemTitle());
                item.setCriteriaDescription(itemReq.getCriteriaDescription());
                item.setMandatory(itemReq.isMandatory());
                item.setOrderIndex(itemReq.getOrderIndex() > 0 ? itemReq.getOrderIndex() : idx++);
                items.add(item);
            }
            itemRepository.saveAll(items);
            template.setItems(items);
        }

        return toResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        template.setActive(false);
        templateRepository.save(template);
    }

    private InspectionChecklistResponse toResponse(InspectionChecklistTemplate t) {
        List<InspectionChecklistItemResponse> items = t.getItems() != null ? t.getItems().stream()
                .map(i -> InspectionChecklistItemResponse.builder()
                        .id(i.getId())
                        .itemTitle(i.getItemTitle())
                        .criteriaDescription(i.getCriteriaDescription())
                        .mandatory(i.isMandatory())
                        .orderIndex(i.getOrderIndex())
                        .build())
                .collect(Collectors.toList()) : List.of();

        return InspectionChecklistResponse.builder()
                .id(t.getId())
                .categoryId(t.getCategory() != null ? t.getCategory().getId() : null)
                .categoryName(t.getCategory() != null ? t.getCategory().getName() : null)
                .title(t.getTitle())
                .description(t.getDescription())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .items(items)
                .build();
    }
}
