package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.ListingCreditService;
import com.secondlife.secondlife.service.ListingFingerprint;
import com.secondlife.secondlife.service.ListingImageSimilarity;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.service.PostService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PostServiceImpl implements PostService {

    private final BigDecimal inspectionFee;
    private final BigDecimal shippingFee;
    private final int maxUnfinishedDrafts;
    private final long maxImageBytes;
    private final int minImages;
    private final int maxImages;
    private final ListingImageSimilarity imageSimilarity;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ListingCreditService listingCredits;
    private final ListingAccessService listingAccess;
    private final AiChatService aiChatService;
    private final CategoryQuestionTemplateRepository templateRepository;
    private final com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ChatClient chatClient;
    private final com.secondlife.secondlife.service.CloudinaryService cloudinaryService;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final org.springframework.ai.chat.model.ChatModel googleChatModel;
    private final com.secondlife.secondlife.service.ListingPublicationService publication;
    private final com.secondlife.secondlife.service.ListingReviewService reviews;
    private final com.secondlife.secondlife.service.ListingSubmissionLock submissionLock;

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           ListingCreditService listingCredits,
                           ListingAccessService listingAccess,
                           AiChatService aiChatService,
                           CategoryQuestionTemplateRepository templateRepository,
                           com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository,
                           CategoryRepository categoryRepository,
                           ItemRepository itemRepository,
                           ChatClient.Builder chatClientBuilder,
                           com.secondlife.secondlife.service.CloudinaryService cloudinaryService,
                           InspectionOrderRepository inspectionOrderRepository,
                           @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel")
                           org.springframework.ai.chat.model.ChatModel googleChatModel,
                           com.secondlife.secondlife.service.ListingPublicationService publication,
                           com.secondlife.secondlife.service.ListingReviewService reviews,
                           com.secondlife.secondlife.service.ListingSubmissionLock submissionLock,
                           ListingImageSimilarity imageSimilarity,
                           @Value("${app.inspection.inspection-fee}") BigDecimal inspectionFee,
                           @Value("${app.inspection.shipping-fee}") BigDecimal shippingFee,
                           @Value("${app.listing.max-unfinished-drafts}") int maxUnfinishedDrafts,
                           @Value("${app.listing.max-image-bytes}") long maxImageBytes,
                           @Value("${app.listing.min-images}") int minImages,
                           @Value("${app.listing.max-images}") int maxImages) {
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
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.chatClient = chatClientBuilder.build();
        this.cloudinaryService = cloudinaryService;
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.googleChatModel = googleChatModel;
        this.publication = publication;
        this.reviews = reviews;
        this.submissionLock = submissionLock;
        this.imageSimilarity = imageSimilarity;
        this.inspectionFee = inspectionFee;
        this.shippingFee = shippingFee;
        this.maxUnfinishedDrafts = maxUnfinishedDrafts;
        this.maxImageBytes = maxImageBytes;
        this.minImages = minImages;
        this.maxImages = maxImages;
    }

    @Override
    @Transactional
    public PostInitResponse initPost(UUID userId, PostInitRequest request) {
        User user = userRepository.findByIdForRoleUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (request.getCategoryId() == null || !categoryRepository.existsById(request.getCategoryId()))
            throw new BadRequestException("A valid categoryId is required");
        if (request.getItemId() != null) {
            var item = itemRepository.findById(request.getItemId()).orElseThrow(() -> new BadRequestException("Invalid itemId"));
            if (!request.getCategoryId().equals(item.getCategory().getId()))
                throw new BadRequestException("itemId does not belong to categoryId");
        }
        if (postRepository.countByUser_IdAndStatusIn(userId, java.util.List.of("DRAFT", "REJECTED", "PENDING", "PENDING_INSPECTION")) >= maxUnfinishedDrafts)
            throw new ConflictException("Maximum " + maxUnfinishedDrafts + " unfinished drafts; finish an existing draft first");
        if (request.getImages() == null || request.getImages().size() < minImages || request.getImages().size() > maxImages)
            throw new BadRequestException("Upload between " + minImages + " and " + maxImages + " product images");
        for (var image : request.getImages()) {
            if (image == null || image.isEmpty() || image.getSize() > maxImageBytes || image.getContentType() == null
                    || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType()))
                throw new BadRequestException("Every image must be a non-empty JPEG, PNG or WebP no larger than " + maxImageBytes + " bytes");
        }

        Post post = new Post();
        post.setUser(user);
        post.setCategoryId(request.getCategoryId());
        post.setItemId(request.getItemId());
        post.setStatus("DRAFT");
        
        for (var image : request.getImages()) {
            try {
                byte[] imageBytes = image.getBytes();
                String perceptualFingerprint = imageSimilarity.fingerprint(imageBytes);
                String imageUrl = cloudinaryService.uploadImage(image);
                if (imageUrl == null || imageUrl.isBlank()) throw new IllegalStateException("Image upload returned an empty URL");
                post.getImages().add(new com.secondlife.secondlife.entity.PostImage(imageUrl,
                        ListingFingerprint.sha256(imageBytes), perceptualFingerprint));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to upload image to Cloudinary", e);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid or excessively large image dimensions");
            }
        }
        post.setImageUrl(post.getImages().getFirst().getImageUrl());
        post.setImageFingerprint(post.getImages().getFirst().getImageFingerprint());

        post = postRepository.save(post);

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        aiRequest.setPostId(post.getId());
        // Prompt for Llava to only describe the visual condition
        String selectedCategoryName = categoryRepository.findById(request.getCategoryId()).orElseThrow().getName();
        String selectedItemName = request.getItemId() == null ? "Chưa chọn" : itemRepository.findById(request.getItemId()).orElseThrow().getName();
        aiRequest.setMessage("Danh mục người bán chọn: " + selectedCategoryName + "; loại sản phẩm: " + selectedItemName
                + ". Dựa vào toàn bộ ảnh, hãy viết mô tả sản phẩm để đăng bán, nhận xét ngoại hình, tình trạng và đặc điểm nhìn thấy."
                + " Chỉ mô tả sản phẩm, không mô tả cảnh nền thành đặc tính sản phẩm. Không suy đoán hãng, tuổi đời, bảo hành nếu ảnh không chứng minh. Không đề xuất giá, không thêm lời chào.");
        aiRequest.setImages(request.getImages());

        // Initial image analysis is free description chat, not valuation.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);
        post.setAiDescription(aiResponse.getReply());
        post.setDescription(aiResponse.getReply());
        post.setDescriptionAccepted(false);

        // 4. Fetch or Generate Template
        CategoryQuestionTemplate template = templateRepository.findByCategoryIdAndItemId(request.getCategoryId(), request.getItemId())
                .orElseGet(() -> {
                    // Generate new template
                    String categoryName = categoryRepository.findById(request.getCategoryId())
                            .map(com.secondlife.secondlife.entity.Category::getName).orElse("Không xác định");
                    String itemName = "Không xác định";
                    if (request.getItemId() != null) {
                        itemName = itemRepository.findById(request.getItemId())
                                .map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định");
                    }

                    String promptText = String.format("Bạn là chuyên gia về đồ cũ. Hãy tạo một đoạn mẫu câu hỏi (template) bằng tiếng Việt để hỏi người dùng các thông tin cần thiết khi họ muốn đăng bán một sản phẩm thuộc danh mục '%s', loại sản phẩm '%s'. Ví dụ: 'Vui lòng cung cấp thêm thông tin: Hãng, Tình trạng bảo hành, Thời gian sử dụng...'. Trả về đúng nội dung câu hỏi, ngắn gọn, không giải thích gì thêm.", categoryName, itemName);

                    String generatedTemplate = chatClient.prompt(promptText).call().content();

                    // Save to DB
                    CategoryQuestionTemplate newTemplate = new CategoryQuestionTemplate();
                    newTemplate.setCategoryId(request.getCategoryId());
                    newTemplate.setItemId(request.getItemId());
                    newTemplate.setTemplateText(generatedTemplate);
                    return templateRepository.save(newTemplate);
                });
                
        String templateText = template.getTemplateText();
                
        // Fetch session to set on post
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(aiResponse.getSessionId()).orElse(null);
        post.setAiChatSession(session);
        post.setTemplate(template);
        postRepository.save(post);

        String combinedResponse = aiResponse.getReply() + "\n\n" + templateText;

        // Note: Ideally we should update the AI message in the database with the appended template, 
        // but for now returning it combined is fine, or the user can see it in UI.

        return new PostInitResponse(
                post.getId(),
                aiResponse.getSessionId(),
                combinedResponse
        );
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId) {
        userRepository.findByIdForRoleUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }
        Post post = session.getPostId() == null ? null : listingAccess.owned(userId, session.getPostId(), true);
        if (post != null) listingAccess.requireDraft(post);
        var finalizeResponse = aiChatService.finalizeChat(sessionId, userId);
        if (post != null) {
            post.setAiDescription(finalizeResponse.getDescription());
            post.setDescription(finalizeResponse.getDescription());
            post.setDescriptionAccepted(false);
            postRepository.save(post);
        }
        
        return finalizeResponse;
    }

    @Override
    @Transactional
    public PostSubmitResponse submitPost(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.PostSubmitRequest request) {
        Post post = listingAccess.owned(userId, postId, true);
        if (request.getTitle() == null || request.getTitle().isBlank() || request.getTitle().length() > 255
                || request.getDescription() == null || request.getDescription().isBlank() || request.getDescription().length() > 10000
                || request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ONE) < 0
                || request.getPrice().scale() > 2 || request.getPrice().precision() - request.getPrice().scale() > 16)
            throw new BadRequestException("A valid title, description and positive VND price are required");
        // A retry after acceptance may return the prior outcome but cannot edit/re-up the listing.
        if (post.isListingCreditCharged() || "PENDING".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus())) {
            if (!("ACTIVE".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus()) || "PENDING".equals(post.getStatus()))
                    || !ListingFingerprint.normalize(request.getTitle()).equals(ListingFingerprint.normalize(post.getTitle()))
                    || !ListingFingerprint.normalize(request.getDescription()).equals(ListingFingerprint.normalize(post.getDescription()))
                    || request.getPrice().compareTo(post.getPrice()) != 0)
                throw new ConflictException("This post has already been submitted; re-up is not supported");
            return submitResponse(post);
        }
        listingAccess.requireDraft(post);
        listingAccess.requireVerifiedSeller(userId);
        if (!post.isDescriptionAccepted() || !ListingFingerprint.normalize(request.getDescription()).equals(ListingFingerprint.normalize(post.getDescription())))
            throw new ConflictException("Accept the latest product description before submitting");
        if (post.getImageUrl() == null || post.getImageUrl().isBlank()) throw new BadRequestException("A product image is required");
        publication.requirePublishAvailable(userId);
        post.setTitle(request.getTitle().trim());
        post.setDescription(request.getDescription().trim());
        post.setPrice(request.getPrice());
        submissionLock.lock();
        var matches = new java.util.ArrayList<UUID>();
        for (Post other : postRepository.findByIdNotAndStatusIn(postId,
                java.util.List.of("ACTIVE", "PENDING_INSPECTION", "PENDING"))) {
            boolean sameImage = !java.util.Collections.disjoint(post.getImageFingerprints(), other.getImageFingerprints());
            boolean visuallySimilar = post.getImages().stream().anyMatch(image -> other.getImages().stream().anyMatch(candidate ->
                    imageSimilarity.similar(image.getPerceptualFingerprint(), candidate.getPerceptualFingerprint())));
            boolean sameProduct = userId.equals(other.getUser().getId()) && java.util.Objects.equals(post.getCategoryId(), other.getCategoryId())
                    && java.util.Objects.equals(post.getItemId(), other.getItemId())
                    && ListingFingerprint.normalize(post.getTitle()).equals(ListingFingerprint.normalize(other.getTitle()))
                    && ListingFingerprint.normalize(post.getDescription()).equals(ListingFingerprint.normalize(other.getDescription()));
            if (sameImage || visuallySimilar || sameProduct) matches.add(other.getId());
        }
        post.setReviewReason(null);
        post.setDuplicatePostIds(null);
        post.setReviewedBy(null); post.setReviewedAt(null);
        if (!matches.isEmpty()) {
            post.setStatus("PENDING");
            post.setRejectionReason(null);
            post.setReviewReason("Possible duplicate product images or listing content; STAFF review required");
            post.setDuplicatePostIds(matches.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
            postRepository.save(post);
            return submitResponse(post);
        }
        String scanResult = aiScanPost(post);
        if (scanResult.startsWith("REJECTED:")) {
            post.setStatus("REJECTED");
            post.setRejectionReason(scanResult.substring("REJECTED:".length()).trim());
            postRepository.save(post);
            return new PostSubmitResponse("REJECTED", false, null, null, post.getRejectionReason(), null);
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
        String prompt = String.format(
            "Bạn là hệ thống kiểm duyệt tự động của sàn mua bán đồ cũ SecondLife. " +
            "Hãy đánh giá bài đăng sản phẩm sau có phù hợp để hiển thị trên sàn không? " +
            "Kiểm tra: (1) Mô tả có khớp với loại sản phẩm, (2) Không có dấu hiệu lừa đảo, " +
            "(3) Không vi phạm nội quy (hàng cấm, hàng giả, thông tin sai lệch). " +
            "Tiêu đề: %s. Mô tả: %s. Giá: %s VNĐ. " +
            "CHỈ trả về đúng một trong hai dạng sau, không giải thích thêm: " +
            "APPROVED hoặc REJECTED:<lý do ngắn gọn bằng tiếng Việt>",
            post.getTitle(), post.getDescription(), post.getPrice()
        );
        Prompt aiPrompt = new Prompt(new UserMessage(prompt));
        ChatClient googleClient = ChatClient.builder(googleChatModel).build();
        String result;
        try { result = googleClient.prompt(aiPrompt).call().content(); }
        catch (Exception ex) { throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex); }
        if (result == null) throw new AiProviderException("AI moderation returned no result");
        result = result.trim();
        if (!"APPROVED".equals(result) && !(result.startsWith("REJECTED:") && result.substring(9).trim().length() > 0))
            throw new AiProviderException("AI moderation returned an invalid result");
        return result;
    }

    @Override
    @Transactional
    public void approvePost(UUID postId) {
        reviews.approve(postId, null);
    }

    @Override
    @Transactional
    public void rejectPost(UUID postId, String reason) {
        reviews.reject(postId, null, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getAdminPosts(String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Post> spec = org.springframework.data.jpa.domain.Specification.where((org.springframework.data.jpa.domain.Specification<Post>) null);
        if (status != null && !status.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        // Public posts should only return ACTIVE ones
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
        
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public com.secondlife.secondlife.dto.response.PostDto getPostDetail(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getMyPosts(UUID userId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);
    }
}
