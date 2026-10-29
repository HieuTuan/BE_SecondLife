package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ApproveTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.RejectTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.UpdateTemplateProposalRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateProposalResponse;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.dto.response.ResolvedQuestionTemplateResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.CategoryQuestionTemplateProposal;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.TemplateProposalStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateMapper;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateProposalMapper;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateProposalRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CatalogManagementService;
import com.secondlife.secondlife.service.CategoryTemplateService;
import com.secondlife.secondlife.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategoryTemplateServiceImpl implements CategoryTemplateService {

    private static final String ADMIN_CATALOG_AI_ACTION_URL = "/admin?subtab=catalog-ai";
    private static final String PROPOSAL_REFERENCE_TYPE = "TEMPLATE_PROPOSAL";

    private final CatalogManagementService catalogService;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final CategoryQuestionTemplateRepository templateRepository;
    private final CategoryQuestionTemplateProposalRepository proposalRepository;
    private final CategoryQuestionTemplateMapper templateMapper;
    private final CategoryQuestionTemplateProposalMapper proposalMapper;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AiChatGateway googleChatGateway;
    private final AiChatGateway ollamaChatGateway;
    private final String ollamaModel;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    public CategoryTemplateServiceImpl(
            CatalogManagementService catalogService,
            CategoryRepository categoryRepository,
            ItemRepository itemRepository,
            CategoryQuestionTemplateRepository templateRepository,
            CategoryQuestionTemplateProposalRepository proposalRepository,
            CategoryQuestionTemplateMapper templateMapper,
            CategoryQuestionTemplateProposalMapper proposalMapper,
            UserRepository userRepository,
            NotificationService notificationService,
            @Qualifier("googleChatGateway") AiChatGateway googleChatGateway,
            @Qualifier("ollamaChatGateway") AiChatGateway ollamaChatGateway,
            @Value("${spring.ai.ollama.chat.options.model}") String ollamaModel,
            PlatformTransactionManager transactionManager
    ) {
        if (ollamaModel == null || ollamaModel.isBlank()) {
            throw new IllegalArgumentException("spring.ai.ollama.chat.options.model must not be blank");
        }
        this.catalogService = catalogService;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
        this.proposalRepository = proposalRepository;
        this.templateMapper = templateMapper;
        this.proposalMapper = proposalMapper;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.googleChatGateway = googleChatGateway;
        this.ollamaChatGateway = ollamaChatGateway;
        this.ollamaModel = ollamaModel;
        if (transactionManager != null) {
            this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            this.readTransactionTemplate = readTx;
        } else {
            this.writeTransactionTemplate = null;
            this.readTransactionTemplate = null;
        }
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateResponse createTemplate(CategoryQuestionTemplateRequest request) {
        return templateMapper.toResponse(catalogService.createTemplate(request));
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateResponse updateTemplate(UUID id, CategoryQuestionTemplateRequest request) {
        return templateMapper.toResponse(catalogService.updateTemplate(id, request));
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID id) {
        catalogService.deleteTemplate(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryQuestionTemplateResponse> getAllTemplates() {
        return catalogService.getAllTemplates().stream().map(templateMapper::toResponse).toList();
    }

    @Override
    public ResolvedQuestionTemplateResponse resolveOrGenerateForListing(
            UUID categoryId,
            UUID itemId,
            UUID sellerId
    ) {
        LookupContext lookup = executeRead(() -> loadLookupContext(categoryId, itemId));
        if (lookup.officialTemplate() != null) {
            return lookup.officialTemplate();
        }

        ResolvedQuestionTemplateResponse reusedPending = executeWrite(() -> incrementExistingPendingProposal(categoryId, itemId));
        if (reusedPending != null) {
            return reusedPending;
        }

        // Generate outside of any database transaction so DB connections/locks are never held during AI I/O.
        String promptText = buildSpecializedTemplatePrompt(
                lookup.categoryName(),
                lookup.itemName(),
                lookup.categoryDefaultTemplateText()
        );
        String aiGeneratedText = generateWithGatewayFallback(promptText, lookup.categoryName(), lookup.itemName());

        if (aiGeneratedText != null && !aiGeneratedText.isBlank()) {
            final String sanitized = sanitizeGeneratedTemplate(aiGeneratedText);
            if (!sanitized.isBlank()) {
                ProposalPersistenceResult persisted = executeWrite(() ->
                        persistPendingProposalAtomically(categoryId, itemId, sellerId, sanitized)
                );
                if (persisted.newlyCreatedProposalId() != null && !persisted.adminIds().isEmpty()) {
                    notifyAdminsOfNewProposal(
                            persisted.newlyCreatedProposalId(),
                            lookup.categoryName(),
                            lookup.itemName(),
                            persisted.adminIds()
                    );
                }
                return persisted.resolvedResponse();
            }
        }

        log.warn("AI question template generation unavailable for category='{}', item='{}'. Using specialized deterministic fallback.",
                lookup.categoryName(), lookup.itemName());
        String fallbackText = buildFallbackTemplateWithCategoryBase(
                lookup.categoryName(),
                lookup.itemName(),
                lookup.categoryDefaultTemplateText()
        );
        return new ResolvedQuestionTemplateResponse(null, fallbackText, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryQuestionTemplateProposalResponse> getProposals(TemplateProposalStatus status) {
        List<CategoryQuestionTemplateProposal> proposals = status == null
                ? proposalRepository.findAllByOrderByUpdatedAtDesc()
                : proposalRepository.findByStatusOrderByUpdatedAtDesc(status);

        if (proposals.isEmpty()) {
            return List.of();
        }

        Set<UUID> categoryIds = proposals.stream()
                .map(CategoryQuestionTemplateProposal::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> itemIds = proposals.stream()
                .map(CategoryQuestionTemplateProposal::getItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<UUID, String> itemNames = itemIds.isEmpty()
                ? Map.of()
                : itemRepository.findAllById(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, Item::getName));

        return proposals.stream()
                .map(proposal -> proposalMapper.toResponse(
                        proposal,
                        categoryNames.getOrDefault(proposal.getCategoryId(), "Không xác định"),
                        proposal.getItemId() != null ? itemNames.get(proposal.getItemId()) : null
                ))
                .toList();
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateProposalResponse updateProposal(
            UUID proposalId,
            UpdateTemplateProposalRequest request
    ) {
        categoryRepository.lockCatalog();
        CategoryQuestionTemplateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Template proposal not found"));
        if (proposal.getStatus() != TemplateProposalStatus.PENDING) {
            throw new ConflictException("Only PENDING proposals can be edited");
        }
        String normalized = CatalogManagementServiceImpl.normalizeTemplateText(
                request != null ? request.templateText() : null
        );
        if (normalized.isBlank()) {
            throw new BadRequestException("templateText is required");
        }

        proposal.setTemplateText(normalized);
        CategoryQuestionTemplateProposal saved = proposalRepository.saveAndFlush(proposal);
        return mapSingleProposal(saved);
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateResponse approveProposal(
            UUID proposalId,
            UUID adminId,
            ApproveTemplateProposalRequest request
    ) {
        categoryRepository.lockCatalog();
        CategoryQuestionTemplateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Template proposal not found"));
        if (proposal.getStatus() != TemplateProposalStatus.PENDING) {
            throw new ConflictException("Only PENDING proposals can be approved");
        }

        String rawCandidate = (request != null && request.templateText() != null && !request.templateText().isBlank())
                ? request.templateText()
                : proposal.getTemplateText();
        String finalText = CatalogManagementServiceImpl.normalizeTemplateText(rawCandidate);
        if (finalText.isBlank()) {
            throw new BadRequestException("templateText is required");
        }

        if (!categoryRepository.existsById(proposal.getCategoryId())) {
            throw new NotFoundException("Category not found");
        }
        if (proposal.getItemId() != null && !itemRepository.existsById(proposal.getItemId())) {
            throw new NotFoundException("Item not found");
        }

        Optional<CategoryQuestionTemplate> existingOfficial = findOfficialTemplate(
                proposal.getCategoryId(),
                proposal.getItemId()
        );
        CategoryQuestionTemplate official = existingOfficial.orElseGet(() -> {
            CategoryQuestionTemplate created = new CategoryQuestionTemplate();
            created.setCategoryId(proposal.getCategoryId());
            created.setItemId(proposal.getItemId());
            return created;
        });
        official.setTemplateText(finalText);
        CategoryQuestionTemplate savedOfficial = templateRepository.saveAndFlush(official);

        proposal.setTemplateText(finalText);
        proposal.setStatus(TemplateProposalStatus.APPROVED);
        proposal.setReviewedBy(adminId);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectionReason(null);
        proposalRepository.saveAndFlush(proposal);

        return templateMapper.toResponse(savedOfficial);
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateProposalResponse rejectProposal(
            UUID proposalId,
            UUID adminId,
            RejectTemplateProposalRequest request
    ) {
        categoryRepository.lockCatalog();
        CategoryQuestionTemplateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Template proposal not found"));
        if (proposal.getStatus() != TemplateProposalStatus.PENDING) {
            throw new ConflictException("Only PENDING proposals can be rejected");
        }

        String reason = request != null && request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : null;
        proposal.setStatus(TemplateProposalStatus.REJECTED);
        proposal.setReviewedBy(adminId);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectionReason(reason);
        CategoryQuestionTemplateProposal saved = proposalRepository.saveAndFlush(proposal);
        return mapSingleProposal(saved);
    }

    private LookupContext loadLookupContext(UUID categoryId, UUID itemId) {
        if (categoryId == null) {
            throw new BadRequestException("A valid categoryId is required");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("A valid categoryId is required"));

        String itemName = null;
        if (itemId != null) {
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new BadRequestException("Invalid itemId"));
            if (!categoryId.equals(item.getCategory().getId())) {
                throw new BadRequestException("itemId does not belong to categoryId");
            }
            itemName = item.getName();
        }

        Optional<CategoryQuestionTemplate> exactOfficial = findOfficialTemplate(categoryId, itemId);
        if (exactOfficial.isPresent() && exactOfficial.get().getTemplateText() != null
                && !exactOfficial.get().getTemplateText().isBlank()) {
            String normalized = CatalogManagementServiceImpl.normalizeTemplateText(
                    exactOfficial.get().getTemplateText()
            );
            return new LookupContext(
                    category.getName(),
                    itemName,
                    null,
                    new ResolvedQuestionTemplateResponse(exactOfficial.get().getId(), normalized, false)
            );
        }

        String categoryDefaultText = null;
        if (itemId != null) {
            categoryDefaultText = templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)
                    .map(CategoryQuestionTemplate::getTemplateText)
                    .map(CatalogManagementServiceImpl::normalizeTemplateText)
                    .filter(text -> !text.isBlank())
                    .orElse(null);
        }

        return new LookupContext(category.getName(), itemName, categoryDefaultText, null);
    }

    private ResolvedQuestionTemplateResponse incrementExistingPendingProposal(UUID categoryId, UUID itemId) {
        Optional<CategoryQuestionTemplateProposal> existingPending = findPendingProposal(categoryId, itemId);
        if (existingPending.isEmpty()) {
            return null;
        }
        CategoryQuestionTemplateProposal pending = existingPending.get();
        String normalized = CatalogManagementServiceImpl.normalizeTemplateText(pending.getTemplateText());
        if (normalized.isBlank()) {
            return null;
        }
        pending.setRequestCount(pending.getRequestCount() + 1);
        proposalRepository.saveAndFlush(pending);
        return new ResolvedQuestionTemplateResponse(null, normalized, true);
    }

    private ProposalPersistenceResult persistPendingProposalAtomically(
            UUID categoryId,
            UUID itemId,
            UUID sellerId,
            String sanitizedTemplateText
    ) {
        categoryRepository.lockCatalog();

        Optional<CategoryQuestionTemplate> officialNow = findOfficialTemplate(categoryId, itemId);
        if (officialNow.isPresent() && officialNow.get().getTemplateText() != null
                && !officialNow.get().getTemplateText().isBlank()) {
            String officialText = CatalogManagementServiceImpl.normalizeTemplateText(
                    officialNow.get().getTemplateText()
            );
            return new ProposalPersistenceResult(
                    new ResolvedQuestionTemplateResponse(officialNow.get().getId(), officialText, false),
                    null,
                    List.of()
            );
        }

        Optional<CategoryQuestionTemplateProposal> pendingNow = findPendingProposal(categoryId, itemId);
        if (pendingNow.isPresent()) {
            CategoryQuestionTemplateProposal pending = pendingNow.get();
            pending.setRequestCount(pending.getRequestCount() + 1);
            proposalRepository.saveAndFlush(pending);
            String pendingText = CatalogManagementServiceImpl.normalizeTemplateText(pending.getTemplateText());
            return new ProposalPersistenceResult(
                    new ResolvedQuestionTemplateResponse(null, pendingText, true),
                    null,
                    List.of()
            );
        }

        CategoryQuestionTemplateProposal created = new CategoryQuestionTemplateProposal();
        created.setCategoryId(categoryId);
        created.setItemId(itemId);
        created.setTemplateText(sanitizedTemplateText);
        created.setStatus(TemplateProposalStatus.PENDING);
        created.setTriggeredBy(sellerId);
        created.setRequestCount(1);
        CategoryQuestionTemplateProposal saved = proposalRepository.saveAndFlush(created);

        List<UUID> adminIds = userRepository.findActiveCatalogAdminIds();
        return new ProposalPersistenceResult(
                new ResolvedQuestionTemplateResponse(null, sanitizedTemplateText, true),
                saved.getId(),
                adminIds
        );
    }

    private void notifyAdminsOfNewProposal(
            UUID proposalId,
            String categoryName,
            String itemName,
            List<UUID> adminIds
    ) {
        String targetLabel = (itemName != null && !itemName.isBlank())
                ? String.format("%s (%s)", itemName, categoryName)
                : String.format("Chung toàn danh mục %s", categoryName);
        String title = "Có kịch bản câu hỏi AI mới chờ duyệt";
        String message = String.format(
                "AI vừa khởi tạo tạm kịch bản câu hỏi thẩm định cho \"%s\". Vui lòng kiểm tra, tinh chỉnh và duyệt lưu vào cơ sở dữ liệu.",
                targetLabel
        );

        for (UUID adminId : adminIds) {
            try {
                notificationService.notifyInApp(
                        adminId,
                        title,
                        message,
                        NotificationType.SYSTEM,
                        proposalId,
                        PROPOSAL_REFERENCE_TYPE,
                        ADMIN_CATALOG_AI_ACTION_URL
                );
            } catch (Exception ex) {
                log.warn("Failed to dispatch template proposal notification to admin {}: {}",
                        adminId, ex.getMessage());
            }
        }
    }

    private Optional<CategoryQuestionTemplate> findOfficialTemplate(UUID categoryId, UUID itemId) {
        return itemId != null
                ? templateRepository.findByCategoryIdAndItemId(categoryId, itemId)
                : templateRepository.findByCategoryIdAndItemIdIsNull(categoryId);
    }

    private Optional<CategoryQuestionTemplateProposal> findPendingProposal(UUID categoryId, UUID itemId) {
        return itemId != null
                ? proposalRepository.findByCategoryIdAndItemIdAndStatus(categoryId, itemId, TemplateProposalStatus.PENDING)
                : proposalRepository.findByCategoryIdAndItemIdIsNullAndStatus(categoryId, TemplateProposalStatus.PENDING);
    }

    private CategoryQuestionTemplateProposalResponse mapSingleProposal(CategoryQuestionTemplateProposal proposal) {
        String categoryName = categoryRepository.findById(proposal.getCategoryId())
                .map(Category::getName)
                .orElse("Không xác định");
        String itemName = proposal.getItemId() != null
                ? itemRepository.findById(proposal.getItemId()).map(Item::getName).orElse(null)
                : null;
        return proposalMapper.toResponse(proposal, categoryName, itemName);
    }

    private String generateWithGatewayFallback(String promptText, String categoryName, String itemName) {
        try {
            String googleReply = googleChatGateway.complete(promptText);
            if (googleReply != null && !googleReply.isBlank()) {
                return googleReply;
            }
            log.warn("Google Gemini returned blank template for category='{}', item='{}'; falling back to Ollama.",
                    categoryName, itemName);
        } catch (Exception googleEx) {
            log.warn("Google Gemini failed to generate template for category='{}', item='{}': {}. Falling back to Ollama.",
                    categoryName, itemName, googleEx.getMessage());
        }

        try {
            String ollamaReply = ollamaChatGateway.complete(
                    new Prompt(
                            List.of(new UserMessage(promptText)),
                            OllamaChatOptions.builder().model(ollamaModel).build()
                    )
            );
            if (ollamaReply != null && !ollamaReply.isBlank()) {
                return ollamaReply;
            }
        } catch (Exception ollamaEx) {
            log.warn("Ollama fallback failed to generate template for category='{}', item='{}': {}",
                    categoryName, itemName, ollamaEx.getMessage());
        }
        return null;
    }

    static String sanitizeGeneratedTemplate(String raw) {
        String normalized = CatalogManagementServiceImpl.normalizeTemplateText(raw);
        if (normalized.isBlank()) {
            return "";
        }
        List<String> validLines = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String cleaned = line.trim()
                    .replaceFirst("^(?:\\d+[\\.\\)]|\\-|•)\\s*", "").replaceAll("\\*\\*\\[([^\\]]+)\\](\\??)\\*\\*", "**$1$2**").replaceAll("^\\[([^\\]]+)\\](\\??)", "$1$2")
                    .trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.contains("?") || cleaned.startsWith("**")) {
                validLines.add(cleaned);
            }
        }
        return String.join("\n", validLines).trim();
    }

    static String buildSpecializedTemplatePrompt(
            String categoryName,
            String itemName,
            String categoryDefaultTemplateText
    ) {
        String targetProduct = (itemName != null && !itemName.isBlank())
                ? String.format("loại sản phẩm \"%s\" (thuộc danh mục \"%s\")", itemName, categoryName)
                : String.format("danh mục sản phẩm \"%s\"", categoryName);
        String targetShort = (itemName != null && !itemName.isBlank()) ? itemName : categoryName;

        String categoryReferenceBlock = (categoryDefaultTemplateText != null && !categoryDefaultTemplateText.isBlank())
                ? String.format("""

                        THAM KHẢO KỊCH BẢN NỀN CỦA DANH MỤC "%s" (Giữ tinh thần các câu hỏi hiện trạng cốt lõi và bổ sung thêm câu hỏi thông số kỹ thuật chuyên sâu riêng cho "%s"):
                        %s
                        """, categoryName, targetShort, categoryDefaultTemplateText)
                : "";

        return String.format("""
                Bạn là chuyên gia thẩm định đồ gia dụng & nội thất cũ hàng đầu tại thị trường Việt Nam.
                Hãy thiết kế bộ câu hỏi khảo sát chuyên sâu gồm ĐÚNG 5 ĐẾN 6 CÂU HỎI bằng tiếng Việt dành riêng cho %s.
                Bộ câu hỏi này giúp người bán trả lời nhanh bằng gợi ý chọn sẵn để AI ĐỊNH GIÁ KHẤU HAO CHÍNH XÁC và TẠO MÔ TẢ ĐĂNG BÁN CHI TIẾT.

                QUY TẮC CẤM TUYỆT ĐỐI (Người bán ĐÃ nhập ở Bước 1):
                - KHÔNG hỏi tên danh mục hay tên loại sản phẩm.
                - KHÔNG hỏi thương hiệu / hãng sản xuất.
                - KHÔNG hỏi năm mua, thời gian mua, mua từ bao giờ hay đã dùng bao lâu.
                - KHÔNG hỏi giá mua mới / giá gốc lúc mua.
                %s
                CẤU TRÚC BẮT BUỘC GỒM 2 PHẦN (TỔNG CỘNG 5 - 6 CÂU HỎI):

                PHẦN 1 — GIỮ ĐẦY ĐỦ CHI TIẾT 3 CÂU HỎI CỐT LÕI VỀ HIỆN TRẠNG & KHẤU HAO:
                1. Câu 1 — Tần suất và cường độ sử dụng thực tế? (Ví dụ phân biệt rõ: Mới dùng lướt vài lần cất kho, Dùng gia đình ít 2-3 lần/tuần, Dùng hằng ngày ổn định, Dùng cường độ cao).
                2. Câu 2 — Tình trạng ngoại quan thực tế và phụ kiện đi kèm đặc thù của "%s"? (Ví dụ phải nhắc đúng phụ kiện thực tế của "%s" như remote giọng nói/chân đế với Tivi, khay kệ với Tủ lạnh, dây cấp/ống xả với Máy giặt, lòng nồi/vỉ hứng dầu với Nồi chiên, nệm lót/gối tựa với Sofa...).
                3. Câu 3 — Tình trạng vận hành kỹ thuật, lịch sử sửa chữa và bảo hành? (Ví dụ sát đặc tính kỹ thuật của "%s": nguyên zin 100%% chưa bung sửa, vận hành êm ổn định không lỗi vặt, còn bảo hành hãng hay đã hết bảo hành).

                PHẦN 2 — BẮT BUỘC BỔ SUNG 2 ĐẾN 3 CÂU HỎI CHUYÊN BIỆT VỀ THÔNG SỐ KỸ THUẬT CỦA RIÊNG "%s":
                TUYỆT ĐỐI KHÔNG gộp thành 1 câu hỏi chung chung kiểu "Thông số kỹ thuật là gì?". Phải tách thành 2 - 3 câu hỏi trực diện vào đúng thông số quyết định giá trị của "%s":
                - Nếu là Tivi / Màn hình:
                  + Hỏi kích thước màn hình bao nhiêu inch? (Ví dụ: 32 inch, 43 inch, 50 inch, 55 inch, 65 inch, Trên 75 inch)
                  + Hỏi độ phân giải và công nghệ Smart TV? (Ví dụ: Full HD thường, Smart TV 4K UHD, QLED / MiniLED 4K, OLED cao cấp điều khiển giọng nói)
                  + Hỏi chất lượng tấm nền hiển thị và loa âm thanh? (Ví dụ: Màn sáng đẹp không điểm chết, Không kẻ sọc không ám màu, Loa to rõ không rè, Có vết xước hoặc đốm sáng nhẹ)
                - Nếu là Máy lạnh / Điều hòa:
                  + Hỏi công suất làm lạnh bao nhiêu ngựa (HP / BTU)? (Ví dụ: 1 HP - 9000 BTU, 1.5 HP - 12000 BTU, 2 HP - 18000 BTU, 2.5 HP - 24000 BTU)
                  + Hỏi loại điều hòa 1 chiều hay 2 chiều và công nghệ Inverter? (Ví dụ: 1 chiều Inverter tiết kiệm điện, 2 chiều Inverter sưởi ấm, 1 chiều Mono tiêu chuẩn, Dòng cao cấp lọc khí khử mùi)
                - Nếu là Tủ lạnh / Tủ đông / Tủ mát:
                  + Hỏi dung tích sử dụng khoảng bao nhiêu lít? (Ví dụ: Mini dưới 100 lít, 150 - 250 lít, 260 - 400 lít, Trên 400 lít cỡ lớn)
                  + Hỏi kiểu thiết kế cánh tủ và công nghệ Inverter? (Ví dụ: 2 cánh ngăn đá trên Inverter, Ngăn đá dưới Inverter, Side by Side / 4 cánh lấy nước ngoài, Tủ tiêu chuẩn không Inverter)
                - Nếu là Máy giặt / Máy sấy quần áo:
                  + Hỏi khối lượng giặt hoặc sấy tối đa bao nhiêu kg? (Ví dụ: Dưới 8 kg, 8.5 - 9.5 kg, 10 - 11 kg, Trên 12 kg hoặc giặt sấy kết hợp)
                  + Hỏi thiết kế cửa lồng giặt và công nghệ truyền động? (Ví dụ: Cửa trước lồng ngang Inverter, Cửa trên lồng đứng Inverter, Truyền động trực tiếp êm ái, Cửa trên dây curoa tiêu chuẩn)
                - Nếu là Thiết bị nhà bếp (Nồi chiên không dầu, Nồi cơm điện, Lò vi sóng, Lò nướng, Bếp từ, Máy rửa bát, Máy lọc nước):
                  + Hỏi dung tích (lít), công suất (W), số vùng nấu hoặc số bộ bát đĩa/lõi lọc tương ứng với "%s"?
                  + Hỏi công nghệ gia nhiệt/lọc và bảng điều khiển (Cao tần IH, cảm ứng LED, núm vặn cơ, đối lưu, RO nóng lạnh...)?
                - Nếu là Nội thất / Bàn ghế / Giường / Tủ quần áo / Sofa / Nệm:
                  + Hỏi kích thước thực tế (Dài x Rộng x Cao hoặc khổ giường/nệm 1m6x2m, 1m8x2m...)?
                  + Hỏi chất liệu chính và kết cấu khung/bề mặt (Gỗ tự nhiên, MDF chống ẩm, Da/Nỉ, Cao su thiên nhiên/Lò xo túi...)?
                - Với bất kỳ mặt hàng "%s" nào khác: Hãy suy luận chính xác 2 - 3 thông số kỹ thuật quan trọng nhất mà người mua đồ cũ tại Việt Nam luôn hỏi đầu tiên.

                ĐỊNH DẠNG ĐẦU RA BẮT BUỘC:
                - Chỉ trả về đúng 5 đến 6 dòng câu hỏi, mỗi câu nằm trên MỘT dòng riêng biệt theo đúng cú pháp:
                  **Nội dung câu hỏi cụ thể?** (Ví dụ: gợi ý 1, gợi ý 2, gợi ý 3, gợi ý 4)
                - KHÔNG bọc câu hỏi trong dấu ngoặc vuông [ ], KHÔNG đánh số thứ tự đầu dòng bên ngoài dấu **, KHÔNG viết lời mở đầu hay kết luận.
                """,
                targetProduct,
                categoryReferenceBlock,
                targetShort,
                targetShort,
                targetShort,
                targetShort,
                targetShort,
                targetShort,
                targetShort
        );
    }

    static String buildFallbackTemplateWithCategoryBase(
            String categoryName,
            String itemName,
            String categoryDefaultTemplateText
    ) {
        String specializedFallback = buildStandardFallbackTemplate(categoryName, itemName);
        if (categoryDefaultTemplateText == null || categoryDefaultTemplateText.isBlank()) {
            return specializedFallback;
        }
        List<String> specQuestions = extractSpecializedSpecLines(categoryName, itemName);
        if (specQuestions.isEmpty()) {
            return categoryDefaultTemplateText;
        }
        return categoryDefaultTemplateText.trim() + "\n" + String.join("\n", specQuestions);
    }

    public static String buildStandardFallbackTemplate(String categoryName, String itemName) {
        String target = (itemName != null && !itemName.isBlank()) ? itemName : categoryName;
        String lower = ((categoryName != null ? categoryName : "") + " " + (itemName != null ? itemName : ""))
                .toLowerCase(Locale.ROOT);

        String accessoryExamples = resolveAccessoryExamples(lower);
        String operationExamples = resolveOperationExamples(lower);

        List<String> lines = new ArrayList<>();
        lines.add("**Tần suất và mức độ sử dụng thực tế của sản phẩm?** (Ví dụ: Dùng hằng ngày ổn định, Dùng ít 2-3 lần/tuần, Mới dùng lướt vài lần cất kho, Chưa từng sử dụng)");
        lines.add(String.format("**Tình trạng ngoại quan của %s và phụ kiện đi kèm?** (Ví dụ: %s)", target, accessoryExamples));
        lines.add(String.format("**Tình trạng vận hành kỹ thuật, lịch sử sửa chữa và bảo hành?** (Ví dụ: %s)", operationExamples));
        lines.addAll(extractSpecializedSpecLines(categoryName, itemName));
        return String.join("\n", lines);
    }

    private static String resolveAccessoryExamples(String lower) {
        if (lower.contains("tivi") || lower.contains("tv") || lower.contains("màn hình")) {
            return "Đẹp 98-99% đủ điều khiển giọng nói + chân đế, Kèm giá treo tường + dây nguồn zin, Viền trầy nhẹ đủ remote, Fullbox đủ phụ kiện";
        }
        if (lower.contains("máy lạnh") || lower.contains("điều hòa")) {
            return "Dàn nóng + dàn lạnh đẹp đủ remote zin, Trắng sáng không ố vàng kèm giá đỡ, Có trầy nhẹ vỏ ngoài đủ điều khiển, Mới 98% đủ phụ kiện";
        }
        if (lower.contains("tủ lạnh") || lower.contains("tủ đông") || lower.contains("tủ mát")) {
            return "Đẹp 98% đủ toàn bộ khay kính + hộp đá, Vỏ ngoài có xước nhẹ không móp méo, Gioăng cao su kín khít đủ khay kệ, Mới đẹp nguyên bản";
        }
        if (lower.contains("máy giặt") || lower.contains("máy sấy")) {
            return "Vỏ đẹp không rỉ sét đủ dây cấp + ống xả, Lồng giặt inox sáng sạch đẹp 98%, Trầy nhẹ bên hông đủ phụ kiện lắp đặt, Như mới full phụ kiện";
        }
        if (lower.contains("sofa") || lower.contains("bàn") || lower.contains("ghế")
                || lower.contains("giường") || lower.contains("tủ quần áo") || lower.contains("nội thất")) {
            return "Bề mặt đẹp 95-99% không trầy rách, Khung chắc chắn kèm đủ đệm gối/phụ kiện, Có vết xước nhẹ không ảnh hưởng thẩm mỹ, Còn rất mới sạch sẽ";
        }
        return "Đẹp như mới 98-99% fullbox đủ phụ kiện, Ngoại hình đẹp đủ phụ kiện cơ bản, Trầy xước nhẹ theo thời gian không nứt vỡ, Thiếu hộp nhưng đủ dây/phụ kiện chính";
    }

    private static String resolveOperationExamples(String lower) {
        if (lower.contains("tivi") || lower.contains("tv") || lower.contains("màn hình")) {
            return "Nguyên zin chưa sửa chữa kết nối Wifi mượt, Hoạt động hoàn hảo còn bảo hành hãng, Nguyên bản chạy ổn định đã hết bảo hành, Đã bảo hành/thay đèn nền một lần";
        }
        if (lower.contains("máy lạnh") || lower.contains("điều hòa")) {
            return "Làm lạnh sâu block êm nguyên zin chưa sửa, Gas zin chạy êm tiết kiệm điện còn bảo hành, Hoạt động tốt đã vệ sinh bảo dưỡng sạch, Đã từng bơm bổ sung gas";
        }
        if (lower.contains("tủ lạnh") || lower.contains("tủ đông") || lower.contains("tủ mát")) {
            return "Làm lạnh sâu làm đá nhanh block êm nguyên zin, Không đóng tuyết chạy êm còn bảo hành hãng, Vận hành ổn định hết bảo hành chưa sửa chữa, Đã thay rơ-le/bảo dưỡng nhẹ";
        }
        if (lower.contains("máy giặt") || lower.contains("máy sấy")) {
            return "Giặt sạch vắt êm không rung lắc nguyên zin, Motor chạy êm bảng mạch tốt còn bảo hành, Hoạt động hoàn hảo mọi chế độ hết bảo hành, Đã vệ sinh lồng giặt/bảo dưỡng định kỳ";
        }
        if (lower.contains("sofa") || lower.contains("bàn") || lower.contains("ghế")
                || lower.contains("giường") || lower.contains("tủ quần áo") || lower.contains("nệm")
                || lower.contains("đệm") || lower.contains("nội thất")) {
            return "Kết cấu nguyên bản chắc chắn không mối mọt, Không ọp ẹp không xẹp lún sử dụng tốt, Bản lề/ốc vít trơn tru nguyên zin, Đã gia cố hoặc bọc lại bề mặt";
        }
        return "Nguyên zin 100% chạy êm chưa từng sửa chữa, Hoạt động tốt mọi chức năng còn bảo hành hãng, Vận hành ổn định đã hết bảo hành, Có lỗi nhẹ hoặc đã bảo dưỡng một lần";
    }

    private static List<String> extractSpecializedSpecLines(String categoryName, String itemName) {
        String target = (itemName != null && !itemName.isBlank()) ? itemName : categoryName;
        String lower = ((categoryName != null ? categoryName : "") + " " + (itemName != null ? itemName : ""))
                .toLowerCase(Locale.ROOT);

        if (lower.contains("tivi") || lower.contains("tv") || lower.contains("màn hình")) {
            return List.of(
                    "**Kích thước màn hình Tivi là bao nhiêu inch?** (Ví dụ: 32 inch, 43 inch, 50 inch, 55 inch, 65 inch, Trên 75 inch)",
                    "**Độ phân giải màn hình và hệ điều hành Smart TV?** (Ví dụ: Full HD thường, Smart TV 4K UHD, QLED / MiniLED 4K, OLED cao cấp tìm kiếm giọng nói)",
                    "**Chất lượng tấm nền hiển thị và loa âm thanh hiện tại?** (Ví dụ: Màn sáng đẹp không điểm chết, Không sọc không ám màu, Loa to rõ không rè, Có đốm sáng nhẹ ở góc)"
            );
        }
        if (lower.contains("máy lạnh") || lower.contains("điều hòa")) {
            return List.of(
                    "**Công suất làm lạnh của máy là bao nhiêu HP (Ngựa / BTU)?** (Ví dụ: 1 HP - 9000 BTU, 1.5 HP - 12000 BTU, 2 HP - 18000 BTU, 2.5 HP - 24000 BTU)",
                    "**Loại máy 1 chiều hay 2 chiều và công nghệ tiết kiệm điện?** (Ví dụ: 1 chiều Inverter tiết kiệm điện, 2 chiều Inverter sưởi ấm, 1 chiều Mono tiêu chuẩn, Dòng cao cấp lọc không khí)"
            );
        }
        if (lower.contains("tủ lạnh") || lower.contains("tủ đông") || lower.contains("tủ mát")) {
            return List.of(
                    "**Dung tích sử dụng của tủ khoảng bao nhiêu lít?** (Ví dụ: Mini dưới 100 lít, 150 - 250 lít, 260 - 400 lít, Trên 400 lít cỡ lớn)",
                    "**Kiểu thiết kế cánh tủ và công nghệ Inverter?** (Ví dụ: 2 cánh ngăn đá trên Inverter, 2 cánh ngăn đá dưới Inverter, Side by Side / 4 cánh, Tủ tiêu chuẩn không Inverter)"
            );
        }
        if (lower.contains("máy giặt") || lower.contains("máy sấy")) {
            return List.of(
                    "**Khối lượng giặt / sấy tối đa là bao nhiêu kg?** (Ví dụ: Dưới 8 kg, 8.5 - 9.5 kg, 10 - 11 kg, Trên 12 kg hoặc giặt sấy kết hợp)",
                    "**Thiết kế cửa lồng giặt và công nghệ truyền động?** (Ví dụ: Cửa trước lồng ngang Inverter, Cửa trên lồng đứng Inverter, Truyền động trực tiếp êm ái, Cửa trên thông thường)"
            );
        }
        if (lower.contains("nồi chiên") || lower.contains("lò vi sóng") || lower.contains("lò nướng")
                || lower.contains("nồi cơm") || lower.contains("nồi áp suất")) {
            return List.of(
                    String.format("**Dung tích và công suất của %s là bao nhiêu?** (Ví dụ: 1.8 lít tiêu chuẩn, 5 - 6 lít gia đình, 8 - 12 lít cỡ lớn, 20 - 30 lít công suất cao)", target),
                    "**Công nghệ gia nhiệt và bảng điều khiển của máy?** (Ví dụ: Điện tử cảm ứng màn hình LED, Cao tần IH đa chế độ, Núm vặn cơ bền bỉ dễ dùng, Có nướng đối lưu / kính quan sát)"
            );
        }
        if (lower.contains("bếp từ") || lower.contains("bếp điện") || lower.contains("bếp hồng ngoại")) {
            return List.of(
                    "**Số vùng nấu và tổng công suất của bếp là bao nhiêu?** (Ví dụ: Bếp đơn 2000W, Bếp đôi 4000W lắp âm/dương, Bếp từ kết hợp hồng ngoại, Bếp 3 vùng nấu Châu Âu)",
                    "**Chất liệu mặt kính và bảng điều khiển cảm ứng?** (Ví dụ: Kính Ceramic/Schott Ceran nguyên vẹn, Cảm ứng trượt nhạy có khóa trẻ em, Phím bấm cảm ứng độc lập, Có hẹn giờ & Inverter)"
            );
        }
        if (lower.contains("bình nóng lạnh") || lower.contains("máy nước nóng")) {
            return List.of(
                    "**Loại máy trực tiếp hay gián tiếp và dung tích bình chứa?** (Ví dụ: Gián tiếp 15 lít, Gián tiếp 20 - 30 lít, Trực tiếp có bơm trợ lực, Trực tiếp không bơm)",
                    "**Trang bị chống giật ELCB và thanh đốt còn tốt không?** (Ví dụ: Có chống giật ELCB an toàn hoạt động tốt, Nước nóng nhanh giữ nhiệt lâu, Bình tráng men không rò nước, Đầy đủ vòi sen/dây cấp)"
            );
        }
        if (lower.contains("nệm") || lower.contains("đệm")) {
            return List.of(
                    "**Kích thước khổ nệm (Rộng x Dài) và độ dày bao nhiêu cm?** (Ví dụ: 1m6 x 2m dày 10cm, 1m8 x 2m dày 15cm, 1m2 x 2m dày 9cm, 2m x 2m2 dày 20cm)",
                    "**Chất liệu ruột nệm và thiết kế gấp hay nguyên tấm?** (Ví dụ: Cao su thiên nhiên nguyên tấm, Lò xo túi độc lập êm ái, Bông ép gấp 3 gọn nhẹ, Foam hoạt tính thoáng khí)"
            );
        }
        if (lower.contains("sofa") || lower.contains("bàn") || lower.contains("ghế")
                || lower.contains("giường") || lower.contains("tủ quần áo") || lower.contains("kệ")
                || lower.contains("nội thất")) {
            return List.of(
                    String.format("**Kích thước thực tế (Dài x Rộng x Cao) của %s là bao nhiêu?** (Ví dụ: 1m6 x 2m, 1m8 x 2m, Dài 1m2 x Rộng 60cm, Sofa văng 2m / góc chữ L 2m4)", target),
                    String.format("**Chất liệu chính và kết cấu khung của %s?** (Ví dụ: Gỗ tự nhiên sồi/xoan đào, Gỗ công nghiệp MDF lõi xanh chống ẩm, Bọc da công nghiệp / vải nỉ cao cấp, Khung sắt sơn tĩnh điện)", target)
            );
        }
        return List.of(
                String.format("**Thông số kích thước, dung tích hoặc công suất chính của %s?** (Ví dụ: Cỡ nhỏ mini tiết kiệm diện tích, Cỡ vừa tiêu chuẩn gia đình, Dung tích/công suất lớn, Phiên bản cao cấp)", target),
                String.format("**Phiên bản công nghệ hoặc tính năng nổi bật của %s?** (Ví dụ: Có Inverter tiết kiệm điện, Điều khiển điện tử thông minh, Cơ bản dễ sử dụng bền bỉ, Đầy đủ chế độ an toàn)", target)
        );
    }

    private <T> T executeRead(Supplier<T> action) {
        if (readTransactionTemplate != null) {
            return readTransactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private <T> T executeWrite(Supplier<T> action) {
        if (writeTransactionTemplate != null) {
            return writeTransactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private record LookupContext(
            String categoryName,
            String itemName,
            String categoryDefaultTemplateText,
            ResolvedQuestionTemplateResponse officialTemplate
    ) {
    }

    private record ProposalPersistenceResult(
            ResolvedQuestionTemplateResponse resolvedResponse,
            UUID newlyCreatedProposalId,
            List<UUID> adminIds
    ) {
    }
}
