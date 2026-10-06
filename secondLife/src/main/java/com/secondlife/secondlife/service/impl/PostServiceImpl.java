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
import com.secondlife.secondlife.entity.UserCredit;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.CreditService;
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

    @Value("${app.inspection.high-value-threshold:5000000}")
    private BigDecimal highValueThreshold;

    @Value("${app.inspection.inspection-fee:200000}")
    private BigDecimal inspectionFee;

    @Value("${app.inspection.shipping-fee:50000}")
    private BigDecimal shippingFee;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final AiChatService aiChatService;
    private final CategoryQuestionTemplateRepository templateRepository;
    private final com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ChatClient chatClient;
    private final com.secondlife.secondlife.service.CloudinaryService cloudinaryService;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final org.springframework.ai.chat.model.ChatModel googleChatModel;

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           CreditService creditService,
                           AiChatService aiChatService,
                           CategoryQuestionTemplateRepository templateRepository,
                           com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository,
                           CategoryRepository categoryRepository,
                           ItemRepository itemRepository,
                           ChatClient.Builder chatClientBuilder,
                           com.secondlife.secondlife.service.CloudinaryService cloudinaryService,
                           InspectionOrderRepository inspectionOrderRepository,
                           @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel")
                           org.springframework.ai.chat.model.ChatModel googleChatModel) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
        this.aiChatService = aiChatService;
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.chatClient = chatClientBuilder.build();
        this.cloudinaryService = cloudinaryService;
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.googleChatModel = googleChatModel;
    }

    @Override
    @Transactional
    public PostInitResponse initPost(UUID userId, PostInitRequest request) {
        // 1. Deduct post credit
        creditService.deductPostCredit(userId);

        // 2. Create Draft Post
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setUser(user);
        post.setCategoryId(request.getCategoryId());
        post.setItemId(request.getItemId());
        post.setStatus("DRAFT");
        
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            java.util.List<String> uploadedUrls = new java.util.ArrayList<>();
            for (org.springframework.web.multipart.MultipartFile file : request.getImages()) {
                try {
                    String imageUrl = cloudinaryService.uploadImage(file);
                    uploadedUrls.add(imageUrl);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to upload image to Cloudinary", e);
                }
            }
            post.setImageUrls(uploadedUrls);
        }
        
        post = postRepository.save(post);

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        aiRequest.setPostId(post.getId());
        // Prompt for Llava to only describe the visual condition
        aiRequest.setMessage("Dựa vào hình ảnh được cung cấp, hãy chỉ nhận xét ngắn gọn về ngoại hình và tình trạng vật lý của sản phẩm này. Không cần thêm lời chào hay bình luận gì khác.");
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            aiRequest.setImage(request.getImages().get(0));
        }

        // This will deduct 1 chat credit if applicable, or we might say the first message doesn't cost a chat credit? 
        // User said: "Mỗi bài đăng đi kèm 5 lượt chat". So it might cost a chat credit. 
        // Actually, AiChatService will create a new session and count=1.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);

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
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }
        Post post = session.getPostId() == null ? null : postRepository.findById(session.getPostId())
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post != null && (post.getUser() == null || !userId.equals(post.getUser().getId()))) {
            throw new ForbiddenException("This post belongs to another user");
        }
        var finalizeResponse = aiChatService.finalizeChat(sessionId, userId);
        if (post != null) {
            post.setAiDescription(finalizeResponse.getDescription());
            post.setDescription(finalizeResponse.getDescription());
            post.setAiSuggestedPrice(finalizeResponse.getSuggestedPrice());
            postRepository.save(post);
        }
        
        return finalizeResponse;
    }

    @Override
    @Transactional
    public PostSubmitResponse submitPost(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.PostSubmitRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            throw new ForbiddenException("This post belongs to another user");
        }

        // Cập nhật thông tin người dùng chốt
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setPrice(request.getPrice());
        postRepository.save(post);

        // BƯỚC A: AI Scan bài đăng
        String scanResult = aiScanPost(post);
        if (scanResult.startsWith("REJECTED")) {
            String reason = scanResult.contains(":") ? scanResult.substring(scanResult.indexOf(":") + 1).trim() : "Nội dung bài đăng không hợp lệ";
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

        // BƯỚC B: Kiểm tra ngưỡng giá
        if (post.getPrice() != null && post.getPrice().compareTo(highValueThreshold) > 0) {
            // Hàng giá trị cao → kiểm định
            BigDecimal totalFee = inspectionFee.add(shippingFee);

            // Kiểm tra credit của Seller
            UserCredit userCredit = creditService.getUserCredit(userId);
            // Dùng post_credits như một đơn vị tương đương tiền (1 credit = 1000 VNĐ)
            // Nếu không đủ credit, trả về response kèm số tiền còn thiếu để FE hiển thị lựa chọn
            long requiredCredits = totalFee.longValue() / 1000;
            if (userCredit.getPostCredits() < requiredCredits) {
                BigDecimal shortfall = totalFee.subtract(BigDecimal.valueOf(userCredit.getPostCredits() * 1000L));
                return new PostSubmitResponse(
                        "INSUFFICIENT_CREDIT",
                        true,
                        inspectionFee,
                        shippingFee,
                        "Không đủ credit để thanh toán phí kiểm định và vận chuyển. Vui lòng nạp thêm hoặc thanh toán trực tiếp qua chuyển khoản.",
                        shortfall
                );
            }

            // Trừ credit
            userCredit.setPostCredits((int)(userCredit.getPostCredits() - requiredCredits));

            // Tạo InspectionOrder
            InspectionOrder order = new InspectionOrder();
            order.setPost(post);
            order.setInspectionFee(inspectionFee);
            order.setShippingFee(shippingFee);
            order.setStatus("PENDING");
            inspectionOrderRepository.save(order);

            post.setStatus("PENDING_INSPECTION");
            postRepository.save(post);

            return new PostSubmitResponse(
                    "PENDING_INSPECTION",
                    true,
                    inspectionFee,
                    shippingFee,
                    "Sản phẩm có giá trị cao. Đã tạo đơn kiểm định. Vui lòng gửi sản phẩm đến trung tâm kiểm định theo hướng dẫn.",
                    null
            );
        } else {
            // Hàng giá thường → AI đã duyệt
            // BƯỚC C: Kiểm tra trùng lặp ảnh
            boolean isDuplicate = checkDuplicateImage(post);
            if (isDuplicate) {
                post.setStatus("PENDING");
                postRepository.save(post);
                return new PostSubmitResponse(
                        "PENDING",
                        false,
                        null,
                        null,
                        "Hệ thống phát hiện ảnh có dấu hiệu trùng lặp. Bài đăng đang chờ nhân viên kiểm duyệt thủ công.",
                        null
                );
            } else {
                post.setStatus("ACTIVE");
                postRepository.save(post);

                return new PostSubmitResponse(
                        "ACTIVE",
                        false,
                        null,
                        null,
                        "Bài đăng đã được duyệt và hiển thị trên sàn.",
                        null
                );
            }
        }
    }

    private boolean checkDuplicateImage(Post post) {
        // TODO: Thay thế bằng code gọi API check trùng ảnh thực tế 
        // (Do hiện tại chưa thấy class gọi API trong source code)
        return false;
    }

    /**
     * Gọi Google Gemini để scan bài đăng.
     * Returns "APPROVED" hoặc "REJECTED:<reason>"
     */
    private String aiScanPost(Post post) {
        ChatClient client = ChatClient.builder(googleChatModel).build();
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
        String result = googleClient.prompt(aiPrompt).call().content();
        return result != null ? result.trim() : "APPROVED";
    }

    @Override
    @Transactional
    public void approvePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (!"PENDING".equals(post.getStatus())) {
            throw new RuntimeException("Post must be in PENDING status to be approved");
        }
        
        post.setStatus("ACTIVE");
        postRepository.save(post);
    }

    @Override
    @Transactional
    public void rejectPost(UUID postId, String reason) {
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
