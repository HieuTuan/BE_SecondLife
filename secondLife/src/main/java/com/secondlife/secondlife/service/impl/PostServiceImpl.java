package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
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
    private final com.secondlife.secondlife.repository.AiChatMessageRepository chatMessageRepository;

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
                           @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel") org.springframework.ai.chat.model.ChatModel googleChatModel,`n                           com.secondlife.secondlife.repository.AiChatMessageRepository chatMessageRepository) {
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
        this.chatMessageRepository = chatMessageRepository;
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

        // Extract names early
        final String categoryName = categoryRepository.findById(request.getCategoryId())
                .map(com.secondlife.secondlife.entity.Category::getName).orElse("Không xác định");
        final String itemName = request.getItemId() != null ? 
                itemRepository.findById(request.getItemId())
                    .map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định") 
                : "Không xác định";

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        aiRequest.setPostId(post.getId());
        // Prompt for Llava/Gemini to check if the image actually matches the item, and describe it.
        String messagePrompt = String.format(
                "Dựa vào hình ảnh, sản phẩm này được người dùng chọn là loại: '%s'. Hãy kiểm tra xem hình ảnh có thực sự là '%s' không. " +
                "NẾU KHÔNG PHẢI (ví dụ: ảnh là nồi cơm điện nhưng người dùng chọn lò vi sóng), BẮT BUỘC bắt đầu câu trả lời của bạn bằng đúng cụm từ '[CẢNH BÁO]' và giải thích sự sai lệch rõ ràng. " +
                "NẾU ĐÚNG, hãy nhận xét ngắn gọn về ngoại hình và tình trạng vật lý của sản phẩm trong ảnh. Không cần thêm lời chào hay bình luận thừa.",
                itemName, itemName);
        aiRequest.setMessage(messagePrompt);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            aiRequest.setImage(request.getImages().get(0));
        }

        // This will deduct 1 chat credit if applicable, or we might say the first
        // message doesn't cost a chat credit?
        // User said: "Mỗi bài đăng đi kèm 5 lượt chat". So it might cost a chat credit.
        // Actually, AiChatService will create a new session and count=1.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);

        // 4. Fetch or Generate Template
        CategoryQuestionTemplate template = templateRepository
                .findByCategoryIdAndItemId(request.getCategoryId(), request.getItemId())
                .orElseGet(() -> {
                    // Generate new template
                    String promptText = String.format(
                            "Bạn là chuyên gia về đồ cũ. Hãy tạo một đoạn mẫu câu hỏi (template) bằng tiếng Việt để hỏi người dùng các thông tin cần thiết khi họ muốn đăng bán một sản phẩm thuộc danh mục '%s', loại sản phẩm '%s'. \n" +
                                    "\n" +
                                    "YÊU CẦU ĐỊNH DẠNG BẮT BUỘC:\n" +
                                    "Mỗi câu hỏi phải theo ĐÚNG định dạng sau, trên từng dòng riêng biệt:\n" +
                                    "**[Tên thông tin cần hỏi]** (Ví dụ: gợi ý 1, gợi ý 2, gợi ý 3)\n" +
                                    "\n" +
                                    "Lưu ý:\n" +
                                    "- Bắt buộc phải có từ 'Ví dụ:' trong ngoặc đơn, các gợi ý trả lời (quick replies) ngăn cách nhau bằng dấu phẩy.\n" +
                                    "- Đưa ra 3-5 câu hỏi quan trọng nhất về tình trạng sản phẩm.\n" +
                                    "- KHÔNG ĐƯỢC HỎI về thương hiệu, hãng sản xuất hay tên sản phẩm vì người dùng đã chọn hoặc cung cấp thông tin này rồi.\n" +
                                    "- Không viết thêm câu dẫn dắt hay kết luận. Chỉ trả về danh sách câu hỏi.",
                            categoryName, itemName);

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
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(aiResponse.getSessionId())
                .orElse(null);
        post.setAiChatSession(session);
        post.setTemplate(template);
        postRepository.save(post);

        String combinedResponse;
        if (aiResponse.getReply().contains("[CẢNH BÁO]")) {
            combinedResponse = aiResponse.getReply() + "\n\n*(Hệ thống đã tạm dừng đưa ra câu hỏi gợi ý vì hình ảnh không khớp. Vui lòng kiểm tra lại hình ảnh hoặc danh mục bạn đã chọn!)*";
        } else {
            combinedResponse = aiResponse.getReply() + "\n\n" + templateText;
        }

        // Note: Ideally we should update the AI message in the database with the
        // appended template,
        // but for now returning it combined is fine, or the user can see it in UI.

        return new PostInitResponse(
                post.getId(),
                aiResponse.getSessionId(),
                combinedResponse);
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse finalizeChatAndDescription(UUID userId,
                                                                                                  UUID sessionId) {
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }
        Post post = session.getPostId() == null ? null
                : postRepository.findById(session.getPostId())
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
    public PostSubmitResponse submitPost(UUID userId, UUID postId,
                                         com.secondlife.secondlife.dto.request.PostSubmitRequest request) {
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
            String reason = scanResult.contains(":") ? scanResult.substring(scanResult.indexOf(":") + 1).trim()
                    : "Nội dung bài đăng không hợp lệ";
            post.setStatus("REJECTED");
            post.setRejectionReason("AI tự động từ chối: " + reason);
            postRepository.save(post);
            return new PostSubmitResponse(
                    "REJECTED",
                    false,
                    null,
                    null,
                    "Bài đăng bị từ chối bởi hệ thống AI: " + reason,
                    null);
        }

        // BƯỚC B: Kiểm tra ngưỡng giá
        if (post.getPrice() != null && post.getPrice().compareTo(highValueThreshold) > 0) {
            // Hàng giá trị cao → kiểm định
            BigDecimal totalFee = inspectionFee.add(shippingFee);

            // Kiểm tra credit của Seller
            UserCredit userCredit = creditService.getUserCredit(userId);
            // Dùng post_credits như một đơn vị tương đương tiền (1 credit = 1000 VNĐ)
            // Nếu không đủ credit, trả về response kèm số tiền còn thiếu để FE hiển thị lựa
            // chọn
            long requiredCredits = totalFee.longValue() / 1000;
            if (userCredit.getPostCredits() < requiredCredits) {
                BigDecimal shortfall = totalFee.subtract(BigDecimal.valueOf(userCredit.getPostCredits() * 1000L));
                return new PostSubmitResponse(
                        "INSUFFICIENT_CREDIT",
                        true,
                        inspectionFee,
                        shippingFee,
                        "Không đủ credit để thanh toán phí kiểm định và vận chuyển. Vui lòng nạp thêm hoặc thanh toán trực tiếp qua chuyển khoản.",
                        shortfall);
            }

            // Trừ credit
            userCredit.setPostCredits((int) (userCredit.getPostCredits() - requiredCredits));

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
                    null);
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
                        null);
            } else {
                post.setStatus("ACTIVE");
                postRepository.save(post);

                return new PostSubmitResponse(
                        "ACTIVE",
                        false,
                        null,
                        null,
                        "Bài đăng đã được duyệt và hiển thị trên sàn.",
                        null);
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
                post.getTitle(), post.getDescription(), post.getPrice());
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
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getAdminPosts(
            String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Post> spec = org.springframework.data.jpa.domain.Specification
                .where((org.springframework.data.jpa.domain.Specification<Post>) null);
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
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getPublicPosts(
            UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        // Public posts should only return ACTIVE ones
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("status"),
                "ACTIVE");

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
    public AiPriceEstimationResponse estimatePrice(UUID userId, UUID postId, String requestId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }

        // Build context from post data
        String itemName = post.getItemId() != null ?
                itemRepository.findById(post.getItemId())
                        .map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định")
                : "Không xác định";

        String description = post.getDescription() != null ? post.getDescription() : "Không có mô tả";
        String priceContext = post.getAiSuggestedPrice() != null
                ? "Giá mà người bán đã đề xuất: " + post.getAiSuggestedPrice() + " VNĐ."
                : "";

        String pricePrompt = String.format(
                "B?n l� chuy�n gia d?nh gi� d? gia d?ng cu d� qua s? d?ng t?i th? tru?ng Vi?t Nam. " +
                "H�y d?nh gi� s?n ph?m sau d?a tr�n th�ng tin du?c cung c?p. " +
                "S?n ph?m: %s.\nM� t? hi?n t?i: %s.\n%s\nTh�ng tin ngu?i b�n d� cung c?p th�m:\n%s\n\n" +
                "QUY T?C �?NH GI� B?T BU?C:\n" +
                "- Gi� d? cu PH?I TH?P HON gi� mua m?i t? 30%% d?n 70%% tu? t�nh tr?ng.\n" +
                "- N?u s?n ph?m m?i 99%% (d�ng < 1 tu?n): gi?m 20-30%% so v?i gi� mua m?i.\n" +
                "- N?u s?n ph?m t?t (d�ng v�i th�ng): gi?m 40-50%% so v?i gi� mua m?i.\n" +
                "- N?u s?n ph?m d� d�ng l�u (> 1 nam): gi?m 50-70%%.\n" +
                "- TUY?T �?I KH�NG dua ra gi� b?ng ho?c cao hon gi� mua m?i.\n\n" +
                "Tr? v? K?T QU? THEO ��NG �?NH D?NG JSON sau, kh�ng gi?i th�ch g� th�m:\n" +
                "{\"fairPriceMin\": <s? nguy�n VN�>, \"fairPriceMax\": <s? nguy�n VN�>, \"suggestedPrice\": <s? nguy�n VN�>, \"expectedSellTime\": \"<v� d?: 3-5 ng�y>\"}" ,
                itemName, description, priceContext, chatContext.toString());

        ChatClient googleClient = ChatClient.builder(googleChatModel).build();
        String aiResult = googleClient.prompt(pricePrompt).call().content();

        BigDecimal fairPriceMin = BigDecimal.ZERO;
        BigDecimal fairPriceMax = BigDecimal.ZERO;
        BigDecimal suggestedPrice = BigDecimal.ZERO;
        String expectedSellTime = "3-5 ngày";

        try {
            // Extract JSON from the AI response
            String jsonStr = aiResult;
            if (aiResult.contains("{") && aiResult.contains("}")) {
                jsonStr = aiResult.substring(aiResult.indexOf("{"), aiResult.lastIndexOf("}") + 1);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(jsonStr);
            fairPriceMin = BigDecimal.valueOf(node.get("fairPriceMin").asLong());
            fairPriceMax = BigDecimal.valueOf(node.get("fairPriceMax").asLong());
            suggestedPrice = BigDecimal.valueOf(node.get("suggestedPrice").asLong());
            if (node.has("expectedSellTime")) {
                expectedSellTime = node.get("expectedSellTime").asText();
            }
        } catch (Exception e) {
            // Fallback: try to extract numbers from raw text
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{5,}").matcher(aiResult.replaceAll("[.,]", ""));
            java.util.List<Long> nums = new java.util.ArrayList<>();
            while (m.find()) nums.add(Long.parseLong(m.group()));
            if (nums.size() >= 3) {
                fairPriceMin = BigDecimal.valueOf(nums.get(0));
                fairPriceMax = BigDecimal.valueOf(nums.get(1));
                suggestedPrice = BigDecimal.valueOf(nums.get(2));
            }
        }

        return AiPriceEstimationResponse.builder()
                .requestId(requestId)
                .fairPriceMin(fairPriceMin)
                .fairPriceMax(fairPriceMax)
                .suggestedPrice(suggestedPrice)
                .modelVersion("SecondLife-AI-v2.1")
                .expectedSellTime(expectedSellTime)
                .createdAt(java.time.Instant.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getMyPosts(UUID userId,
                                                                                                           org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb
                .equal(root.get("user").get("id"), userId);
        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);
    }
}