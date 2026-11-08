package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionChecklistItemRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistSearchRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistItemResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistPricingResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionChecklistItem;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.InspectionChecklistMapper;
import com.secondlife.secondlife.repository.InspectionChecklistTemplateRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.service.InspectionChecklistPricingService;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionChecklistServiceImpl implements InspectionChecklistService {
    private final InspectionChecklistTemplateRepository templates;
    private final ItemRepository catalogItems;
    private final InspectionGovernanceAccessService access;
    private final InspectionChecklistMapper mapper;
    private final InspectionChecklistPricingService pricing;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getAllTemplates() {
        return templates.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(t -> t.getApprovalStatus() == InspectionChecklistStatus.APPROVED)
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getTemplatesForCenter(UUID actorId) {
        access.requireCenter(actorId);
        return templates.findVisibleToCenter(actorId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getById(UUID actorId, UUID templateId) {
        User actor = access.requireActiveUser(actorId);
        InspectionChecklistTemplate template = requireTemplate(templateId);
        boolean author = template.getCreatedBy() != null && actorId.equals(template.getCreatedBy().getId());
        if (!actor.hasRole("ADMIN") && !author && template.getApprovalStatus() != InspectionChecklistStatus.APPROVED) {
            throw new ForbiddenException("Không được xem đề xuất checklist của trung tâm khác");
        }
        if (actor.hasRole("ADMIN")) {
            return withPrices(List.of(template)).get(template.getId());
        }
        return mapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionChecklistResponse getByCategoryId(UUID categoryId) {
        // Only historical orders without snapshots use this compatibility path.
        List<InspectionChecklistTemplate> candidates = templates.findByActiveTrueOrderByCreatedAtDesc();
        return candidates.stream()
                .filter(t -> t.getApprovalStatus() == InspectionChecklistStatus.LEGACY)
                .filter(t -> categoryId != null && t.getCategory() != null && categoryId.equals(t.getCategory().getId()))
                .findFirst().or(() -> candidates.stream()
                        .filter(t -> t.getApprovalStatus() == InspectionChecklistStatus.LEGACY && t.getCategory() == null)
                        .findFirst())
                .map(mapper::toResponse)
                .orElseGet(this::legacyDefault);
    }

    @Override
    @Transactional
    public InspectionChecklistResponse createTemplate(UUID creatorId, InspectionChecklistTemplateRequest request) {
        User creator = access.requireCenter(creatorId);
        Item item = requireItem(request);
        InspectionChecklistTemplate current = approvedFor(item.getId()).orElse(null);
        return mapper.toResponse(templates.saveAndFlush(buildProposal(creator, item, request, current)));
    }

    @Override
    @Transactional
    public InspectionChecklistResponse updateTemplate(UUID actorId, UUID templateId, InspectionChecklistTemplateRequest request) {
        User creator = access.requireCenter(actorId);
        Item item = requireItem(request);
        InspectionChecklistTemplate original = templates.findByIdForUpdate(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        requireAuthor(actorId, original);
        if (original.getCatalogItem() != null && !item.getId().equals(original.getCatalogItem().getId())) {
            throw new BadRequestException("Một phiên bản checklist không được đổi loại sản phẩm");
        }
        if (original.getApprovalStatus() == InspectionChecklistStatus.WITHDRAWN) {
            throw new ConflictException("Đề xuất đã được rút");
        }
        if (original.getApprovalStatus() == InspectionChecklistStatus.PENDING_APPROVAL) {
            original.setApprovalStatus(InspectionChecklistStatus.WITHDRAWN);
            original.setActive(false);
        }
        InspectionChecklistTemplate current = approvedFor(item.getId()).orElse(null);
        return mapper.toResponse(templates.saveAndFlush(buildProposal(creator, item, request, current)));
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID actorId, UUID templateId) {
        access.requireCenter(actorId);
        InspectionChecklistTemplate template = templates.findByIdForUpdate(templateId)
                .orElseThrow(() -> new NotFoundException("Checklist template not found"));
        requireAuthor(actorId, template);
        if (template.getApprovalStatus() == InspectionChecklistStatus.APPROVED
                || template.getApprovalStatus() == InspectionChecklistStatus.LEGACY) {
            throw new ConflictException("Checklist đã áp dụng được giữ nguyên; hãy gửi phiên bản mới để Admin duyệt");
        }
        template.setApprovalStatus(InspectionChecklistStatus.WITHDRAWN);
        template.setActive(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionChecklistResponse> getProposals(UUID actorId, InspectionChecklistStatus status, Pageable pageable) {
        return searchProposals(actorId, new InspectionChecklistSearchRequest(null, status, null, null, null, null, null), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionChecklistResponse> searchProposals(UUID actorId, InspectionChecklistSearchRequest filter, Pageable pageable) {
        User actor = access.requireActiveUser(actorId);
        UUID authorId = actor.hasRole("ADMIN") ? null : access.requireCenter(actorId).getId();
        Pageable sorted = InspectionPageables.validate(pageable, Set.of("id", "createdAt", "reviewedAt", "revision", "approvalStatus", "complexityLevel"));
        Page<UUID> ids = templates.searchProposalIds(authorId, filter.status(), filter.categoryId(), filter.itemId(),
                filter.complexityLevel(), filter.inspectionCenterId(), filter.active(),
                InspectionChecklistSearch.normalizedPattern(filter.keyword()), InspectionChecklistSearch.ACCENTED_CHARACTERS,
                InspectionChecklistSearch.REPLACEMENT_CHARACTERS, sorted);
        if (ids.isEmpty()) return new PageImpl<>(List.of(), sorted, ids.getTotalElements());
        List<InspectionChecklistTemplate> templatesInPage = templates.findDetailsByIds(ids.getContent());
        Map<UUID, InspectionChecklistResponse> details;
        if (actor.hasRole("ADMIN")) {
            details = withPrices(templatesInPage);
        } else {
            details = templatesInPage.stream().collect(Collectors.toMap(InspectionChecklistTemplate::getId, mapper::toResponse));
        }
        return ids.map(details::get);
    }

    private Map<UUID, InspectionChecklistResponse> withPrices(List<InspectionChecklistTemplate> templatesInPage) {
        Map<UUID, InspectionChecklistPricingResponse> prices = pricing.getPrices(templatesInPage);
        return templatesInPage.stream().collect(Collectors.toMap(InspectionChecklistTemplate::getId,
                template -> mapper.withPricing(mapper.toResponse(template), prices.get(template.getId()))));
    }

    @Override
    @Transactional
    public InspectionChecklistResponse review(UUID reviewerId, UUID templateId, boolean approved, String reason) {
        User reviewer = access.requireAdmin(reviewerId);
        UUID itemId = templates.findCatalogItemId(templateId)
                .orElseThrow(() -> new ConflictException("Checklist không tồn tại hoặc cần gửi lại theo Item cụ thể"));
        catalogItems.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        InspectionChecklistTemplate proposal = templates.findByIdForUpdate(templateId).orElseThrow();
        if (proposal.getApprovalStatus() != InspectionChecklistStatus.PENDING_APPROVAL) {
            throw new ConflictException("Đề xuất checklist đã được xử lý");
        }
        if (proposal.getCreatedBy() != null && reviewerId.equals(proposal.getCreatedBy().getId())) {
            throw new ForbiddenException("Người gửi không được tự duyệt đề xuất");
        }
        if (!approved && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Cần nêu lý do từ chối");
        }
        if (reason != null && reason.length() > 2000) throw new BadRequestException("Lý do tối đa 2000 ký tự");
        if (approved) {
            if (!proposal.getCatalogItem().isSelectable()) throw new ConflictException("Item đã được tách; cần chọn loại cụ thể");
            InspectionChecklistTemplate current = approvedFor(proposal.getCatalogItem().getId()).orElse(null);
            UUID currentId = current == null ? null : current.getId();
            if (!Objects.equals(currentId, proposal.getSupersedesId())) {
                throw new ConflictException("Checklist hiện hành đã thay đổi; cần gửi lại đề xuất dựa trên phiên bản mới");
            }
            if (current != null) {
                current.setActive(false);
                templates.saveAndFlush(current);
            }
        }
        proposal.setApprovalStatus(approved ? InspectionChecklistStatus.APPROVED : InspectionChecklistStatus.REJECTED);
        proposal.setActive(approved);
        proposal.setReviewedBy(reviewer);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectReason(approved ? null : reason.strip());
        return mapper.toResponse(templates.saveAndFlush(proposal));
    }

    private InspectionChecklistTemplate buildProposal(User author, Item item,
            InspectionChecklistTemplateRequest request, InspectionChecklistTemplate current) {
        if (request.title() == null || request.title().isBlank() || request.title().length() > 200
                || request.complexityLevel() == null) {
            throw new BadRequestException("Cần tên checklist và cấp độ phức tạp L1-L4 hợp lệ");
        }
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw new BadRequestException("Checklist cần từ 1 đến 100 tiêu chí");
        }
        InspectionChecklistTemplate proposal = new InspectionChecklistTemplate();
        proposal.setCategory(item.getCategory());
        proposal.setCatalogItem(item);
        proposal.setTitle(request.title());
        proposal.setDescription(request.description());
        proposal.setComplexityLevel(request.complexityLevel());
        proposal.setCreatedBy(author);
        proposal.setApprovalStatus(InspectionChecklistStatus.PENDING_APPROVAL);
        proposal.setActive(false);
        if (current != null) {
            proposal.setSupersedesId(current.getId());
            proposal.setRevision(current.getRevision() + 1);
        }
        Set<String> titles = new HashSet<>();
        Set<Integer> indexes = new HashSet<>();
        boolean hasMandatory = false;
        List<InspectionChecklistItem> criteria = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) {
            InspectionChecklistItemRequest input = request.items().get(index);
            if (input == null || input.itemTitle() == null || input.itemTitle().isBlank()
                    || input.itemTitle().length() > 255 || !titles.add(input.itemTitle().strip().toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Tên tiêu chí phải hợp lệ, không trùng và tối đa 255 ký tự");
            }
            int position = input.orderIndex() == null || input.orderIndex() == 0 ? index + 1 : input.orderIndex();
            if (position < 1 || !indexes.add(position)) throw new BadRequestException("Thứ tự tiêu chí phải dương và không trùng");
            InspectionChecklistItem criterion = new InspectionChecklistItem();
            criterion.setTemplate(proposal);
            criterion.setItemTitle(input.itemTitle());
            criterion.setCriteriaDescription(input.criteriaDescription());
            criterion.setMandatory(input.mandatory());
            criterion.setOrderIndex(position);
            criteria.add(criterion);
            hasMandatory |= input.mandatory();
        }
        if (!hasMandatory) throw new BadRequestException("Checklist cần ít nhất một tiêu chí bắt buộc");
        criteria.sort(Comparator.comparing(InspectionChecklistItem::getOrderIndex));
        proposal.setItems(criteria);
        return proposal;
    }

    private Item requireItem(InspectionChecklistTemplateRequest request) {
        if (request.itemId() == null) throw new BadRequestException("Cần chọn Item cụ thể");
        Item item = catalogItems.findByIdForUpdate(request.itemId())
                .orElseThrow(() -> new NotFoundException("Item not found"));
        if (!item.isSelectable()) throw new ConflictException("Item đã được tách; vui lòng chọn loại cụ thể");
        if (request.categoryId() != null && !request.categoryId().equals(item.getCategory().getId())) {
            throw new BadRequestException("Item không thuộc danh mục đã chọn");
        }
        return item;
    }

    private Optional<InspectionChecklistTemplate> approvedFor(UUID itemId) {
        return templates.findByCatalogItem_IdAndApprovalStatusAndActiveTrue(
                itemId, InspectionChecklistStatus.APPROVED);
    }

    private InspectionChecklistTemplate requireTemplate(UUID id) {
        return templates.findByIdWithItems(id).orElseThrow(() -> new NotFoundException("Checklist template not found"));
    }

    private void requireAuthor(UUID actorId, InspectionChecklistTemplate template) {
        if (template.getCreatedBy() == null || !actorId.equals(template.getCreatedBy().getId())) {
            throw new ForbiddenException("Không được sửa hoặc rút đề xuất của trung tâm khác");
        }
    }

    private InspectionChecklistResponse legacyDefault() {
        return InspectionChecklistResponse.builder().title("Checklist kiểm định chung")
                .description("Checklist tương thích cho đơn cũ chưa lưu phiên bản")
                .approvalStatus(InspectionChecklistStatus.LEGACY)
                .items(List.of(
                        InspectionChecklistItemResponse.builder().itemTitle("Ngoại quan và ốc vít")
                                .criteriaDescription("Kiểm tra hiện trạng ngoại quan").mandatory(true).orderIndex(1).build(),
                        InspectionChecklistItemResponse.builder().itemTitle("Chức năng hoạt động chính")
                                .criteriaDescription("Đối chiếu chức năng chính theo sản phẩm").mandatory(true).orderIndex(2).build()
                )).build();
    }
}
