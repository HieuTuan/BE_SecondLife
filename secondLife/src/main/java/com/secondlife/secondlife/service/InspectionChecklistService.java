package com.secondlife.secondlife.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionChecklistService {

    private final InspectionChecklistTemplateRepository templateRepository;
    private final InspectionChecklistItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getAllTemplates() {
        return templateRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InspectionChecklistResponse getById(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findByIdWithItems(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public InspectionChecklistResponse getByCategoryId(UUID categoryId) {
        if (categoryId != null) {
            List<InspectionChecklistTemplate> list = templateRepository.findByCategoryWithItems(categoryId);
            if (!list.isEmpty()) {
                return toResponse(list.get(0));
            }
        }
        // Fallback: Lấy template chung đầu tiên
        List<InspectionChecklistTemplate> general = templateRepository.findByActiveTrueOrderByCreatedAtDesc();
        if (!general.isEmpty()) {
            return toResponse(templateRepository.findByIdWithItems(general.get(0).getId()).orElse(general.get(0)));
        }
        return defaultTemplate();
    }

    @Transactional
    public InspectionChecklistResponse createTemplate(UUID creatorId, InspectionChecklistTemplateRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        }

        InspectionChecklistTemplate template = new InspectionChecklistTemplate();
        template.setCategory(category);
        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());
        template.setCreatedBy(creator);
        template.setActive(true);

        if (request.getItems() != null) {
            int order = 1;
            for (InspectionChecklistItemRequest itemReq : request.getItems()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(template);
                item.setItemTitle(itemReq.getItemTitle());
                item.setCriteriaDescription(itemReq.getCriteriaDescription());
                item.setMandatory(itemReq.isMandatory());
                item.setOrderIndex(itemReq.getOrderIndex() != null ? itemReq.getOrderIndex() : order++);
                template.getItems().add(item);
            }
        }

        InspectionChecklistTemplate saved = templateRepository.save(template);
        return toResponse(saved);
    }

    @Transactional
    public InspectionChecklistResponse updateTemplate(UUID templateId, InspectionChecklistTemplateRequest request) {
        InspectionChecklistTemplate template = templateRepository.findByIdWithItems(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            template.setCategory(category);
        } else {
            template.setCategory(null);
        }

        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());

        template.getItems().clear();
        if (request.getItems() != null) {
            int order = 1;
            for (InspectionChecklistItemRequest itemReq : request.getItems()) {
                InspectionChecklistItem item = new InspectionChecklistItem();
                item.setTemplate(template);
                item.setItemTitle(itemReq.getItemTitle());
                item.setCriteriaDescription(itemReq.getCriteriaDescription());
                item.setMandatory(itemReq.isMandatory());
                item.setOrderIndex(itemReq.getOrderIndex() != null ? itemReq.getOrderIndex() : order++);
                template.getItems().add(item);
            }
        }

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        InspectionChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        template.setActive(false);
        templateRepository.save(template);
    }

    private InspectionChecklistResponse toResponse(InspectionChecklistTemplate template) {
        List<InspectionChecklistItemResponse> items = new ArrayList<>();
        if (template.getItems() != null) {
            items = template.getItems().stream()
                    .map(it -> InspectionChecklistItemResponse.builder()
                            .id(it.getId())
                            .itemTitle(it.getItemTitle())
                            .criteriaDescription(it.getCriteriaDescription())
                            .mandatory(it.isMandatory())
                            .orderIndex(it.getOrderIndex())
                            .build())
                    .collect(Collectors.toList());
        }

        return InspectionChecklistResponse.builder()
                .id(template.getId())
                .categoryId(template.getCategory() != null ? template.getCategory().getId() : null)
                .categoryName(template.getCategory() != null ? template.getCategory().getName() : "Chung / Mọi danh mục")
                .title(template.getTitle())
                .description(template.getDescription())
                .active(template.isActive())
                .items(items)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private InspectionChecklistResponse defaultTemplate() {
        List<InspectionChecklistItemResponse> items = List.of(
                new InspectionChecklistItemResponse(UUID.randomUUID(), "Kiểm tra ngoại quan, trầy xước, nứt vỡ", "Không cấn móp nặng, màn hình/vỏ nguyên vẹn", true, 1),
                new InspectionChecklistItemResponse(UUID.randomUUID(), "Tháo ốc kiểm tra bo mạch bên trong", "Bo mạch sạch, không bị vô nước, chưa sửa chữa chắp vá", true, 2),
                new InspectionChecklistItemResponse(UUID.randomUUID(), "Kiểm tra chức năng nguồn và khởi động", "Lên nguồn ổn định, không sập nguồn đột ngột", true, 3),
                new InspectionChecklistItemResponse(UUID.randomUUID(), "Kiểm tra linh kiện chính (Pin, Động cơ, Màn hình)", "Hiệu suất hoạt động đạt trên 80% định mức", true, 4),
                new InspectionChecklistItemResponse(UUID.randomUUID(), "Kiểm tra cổng kết nối và phụ kiện kèm theo", "Đầy đủ phụ kiện theo mô tả, sạc/cắm điện bình thường", false, 5)
        );

        return InspectionChecklistResponse.builder()
                .id(UUID.randomUUID())
                .title("Checklist tiêu chuẩn SecondLife")
                .description("Bộ tiêu chí kiểm định kỹ thuật 5 bước cho thiết bị điện tử và gia dụng")
                .active(true)
                .items(items)
                .build();
    }
}
