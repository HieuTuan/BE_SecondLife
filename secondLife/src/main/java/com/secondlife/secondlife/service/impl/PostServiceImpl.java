package com.secondlife.secondlife.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.dto.request.AcceptDescriptionRequest;
import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.request.UpdateDraftRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
import com.secondlife.secondlife.dto.response.PostDto;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
import com.secondlife.secondlife.dto.response.ResolvedQuestionTemplateResponse;
import com.secondlife.secondlife.entity.AiChatMessage;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.PostImage;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.CategoryTemplateService;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.ListingCreditService;
import com.secondlife.secondlife.service.ListingFingerprint;
import com.secondlife.secondlife.service.ListingImageSimilarity;
import com.secondlife.secondlife.service.ListingPublicationService;
import com.secondlife.secondlife.service.ListingReviewService;
import com.secondlife.secondlife.service.PostService;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostServiceImpl implements PostService {
    private final PostMapper postMapper;
    private final BigDecimal inspectionFee;
    private final BigDecimal shippingFee;
    private final int maxUnfinishedDrafts;
    private final long maxImageBytes;
    private final int minImages;
    private final int maxImages;
    private final ListingImageSimilarity imageSimilarity;
    private final ListingFingerprint listingFingerprint;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ListingCreditService listingCredits;
    private final ListingAccessService listingAccess;
    private final AiChatService aiChatService;
    private final CategoryTemplateService categoryTemplateService;
    private final CategoryQuestionTemplateRepository templateRepository;
    private final AiChatSessionRepository sessionRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final AiChatGateway chatGateway;
    private final CloudinaryService cloudinaryService;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final AiChatGateway googleChatGateway;
    private final AiChatMessageRepository chatMessageRepository;
    private final ListingPublicationService publication;
    private final ListingReviewService reviews;
    private final TransactionTemplate transactionTemplate;

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           ListingCreditService listingCredits,
                           ListingAccessService listingAccess,
                           AiChatService aiChatService,
                           CategoryTemplateService categoryTemplateService,
                           CategoryQuestionTemplateRepository templateRepository,
                           AiChatSessionRepository sessionRepository,
                           AiChatMessageRepository chatMessageRepository,
                           CategoryRepository categoryRepository,
                           ItemRepository itemRepository,
                           @Qualifier("defaultChatGateway") AiChatGateway chatGateway,
                           CloudinaryService cloudinaryService,
                           InspectionOrderRepository inspectionOrderRepository,
                           @Qualifier("googleChatGateway") AiChatGateway googleChatGateway,
                           ListingPublicationService publication,
                           ListingReviewService reviews,
                           ListingImageSimilarity imageSimilarity,
                           ListingFingerprint listingFingerprint,
                           @Value("${app.inspection.inspection-fee}") BigDecimal inspectionFee,
                           @Value("${app.inspection.shipping-fee}") BigDecimal shippingFee,
                           @Value("${app.listing.max-unfinished-drafts}") int maxUnfinishedDrafts,
                           @Value("${app.listing.max-image-bytes}") long maxImageBytes,
                           @Value("${app.listing.min-images}") int minImages,
                           @Value("${app.listing.max-images}") int maxImages,
                           PostMapper postMapper,
                           PlatformTransactionManager transactions) {
        this.transactionTemplate = transactions != null ? new TransactionTemplate(transactions) : null;
        this.postMapper = postMapper;

        if (inspectionFee.signum() < 0 || shippingFee.signum() < 0)
            throw new IllegalArgumentException("Inspection and shipping fees must be non-negative");

        if (maxUnfinishedDrafts <= 0 || maxImageBytes <= 0)
            throw new IllegalArgumentException("Listing draft and image byte limits must be positive");

        if (minImages <= 0 || maxImages < minImages)
            throw new IllegalArgumentException("Listing image bounds require a positive minimum and maximum at least the minimum");

        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.listingCredits = listingCredits;
        this.listingAccess = listingAccess;
        this.aiChatService = aiChatService;
        this.categoryTemplateService = categoryTemplateService;
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.chatGateway = chatGateway;
        this.cloudinaryService = cloudinaryService;
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.googleChatGateway = googleChatGateway;
        this.chatMessageRepository = chatMessageRepository;
        this.publication = publication;
        this.reviews = reviews;
        this.imageSimilarity = imageSimilarity;
        this.listingFingerprint = listingFingerprint;
        this.inspectionFee = inspectionFee;
        this.shippingFee = shippingFee;
        this.maxUnfinishedDrafts = maxUnfinishedDrafts;
        this.maxImageBytes = maxImageBytes;
        this.minImages = minImages;
        this.maxImages = maxImages;
    }

    @Override
    public PostInitResponse initPost(UUID userId, PostInitRequest request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.categoryId() == null || !categoryRepository.existsById(request.categoryId()))
            throw new BadRequestException("A valid categoryId is required");

        if (request.itemId() != null) {
            var item = itemRepository.findById(request.itemId()).orElseThrow(() -> new BadRequestException("Invalid itemId"));
            if (!request.categoryId().equals(item.getCategory().getId()))
                throw new BadRequestException("itemId does not belong to categoryId");
        }

        if (postRepository.countByUser_IdAndStatusIn(userId, List.of("DRAFT", "REJECTED", "PENDING", "PENDING_INSPECTION")) >= maxUnfinishedDrafts)
            throw new ConflictException("Maximum " + maxUnfinishedDrafts + " unfinished drafts; finish an existing draft first");

        if (request.images() == null || request.images().size() < minImages || request.images().size() > maxImages)
            throw new BadRequestException("Upload between " + minImages + " and " + maxImages + " product images");

        for (var image : request.images()) {
            if (image == null || image.isEmpty() || image.getSize() > maxImageBytes || image.getContentType() == null
                    || !Set.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType()))
                throw new BadRequestException("Every image must be a non-empty JPEG, PNG or WebP no larger than " + maxImageBytes + " bytes");
        }

        List<PostImage> postImages = new ArrayList<>();
        for (var image : request.images()) {
            try {
                byte[] imageBytes = image.getBytes();
                String perceptualFingerprint = imageSimilarity.fingerprint(imageBytes);
                String imageUrl = cloudinaryService.uploadImage(image);
                if (imageUrl == null || imageUrl.isBlank()) throw new IllegalStateException("Image upload returned an empty URL");
                postImages.add(new PostImage(imageUrl,
                        listingFingerprint.sha256(imageBytes), perceptualFingerprint));
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload image to Cloudinary", e);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid or excessively large image dimensions");
            }
        }

        Post post = executeInTransaction(() -> {
            Post p = new Post();
            p.setUser(userRepository.getReferenceById(userId));
            p.setCategoryId(request.categoryId());
            p.setItemId(request.itemId());
            p.setStatus("DRAFT");
            p.getImages().addAll(postImages);
            p.setImageUrl(postImages.getFirst().getImageUrl());
            p.setImageFingerprint(postImages.getFirst().getImageFingerprint());
            return postRepository.save(p);
        });

        boolean isAiMode = request.mode() == null || !"manual".equalsIgnoreCase(request.mode().trim());

        if (isAiMode) {
            // Deduct 1 AI_CHAT credit immediately in an autonomous transaction because AI Vision scan consumes costly GPU/LLM resources
            String chatCreditKey = "AI_INIT_SCAN:" + post.getId() + ":" + UUID.randomUUID();
            listingCredits.consumeImmediately(userId, CreditType.AI_CHAT, chatCreditKey);

            // 3. Call AI to analyze image
            String selectedCategoryName = categoryRepository.findById(request.categoryId()).orElseThrow().getName();
            String selectedItemName = request.itemId() == null ? "Không xác định" : itemRepository.findById(request.itemId()).orElseThrow().getName();
            String declaredBrand = request.brand() != null && !request.brand().isBlank() ? request.brand().trim() : null;

            StringBuilder visionCheckPrompt = new StringBuilder();
            visionCheckPrompt.append("Người bán đang đăng bài với thông tin khai báo ở Bước 1:\n");
            visionCheckPrompt.append(String.format("- Danh mục sản phẩm đã chọn: \"%s\"\n", selectedCategoryName));
            visionCheckPrompt.append(String.format("- Loại thiết bị/sản phẩm đã chọn: \"%s\"\n", selectedItemName));
            if (declaredBrand != null) {
                visionCheckPrompt.append(String.format("- Thương hiệu/Hãng sản xuất người bán đã nhập: \"%s\"\n", declaredBrand));
            }

            visionCheckPrompt.append("""

                    NHIỆM VỤ BẮT BUỘC (KIỂM TRA HÌNH ẢNH SẢN PHẨM):
                    Hãy quan sát thật kỹ toàn bộ các hình ảnh sản phẩm đính kèm để thẩm định 2 tiêu chuẩn:
                    """);

            visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 1 - LOẠI SẢN PHẨM:
                    - Nếu sản phẩm trong ảnh KHÔNG PHẢI là loại "%s" (hoặc không phù hợp với danh mục "%s"):
                      (Ví dụ: Người bán chọn loại "%s" nhưng hình ảnh thực tế lại là thiết bị khác như "Quạt", "Máy giặt", "Nồi chiên", "Bếp", "Bàn ăn", "Tủ lạnh"...):
                      -> BẮT BUỘC TRẢ VỀ CÂU TRẢ LỜI BẮT ĐẦU BẰNG TIỀN TỐ:
                      [CẢNH_BÁO_KHÔNG_KHỚP_LOAI]
                      Sau đó nêu rõ:
                      - Tên sản phẩm thực tế mà bạn nhìn thấy trong ảnh là gì.
                      - Khẳng định rõ ràng sản phẩm thực tế trong ảnh không khớp với loại sản phẩm "%s" mà người bán đã chọn.
                      - Yêu cầu người bán quay lại Bước 1 để chọn đúng Loại sản phẩm hoặc tải lại hình ảnh đúng của "%s".
                      TUYỆT ĐỐI KHÔNG viết mô tả bán hàng nếu hình ảnh sai loại sản phẩm.
                    """, selectedItemName, selectedCategoryName, selectedItemName, selectedItemName, selectedItemName));

            if (declaredBrand != null) {
                visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 2 - THƯƠNG HIỆU / HÃNG SẢN XUẤT:
                    - Nếu sản phẩm trong ảnh đúng là loại "%s", hãy quan sát logo, tên thương hiệu in trên thân sản phẩm, nhãn mác hoặc vỏ hộp:
                    - Nếu trong ảnh NHÌN THẤY RÕ RÀNG thương hiệu thực tế của sản phẩm là một hãng KHÁC BIỆT HOÀN TOÀN so với hãng mà người bán đã nhập ("%s"):
                      (Ví dụ: Người bán nhập "%s" nhưng trên thân sản phẩm in rõ logo/chữ "Tefal", "Sunhouse", "Panasonic", "Philips", "Kangaroo", "Sharp", "Cuckoo", "Electrolux"...):
                      -> BẮT BUỘC TRẢ VỀ CÂU TRẢ LỜI BẮT ĐẦU BẰNG TIỀN TỐ:
                      [CẢNH_BÁO_KHÔNG_KHỚP_HANG: <Tên_Hãng_Thực_Tế_Trong_Ảnh>]
                      Sau đó nêu rõ:
                      - Tên thương hiệu thực tế mà bạn nhìn thấy rõ ràng trên sản phẩm trong ảnh là gì.
                      - Khẳng định thương hiệu trên sản phẩm trong ảnh khác với thương hiệu "%s" mà người bán đã nhập.
                      - Yêu cầu người bán kiểm tra lại để đổi thương hiệu thành hãng trong ảnh hoặc tải lên hình ảnh đúng của hãng "%s".
                      LƯU Ý: Nếu trên ảnh không thể nhìn rõ thương hiệu (ảnh chụp góc khuất, chữ in mờ, hoặc không có logo) thì KHÔNG được cảnh báo sai hãng, hãy coi như hợp lệ. Chỉ cảnh báo khi NHÌN THẤY RÕ mâu thuẫn thương hiệu!
                    """, selectedItemName, declaredBrand, declaredBrand, declaredBrand, declaredBrand));
            }

            visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 3 - NẾU HÌNH ẢNH HỢP LỆ (Đúng loại "%s"%s):
                    -> BẮT ĐẦU BẰNG:
                    [HỢP_LỆ]
                    Sau đó nhận xét ngắn gọn, khách quan về ngoại quan, màu sắc, tình trạng và các đặc điểm nhìn thấy được của sản phẩm để phục vụ đăng bán. Chỉ mô tả sản phẩm nhìn thấy, không suy đoán hãng hay tuổi đời nếu ảnh không chứng minh. Không đề xuất giá, không thêm lời chào, không tự đặt câu hỏi.
                    """, selectedItemName, declaredBrand != null ? " và đúng/không mâu thuẫn hãng " + declaredBrand : ""));

            AiChatRequest aiRequest = new AiChatRequest(
                    null, post.getId(), visionCheckPrompt.toString(), request.images());

            AiChatResponse aiResponse;
            try {
                aiResponse = aiChatService.processChat(aiRequest, userId);
            } catch (RuntimeException ex) {
                log.warn("Post init AI vision scan failed for postId={}; refunding AI_CHAT credit and deleting orphan draft: {}",
                        post.getId(), ex.getMessage());
                final UUID failedPostId = post.getId();
                final String refundKey = chatCreditKey + ":REFUND";
                runInTransaction(() -> {
                    listingCredits.refund(userId, CreditType.AI_CHAT, refundKey);
                    postRepository.deleteById(failedPostId);
                });
                throw ex;
            }
            String aiReply = aiResponse.reply() != null ? aiResponse.reply().trim() : "";

            // 1. Kiểm tra không khớp loại sản phẩm
            boolean isTypeMismatch = aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_LOAI]")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP LOẠI]")
                    || aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP]")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP]")
                    || (!aiReply.contains("[HỢP_LỆ]") && !aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_HANG]") && !aiReply.contains("[CẢNH BÁO KHÔNG KHỚP HÃNG]") && (
                            aiReply.toLowerCase().contains("không khớp với loại")
                            || aiReply.toLowerCase().contains("không phải là loại")
                            || aiReply.toLowerCase().contains("khác với loại")
                       ));

            // 2. Kiểm tra không khớp thương hiệu
            boolean isBrandMismatch = aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_HANG")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP HÃNG")
                    || (!aiReply.contains("[HỢP_LỆ]") && (
                            aiReply.toLowerCase().contains("không khớp với thương hiệu")
                            || aiReply.toLowerCase().contains("khác với thương hiệu")
                            || aiReply.toLowerCase().contains("thương hiệu thực tế trong ảnh là")
                            || aiReply.toLowerCase().contains("thương hiệu thực tế là")
                       ));

            if (isTypeMismatch) {
                String cleanReason = aiReply
                        .replaceAll("\\[CẢNH_BÁO[^\\]]*\\]", "")
                        .replaceAll("\\[CẢNH BÁO[^\\]]*\\]", "")
                        .replace("[KHÔNG_PHÙ_HỢP]", "")
                        .replace("[KHÔNG PHÙ HỢP]", "")
                        .trim();
                log.warn("Post init blocked: Image does not match category/item. Selected: {} / {}, Reason: {}",
                        selectedCategoryName, selectedItemName, cleanReason);
                runInTransaction(() -> postRepository.delete(post));
                throw new BadRequestException("HÌNH ẢNH KHÔNG KHỚP LOẠI SẢN PHẨM: " + cleanReason);
            }

            if (isBrandMismatch) {
                String detectedBrand = "";
                Matcher m = Pattern.compile("\\[CẢNH_BÁO_KHÔNG_KHỚP_HANG:\\s*([^\\]]+)\\]").matcher(aiReply);
                if (m.find()) {
                    detectedBrand = m.group(1).trim();
                }
                String cleanReason = aiReply
                        .replaceAll("\\[CẢNH_BÁO[^\\]]*\\]", "")
                        .replaceAll("\\[CẢNH BÁO[^\\]]*\\]", "")
                        .trim();
                log.warn("Post init blocked: Image does not match brand. Declared: {}, Detected: {}, Reason: {}",
                        declaredBrand, detectedBrand, cleanReason);
                runInTransaction(() -> postRepository.delete(post));
                String brandPrefix = detectedBrand.isEmpty() ? "" : "[DETECTED_BRAND:" + detectedBrand + "] ";
                throw new BadRequestException("HÌNH ẢNH KHÔNG KHỚP THƯƠNG HIỆU: " + brandPrefix + cleanReason);
            }

            // Nếu hợp lệ: làm sạch tiền tố [HỢP_LỆ]
            String cleanDescription = aiReply.replaceFirst("^\\[HỢP_LỆ\\]\\s*", "").trim();

            // 4. Resolve official template or generate temporary specialized AI template (+ submit Admin proposal):
            ResolvedQuestionTemplateResponse resolvedTemplate = categoryTemplateService.resolveOrGenerateForListing(
                    request.categoryId(),
                    request.itemId(),
                    userId
            );
            String templateText = resolvedTemplate.templateText() != null ? resolvedTemplate.templateText() : "";
            final UUID officialTemplateId = resolvedTemplate.officialTemplateId();

            // Update post with session and official template (temporary proposals keep post.template = null)
            final UUID finalPostId = post.getId();
            runInTransaction(() -> {
                Post managed = postRepository.findById(finalPostId).orElse(post);
                AiChatSession session = sessionRepository.findById(aiResponse.sessionId()).orElse(null);
                CategoryQuestionTemplate officialTemplate = officialTemplateId != null
                        ? templateRepository.findById(officialTemplateId).orElse(null)
                        : null;
                managed.setAiDescription(cleanDescription);
                managed.setDescription(null);
                managed.setDescriptionAccepted(false);
                managed.setAiChatSession(session);
                managed.setTemplate(officialTemplate);
                postRepository.save(managed);
            });

            return new PostInitResponse(
                    post.getId(),
                    aiResponse.sessionId(),
                    templateText,
                    cleanDescription);
        } else {
            // Manual mode: No AI visual scan, no credit deducted
            runInTransaction(() -> {
                post.setAiDescription(null);
                post.setDescription(null);
                post.setDescriptionAccepted(false);
                postRepository.save(post);
            });

            return new PostInitResponse(
                    post.getId(),
                    null,
                    null,
                    null);
        }
    }

    @Override
    public PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId) {
        return finalizeChatAndDescription(userId, sessionId, null);
    }

    @Override
    public PostFinalizeResponse finalizeChatAndDescription(UUID userId,
                                                           UUID sessionId,
                                                           String message) {
        // Phase 1: Validate, append optional message, and deduct credit in a short TX
        SessionPrecheck precheck = executeInTransaction(() -> {
            AiChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("Session not found"));
            if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
                throw new ForbiddenException("This chat session belongs to another user");
            }
            if (session.isCompleted()) {
                throw new ConflictException("Session is already completed");
            }

            UUID postId = session.getPostId();
            if (postId != null) {
                Post post = listingAccess.owned(userId, postId, false);
                listingAccess.requireDraft(post);
            }

            String idempotencyKey = "AI_FINALIZE:" + sessionId + ":" + UUID.randomUUID();
            listingCredits.consume(userId, CreditType.AI_CHAT, idempotencyKey);

            if (message != null && !message.isBlank()) {
                AiChatMessage userDbMsg = new AiChatMessage();
                userDbMsg.setSession(session);
                userDbMsg.setRole("USER");
                userDbMsg.setMessageContent(message.trim());
                chatMessageRepository.save(userDbMsg);
                session.setMessageCount(session.getMessageCount() + 1);
                sessionRepository.save(session);
            }

            return new SessionPrecheck(postId, idempotencyKey);
        });

        // Phase 2: Call external AI provider OUTSIDE any DB transaction / row lock
        PostFinalizeResponse finalizeResponse;
        try {
            finalizeResponse = aiChatService.finalizeChat(sessionId, userId);
        } catch (RuntimeException ex) {
            runInTransaction(() -> listingCredits.refund(
                    userId, CreditType.AI_CHAT, precheck.creditKey() + ":REFUND"));
            throw ex;
        }

        // Phase 3: Persist generated description and title in a short TX
        if (precheck.postId() != null) {
            runInTransaction(() -> {
                Post post = listingAccess.owned(userId, precheck.postId(), true);
                listingAccess.requireDraft(post);
                post.setAiDescription(finalizeResponse.description());
                post.setDescription(finalizeResponse.description());
                post.setDescriptionAccepted(false);
                if (finalizeResponse.title() != null && !finalizeResponse.title().isBlank()) {
                    post.setTitle(finalizeResponse.title());
                }
                postRepository.save(post);
            });
        }

        return finalizeResponse;
    }

    @Override
    public PostFinalizeResponse regenerateChatAndDescription(UUID userId, UUID sessionId) {
        return regenerateChatAndDescription(userId, sessionId, null);
    }

    @Override
    public PostFinalizeResponse regenerateChatAndDescription(UUID userId,
                                                             UUID sessionId,
                                                             String message) {
        // Phase 1: Validate, append optional message, and deduct credit in a short TX
        SessionPrecheck precheck = executeInTransaction(() -> {
            AiChatSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("Session not found"));
            if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
                throw new ForbiddenException("This chat session belongs to another user");
            }

            UUID postId = session.getPostId();
            if (postId != null) {
                Post post = listingAccess.owned(userId, postId, false);
                listingAccess.requireDraft(post);
            }

            String idempotencyKey = "AI_REGENERATE:" + sessionId + ":" + UUID.randomUUID();
            listingCredits.consume(userId, CreditType.AI_CHAT, idempotencyKey);

            if (message != null && !message.isBlank()) {
                AiChatMessage userDbMsg = new AiChatMessage();
                userDbMsg.setSession(session);
                userDbMsg.setRole("USER");
                userDbMsg.setMessageContent(message.trim());
                chatMessageRepository.save(userDbMsg);
                session.setMessageCount(session.getMessageCount() + 1);
                sessionRepository.save(session);
            }

            return new SessionPrecheck(postId, idempotencyKey);
        });

        // Phase 2: Call external AI provider OUTSIDE any DB transaction / row lock
        PostFinalizeResponse finalizeResponse;
        try {
            finalizeResponse = aiChatService.regenerateDescription(sessionId, userId);
        } catch (RuntimeException ex) {
            runInTransaction(() -> listingCredits.refund(
                    userId, CreditType.AI_CHAT, precheck.creditKey() + ":REFUND"));
            throw ex;
        }

        // Phase 3: Persist regenerated description and title in a short TX
        if (precheck.postId() != null) {
            runInTransaction(() -> {
                Post post = listingAccess.owned(userId, precheck.postId(), true);
                listingAccess.requireDraft(post);
                post.setAiDescription(finalizeResponse.description());
                post.setDescription(finalizeResponse.description());
                post.setDescriptionAccepted(false);
                if (finalizeResponse.title() != null && !finalizeResponse.title().isBlank()) {
                    post.setTitle(finalizeResponse.title());
                }
                postRepository.save(post);
            });
        }

        return finalizeResponse;
    }

    @Override
    public String suggestTitle(UUID userId, UUID postId, String context) {
        TitlePromptContext promptContext = executeInTransaction(() -> {
            Post post = listingAccess.owned(userId, postId, false);
            listingAccess.requireDraft(post);

            String idempotencyKey = "AI_TITLE:" + postId + ":" + UUID.randomUUID();
            listingCredits.consume(userId, CreditType.AI_CHAT, idempotencyKey);

            String categoryName = post.getCategoryId() != null
                    ? categoryRepository.findById(post.getCategoryId()).map(Category::getName).orElse("Không xác định")
                    : "Không xác định";
            String itemName = post.getItemId() != null
                    ? itemRepository.findById(post.getItemId()).map(Item::getName).orElse("Không xác định")
                    : "Không xác định";
            String description = post.getDescription() != null && !post.getDescription().isBlank()
                    ? post.getDescription()
                    : post.getAiDescription();

            String promptText = String.format("""
                    Bạn là chuyên gia marketing và tối ưu bài đăng thương mại điện tử chuyên đồ gia dụng cũ tại Việt Nam.
                    Hãy tạo đúng 01 tiêu đề bài đăng bán hàng hấp dẫn, chuyên nghiệp và chuẩn SEO dựa trên thông tin sau:
                    - Danh mục: %s
                    - Loại sản phẩm: %s
                    - Thông tin phụ / Tình trạng: %s
                    - Mô tả tóm tắt: %s

                    YÊU CẦU BẮT BUỘC:
                    - Độ dài tiêu đề: từ 35 đến 80 ký tự.
                    - Chứa tên thiết bị, thương hiệu (nếu có), phân khúc/tính năng nổi bật và độ mới thực tế (ví dụ: Like New 99%%, Đẹp 95%%, Chính Hãng...).
                    - CHỈ trả về đúng 01 dòng tiêu đề duy nhất. TUYỆT ĐỐI KHÔNG sử dụng emoji/icon, không có dấu ngoặc kép, không có lời chào hay giải thích gì thêm.
                    """,
                    categoryName, itemName, context != null ? context : "", description != null ? description : "");
            return new TitlePromptContext(promptText, post.getTitle(), idempotencyKey);
        });

        String generatedTitle;
        try {
            generatedTitle = chatGateway.complete(promptContext.promptText());
        } catch (RuntimeException ex) {
            runInTransaction(() -> listingCredits.refund(
                    userId, CreditType.AI_CHAT, promptContext.creditKey() + ":REFUND"));
            throw ex;
        }

        if (generatedTitle != null && !generatedTitle.isBlank()) {
            final String cleanedTitle = AiChatServiceImpl.stripEmojis(generatedTitle).replaceAll("^[\\\"'\\*\\-\\s]+|[\\\"'\\*\\-\\s]+$", "").trim();
            runInTransaction(() -> {
                Post post = listingAccess.owned(userId, postId, true);
                listingAccess.requireDraft(post);
                post.setTitle(cleanedTitle);
                postRepository.save(post);
            });
            return cleanedTitle;
        }
        return promptContext.fallbackTitle();
    }

    @Override
    @Transactional
    public PostSubmitResponse submitPost(UUID userId, UUID postId, PostSubmitRequest request) {
        Post post = listingAccess.owned(userId, postId, true);

        if (request.title() == null || request.title().isBlank() || request.title().length() > 255
                || request.description() == null || request.description().isBlank() || request.description().length() > 10000
                || request.price() == null || request.price().compareTo(BigDecimal.ONE) < 0
                || request.price().scale() > 2 || request.price().precision() - request.price().scale() > 16)
            throw new BadRequestException("A valid title, description and positive VND price are required");

        // A retry after acceptance may return the prior outcome but cannot edit/re-up the listing.
        if (post.isListingCreditCharged() || "PENDING".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus())) {
            if (!("ACTIVE".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus()) || "PENDING".equals(post.getStatus()))
                    || !listingFingerprint.normalize(request.title()).equals(listingFingerprint.normalize(post.getTitle()))
                    || !listingFingerprint.normalize(request.description()).equals(listingFingerprint.normalize(post.getDescription()))
                    || request.price().compareTo(post.getPrice()) != 0)
                throw new ConflictException("This post has already been submitted; re-up is not supported");

            return submitResponse(post);
        }

        listingAccess.requireDraft(post);
        listingAccess.requireVerifiedSeller(userId);

        if (!post.isDescriptionAccepted() || !listingFingerprint.normalize(request.description()).equals(listingFingerprint.normalize(post.getDescription())))
            throw new ConflictException("Accept the latest product description before submitting");

        if (post.getImageUrl() == null || post.getImageUrl().isBlank()) throw new BadRequestException("A product image is required");

        publication.requirePublishAvailable(userId);

        post.setTitle(request.title().trim());
        post.setDescription(request.description().trim());
        post.setPrice(request.price());
        post.setShippingWeight(request.shippingWeight());
        post.setShippingLength(request.shippingLength());
        post.setShippingWidth(request.shippingWidth());
        post.setShippingHeight(request.shippingHeight());

        postRepository.lockForSubmission();

        var matches = new ArrayList<UUID>();
        for (Post other : postRepository.findByIdNotAndStatusIn(postId,
                List.of("ACTIVE", "PENDING_INSPECTION", "PENDING"))) {
            boolean sameImage = !Collections.disjoint(post.getImageFingerprints(), other.getImageFingerprints());
            boolean visuallySimilar = post.getImages().stream().anyMatch(image -> other.getImages().stream().anyMatch(candidate ->
                    imageSimilarity.similar(image.getPerceptualFingerprint(), candidate.getPerceptualFingerprint())));
            boolean sameProduct = userId.equals(other.getUser().getId()) && Objects.equals(post.getCategoryId(), other.getCategoryId())
                    && Objects.equals(post.getItemId(), other.getItemId())
                    && listingFingerprint.normalize(post.getTitle()).equals(listingFingerprint.normalize(other.getTitle()))
                    && listingFingerprint.normalize(post.getDescription()).equals(listingFingerprint.normalize(other.getDescription()));
            if (sameImage || visuallySimilar || sameProduct) matches.add(other.getId());
        }

        post.setReviewReason(null);
        post.setDuplicatePostIds(null);
        post.setReviewedBy(null);
        post.setReviewedAt(null);

        if (!matches.isEmpty()) {
            post.setStatus("PENDING");
            post.setRejectionReason(null);
            post.setReviewReason("Possible duplicate product images or listing content; STAFF review required");
            post.setDuplicatePostIds(matches.stream().map(UUID::toString).collect(Collectors.joining(",")));
            postRepository.save(post);
            return submitResponse(post);
        }

        String scanResult = aiScanPost(post);
        if (scanResult.startsWith("REJECTED:")) {
            String reason = scanResult.substring("REJECTED:".length()).trim();
            post.setStatus("REJECTED");
            post.setRejectionReason("AI tự động từ chối: " + reason);
            postRepository.save(post);
            return new PostSubmitResponse(
                    "REJECTED",
                    false,
                    null,
                    null,
                    "Bài đăng bị từ chối bởi hệ thống AI: " + reason,
                    null
            );
        }

        publication.accept(post);
        postRepository.save(post);
        return submitResponse(post);
    }

    private PostSubmitResponse submitResponse(Post post) {
        if ("PENDING".equals(post.getStatus()))
            return new PostSubmitResponse("PENDING", false, null, null, "Bài có dấu hiệu trùng lặp, đang chờ STAFF kiểm duyệt; chưa trừ credit đăng bài", null);
        boolean inspection = "PENDING_INSPECTION".equals(post.getStatus());
        return new PostSubmitResponse(post.getStatus(), inspection, inspection ? inspectionFee : null,
                inspection ? shippingFee : null, inspection
                ? "Đã nhận bài và tạo đơn kiểm định. Phí kiểm định/vận chuyển thanh toán qua luồng riêng."
                : "Bài đăng đã được duyệt và hiển thị trên sàn.", null);
    }

    /**
     * Gọi Google Gemini để scan bài đăng.
     * Returns "APPROVED" hoặc "REJECTED:<reason>"
     */
    private String aiScanPost(Post post) {
        String prompt = String.format("""
                Bạn là hệ thống kiểm duyệt tự động cho sàn mua bán đồ cũ SecondLife.
                Hãy đánh giá bài đăng sản phẩm sau có phù hợp để hiển thị trên sàn hay không:
                Tiêu đề: %s
                Mô tả: %s
                Giá: %s VNĐ
                TIÊU CHÍ KIỂM DUYỆT:
                1. HỢP LỆ (APPROVED):
                   - Sản phẩm là đồ dùng, gia dụng, thiết bị sinh hoạt cũ/đã qua sử dụng hợp pháp.
                   - Mô tả rõ ràng (chấp nhận định dạng Markdown hoặc mô tả do AI hỗ trợ tạo).
                   - Mức giá khả thi (không phải giá 0 VNĐ hoặc spam số ảo).
                2. TỪ CHỐI (REJECTED):
                   - Hàng cấm theo quy định (vũ khí, ma túy, chất kích thích, động vật quý hiếm, nội dung đồi trụy).
                   - Dấu hiệu lừa đảo trắng trợn, spam vô nghĩa, lôi kéo giao dịch ngoài sàn.
                   - Tiêu đề và mô tả hoàn toàn sai lệch hoặc không liên quan đến đồ dùng cần bán.
                QUY TẮC BẮT BUỘC:
                CHỈ trả về đúng một trong hai dạng sau trên một dòng duy nhất, không giải thích thêm:
                APPROVED
                hoặc
                REJECTED:<lý do ngắn gọn bằng tiếng Việt>
                """,
                post.getTitle(), post.getDescription(), post.getPrice()
        );

        Prompt aiPrompt = new Prompt(new UserMessage(prompt));
        String result;
        try {
            result = googleChatGateway.complete(aiPrompt);
        } catch (Exception ex) {
            throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex);
        }

        if (result == null) throw new AiProviderException("AI moderation returned no result");
        result = result.trim();

        if (!"APPROVED".equals(result) && !(result.startsWith("REJECTED:") && result.substring(9).trim().length() > 0))
            throw new AiProviderException("AI moderation returned an invalid result");

        return result;
    }

    @Override
    @Transactional
    public void approvePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!"PENDING".equals(post.getStatus())) {
            throw new RuntimeException("Post must be in PENDING status to be approved");
        }
        listingAccess.requireVerifiedSeller(post.getUser().getId());
        post.setStatus("ACTIVE");
        postRepository.save(post);
        reviews.approve(postId, null);
    }

    @Override
    @Transactional
    public void rejectPost(UUID postId, String reason) {
        reviews.reject(postId, null, reason);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!"PENDING".equals(post.getStatus())) {
            throw new RuntimeException("Post must be in PENDING status to be rejected");
        }
        post.setStatus("REJECTED");
        post.setRejectionReason(reason);
        postRepository.save(post);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDto> getAdminPosts(
            String status, UUID categoryId, UUID itemId, Pageable pageable) {
        Specification<Post> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                Fetch<Object, Object> userFetch = root.fetch("user", JoinType.LEFT);
                userFetch.fetch("profile", JoinType.LEFT);
            }
            return cb.conjunction();
        };

        if (status != null && !status.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        return postRepository.findAll(spec, pageable).map(postMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDto> getPublicPosts(UUID categoryId, UUID itemId, Pageable pageable) {
        Specification<Post> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                Fetch<Object, Object> userFetch = root.fetch("user", JoinType.LEFT);
                userFetch.fetch("profile", JoinType.LEFT);
            }
            return cb.equal(root.get("status"), "ACTIVE");
        };

        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        return postRepository.findAll(spec, pageable).map(postMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDto getPostDetail(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        return postMapper.toResponse(post);
    }

    @Override
    public AiPriceEstimationResponse estimatePrice(UUID userId, UUID postId, String requestId) {
        ValuationPromptContext promptContext = executeInTransaction(() -> {
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new NotFoundException("Post not found"));
            if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
                throw new ForbiddenException("This post belongs to another user");
            }

            listingAccess.requireDraft(post);

            String valuationKey = "VALUATION:" + postId + ":" + UUID.randomUUID();
            listingCredits.consume(userId, CreditType.VALUATION, valuationKey);

            String itemName = post.getItemId() != null
                    ? itemRepository.findById(post.getItemId()).map(Item::getName).orElse("Không xác định")
                    : "Không xác định";

            String description = post.getDescription() != null ? post.getDescription() : "Không có mô tả";
            String priceContext = post.getAiSuggestedPrice() != null
                    ? "Giá mà người bán đã đề xuất: " + post.getAiSuggestedPrice() + " VNĐ."
                    : "";

            StringBuilder chatContext = new StringBuilder();
            UUID targetSessionId = null;
            if (requestId != null && !requestId.trim().isEmpty()) {
                try {
                    targetSessionId = UUID.fromString(requestId.trim());
                } catch (Exception ignored) {
                }
            }

            if (targetSessionId == null && post.getAiChatSession() != null) {
                targetSessionId = post.getAiChatSession().getId();
            }

            if (targetSessionId != null) {
                List<AiChatMessage> messages =
                        chatMessageRepository.findBySessionIdOrderBySentAtAsc(targetSessionId);

                if (messages.isEmpty() && post.getAiChatSession() != null && !targetSessionId.equals(post.getAiChatSession().getId())) {
                    targetSessionId = post.getAiChatSession().getId();
                    messages = chatMessageRepository.findBySessionIdOrderBySentAtAsc(targetSessionId);
                }

                for (AiChatMessage msg : messages) {
                    if ("USER".equals(msg.getRole()) && msg.getMessageContent() != null && !msg.getMessageContent().isBlank()) {
                        String content = AiChatServiceImpl.sanitizePersistedMessageForContext("USER", msg.getMessageContent());
                        if (!content.isBlank() && !content.startsWith("Danh mục người bán chọn:")) {
                            chatContext.append("- ").append(content).append("\n");
                        }
                    }
                }
            }

            String pricePrompt = String.format(
                    """
                    Bạn là chuyên gia thẩm định và định giá đồ gia dụng cũ tại thị trường Việt Nam.
                    Nhiệm vụ của bạn là thẩm định sản phẩm dựa trên thông tin thực tế của sản phẩm và người bán để đưa ra khoảng giá thanh lý hợp lý nhất bằng VNĐ.

                    THÔNG TIN SẢN PHẨM:
                    - Loại sản phẩm: %s
                    - Mô tả hiện tại: %s
                    %s
                    - Chi tiết người bán cung cấp thêm:
                    %s

                    QUY TẮC ĐỊNH GIÁ THỰC TẾ:
                    1. Ước lượng giá mua mới của sản phẩm trên thị trường Việt Nam làm mốc chuẩn. Bỏ qua giá mua mới nếu người bán cung cấp mức giá phi thực tế.
                    2. ĐÁNH GIÁ THEO HAO MÒN THỰC TẾ & TẦN SUẤT SỬ DỤNG (Ưu tiên ngoại quan, số lần dùng và tình trạng bảo hành/phụ kiện hơn là chỉ đếm thời gian mua):
                       - Hàng lướt / Like New: Mới dùng vài lần, nguyên hộp/phụ kiện, đẹp 98-99%% (kể cả đã mua vài tháng đến 1 năm nhưng ít dùng): Giảm 15-25%% so với giá mới.
                       - Hàng còn rất tốt / Dùng giữ gìn: Ngoại hình đẹp 90-95%%, không lỗi lầm, dùng ít hoặc mua 6 tháng - 1 năm tần suất sử dụng thấp: Giảm 25-35%% so với giá mới.
                       - Hàng sử dụng thông thường: Dùng đều đặn 1-2 năm, có trầy xước nhẹ/hao mòn tự nhiên, hết bảo hành: Giảm 35-50%% so với giá mới.
                       - Hàng đã dùng lâu / Đời cũ: Dùng trên 2-3 năm, có hao mòn rõ rệt hoặc lỗi thời: Giảm 50-65%% so với giá mới.
                    3. Giá đề xuất (suggestedPrice) là giá thanh lý thực tế, dễ bán nhất trên thị trường đồ cũ Việt Nam. Tuyệt đối không định giá đồ cũ cao hơn hoặc bằng giá mới.
                    4. Ràng buộc: fairPriceMin <= suggestedPrice <= fairPriceMax. Tất cả các giá đều là số nguyên dương VND (> 0).
                    5. BẮT BUỘC chỉ trả về duy nhất định dạng JSON sau, không bọc markdown và không giải thích gì thêm:
                    {"fairPriceMin": <số nguyên VND>, "fairPriceMax": <số nguyên VND>, "suggestedPrice": <số nguyên VND>, "expectedSellTime": "<ví dụ: 3-5 ngày>"}
                    """,
                    itemName, description, priceContext, chatContext.length() > 0 ? chatContext.toString() : "Không có thêm thông tin");
            return new ValuationPromptContext(pricePrompt, valuationKey);
        });

        String aiResult;
        try {
            aiResult = googleChatGateway.complete(promptContext.pricePrompt());
        } catch (RuntimeException ex) {
            runInTransaction(() -> listingCredits.refund(
                    userId, CreditType.VALUATION, promptContext.valuationKey() + ":REFUND"));
            throw ex;
        }

        BigDecimal fairPriceMin = BigDecimal.ZERO;
        BigDecimal fairPriceMax = BigDecimal.ZERO;
        BigDecimal suggestedPrice = BigDecimal.ZERO;
        String expectedSellTime = "3-5 ngày";

        try {
            String jsonStr = aiResult;
            if (aiResult.contains("{") && aiResult.contains("}")) {
                jsonStr = aiResult.substring(aiResult.indexOf("{"), aiResult.lastIndexOf("}") + 1);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonStr);
            fairPriceMin = BigDecimal.valueOf(node.get("fairPriceMin").asLong());
            fairPriceMax = BigDecimal.valueOf(node.get("fairPriceMax").asLong());
            suggestedPrice = BigDecimal.valueOf(node.get("suggestedPrice").asLong());
            if (node.has("expectedSellTime")) {
                expectedSellTime = node.get("expectedSellTime").asText();
            }
        } catch (Exception e) {
            Matcher m = Pattern.compile("[0-9]{5,}").matcher(aiResult != null ? aiResult.replaceAll("[.,]", "") : "");
            List<Long> nums = new ArrayList<>();
            while (m.find()) nums.add(Long.parseLong(m.group()));
            if (nums.size() >= 3) {
                fairPriceMin = BigDecimal.valueOf(nums.get(0));
                fairPriceMax = BigDecimal.valueOf(nums.get(1));
                suggestedPrice = BigDecimal.valueOf(nums.get(2));
            }
        }

        final BigDecimal finalSuggestedPrice = suggestedPrice;
        if (finalSuggestedPrice != null && finalSuggestedPrice.compareTo(BigDecimal.ZERO) > 0) {
            runInTransaction(() -> {
                Post post = postRepository.findById(postId).orElse(null);
                if (post != null) {
                    post.setAiSuggestedPrice(finalSuggestedPrice);
                    postRepository.save(post);
                }
            });
        }

        return AiPriceEstimationResponse.builder()
                .requestId(requestId)
                .fairPriceMin(fairPriceMin)
                .fairPriceMax(fairPriceMax)
                .suggestedPrice(suggestedPrice)
                .modelVersion("SecondLife-AI-v2.1")
                .expectedSellTime(expectedSellTime)
                .createdAt(Instant.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDto> getMyPosts(UUID userId, Pageable pageable) {
        Specification<Post> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                Fetch<Object, Object> userFetch = root.fetch("user", JoinType.LEFT);
                userFetch.fetch("profile", JoinType.LEFT);
            }
            return cb.equal(root.get("user").get("id"), userId);
        };

        Page<Post> page = postRepository.findAll(spec, pageable);
        if (!page.isEmpty()) {
            postRepository.findAllWithImagesByIdIn(page.getContent().stream().map(Post::getId).toList());
        }
        return page.map(postMapper::toResponse);
    }

    @Override
    @Transactional
    public PostDto updateDraft(UUID userId, UUID postId, UpdateDraftRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }

        post.setTitle(request.title());
        post.setDescription(request.description());
        post.setItemCondition(request.itemCondition());
        post.setPrice(request.price());

        if (request.shippingWeight() != null) post.setShippingWeight(request.shippingWeight());
        if (request.shippingLength() != null) post.setShippingLength(request.shippingLength());
        if (request.shippingWidth() != null) post.setShippingWidth(request.shippingWidth());
        if (request.shippingHeight() != null) post.setShippingHeight(request.shippingHeight());

        post = postRepository.save(post);
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public PostDto acceptDescription(UUID userId, UUID postId, AcceptDescriptionRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }

        post.setDescription(request.description());
        post = postRepository.save(post);
        return postMapper.toResponse(post);
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(tx -> action.get());
        }
        return action.get();
    }

    private void runInTransaction(Runnable action) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(tx -> action.run());
        } else {
            action.run();
        }
    }

    private record SessionPrecheck(UUID postId, String creditKey) {
    }

    private record TitlePromptContext(String promptText, String fallbackTitle, String creditKey) {
    }

    private record ValuationPromptContext(String pricePrompt, String valuationKey) {
    }
}
