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
import com.secondlife.secondlife.mapper.InspectionChecklistMapper;
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
    private final InspectionChecklistMapper inspectionChecklistMapper;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getAllTemplates() {
        return templateRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(inspectionChecklistMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getById(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findByIdWithItems(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        return inspectionChecklistMapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getByCategoryId(UUID categoryId) {
        if (categoryId != null) {
            List<InspectionChecklistTemplate> list = templateRepository.findByCategoryWithItems(categoryId);
            if (!list.isEmpty()) {
                return inspectionChecklistMapper.toResponse(list.get(0));
            }
        }
        List<InspectionChecklistTemplate> general = templateRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (!general.isEmpty()) {
            return inspectionChecklistMapper.toResponse(templateRepository.findByIdWithItems(general.get(0).getId()).orElse(general.get(0)));
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
        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setCreatedBy(creator);
        template.setActive(true);

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId()).orElse(null);
            template.setCategory(category);
        }

        InspectionChecklistTemplate saved = templateRepository.save(template);

        if (request.items() != null && !request.items().isEmpty()) {
            List<InspectionChecklistItem> items = new ArrayList<>();
            int idx = 1;
            for (InspectionChecklistItemRequest itemReq : request.items()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(saved);
                item.setItemTitle(itemReq.itemTitle());
                item.setCriteriaDescription(itemReq.criteriaDescription());
                item.setMandatory(itemReq.mandatory());
                item.setOrderIndex(itemReq.orderIndex() > 0 ? itemReq.orderIndex() : idx++);
                items.add(item);
            }
            itemRepository.saveAll(items);
            saved.setItems(items);
        }

        return inspectionChecklistMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionChecklistResponse updateTemplate(UUID templateId, InspectionChecklistTemplateRequest request) {
        InspectionChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));

        template.setTitle(request.title());
        template.setDescription(request.description());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId()).orElse(null);
            template.setCategory(category);
        }

        if (request.items() != null) {
            itemRepository.deleteAll(template.getItems());
            template.getItems().clear();

            List<InspectionChecklistItem> items = new ArrayList<>();
            int idx = 1;
            for (InspectionChecklistItemRequest itemReq : request.items()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(template);
                item.setItemTitle(itemReq.itemTitle());
                item.setCriteriaDescription(itemReq.criteriaDescription());
                item.setMandatory(itemReq.mandatory());
                item.setOrderIndex(itemReq.orderIndex() > 0 ? itemReq.orderIndex() : idx++);
                items.add(item);
            }
            itemRepository.saveAll(items);
            template.setItems(items);
        }

        return inspectionChecklistMapper.toResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        template.setActive(false);
        templateRepository.save(template);
    }

}
