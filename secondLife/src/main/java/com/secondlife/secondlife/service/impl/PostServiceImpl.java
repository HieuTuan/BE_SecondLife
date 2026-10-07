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
                           @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel") org.springframework.ai.chat.model.ChatModel googleChatModel,
                           com.secondlife.secondlife.repository.AiChatMessageRepository chatMessageRepository) {
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
                .map(com.secondlife.secondlife.entity.Category::getName).orElse("KhÃ´ng xÃ¡c Ä‘á»‹nh");
        final String itemName = request.getItemId() != null ? 
                itemRepository.findById(request.getItemId())
                    .map(com.secondlife.secondlife.entity.Item::getName).orElse("KhÃ´ng xÃ¡c Ä‘á»‹nh") 
                : "KhÃ´ng xÃ¡c Ä‘á»‹nh";

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        aiRequest.setPostId(post.getId());
        // Prompt for Llava/Gemini to check if the image actually matches the item, and describe it.
        String messagePrompt = String.format(
                "Dá»±a vÃ o hÃ¬nh áº£nh, sáº£n pháº©m nÃ y Ä‘Æ°á»£c ngÆ°á»i dÃ¹ng chá»n lÃ  loáº¡i: '%s'. HÃ£y kiá»ƒm tra xem hÃ¬nh áº£nh cÃ³ thá»±c sá»± lÃ  '%s' khÃ´ng. " +
                "Náº¾U KHÃ”NG PHáº¢I (vÃ­ dá»¥: áº£nh lÃ  ná»“i cÆ¡m Ä‘iá»‡n nhÆ°ng ngÆ°á»i dÃ¹ng chá»n lÃ² vi sÃ³ng), Báº®T BUá»˜C báº¯t Ä‘áº§u cÃ¢u tráº£ lá»i cá»§a báº¡n báº±ng Ä‘Ãºng cá»¥m tá»« '[Cáº¢NH BÃO]' vÃ  giáº£i thÃ­ch sá»± sai lá»‡ch rÃµ rÃ ng. " +
                "Náº¾U ÄÃšNG, hÃ£y nháº­n xÃ©t ngáº¯n gá»n vá» ngoáº¡i hÃ¬nh vÃ  tÃ¬nh tráº¡ng váº­t lÃ½ cá»§a sáº£n pháº©m trong áº£nh. KhÃ´ng cáº§n thÃªm lá»i chÃ o hay bÃ¬nh luáº­n thá»«a.",
                itemName, itemName);
        aiRequest.setMessage(messagePrompt);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            aiRequest.setImage(request.getImages().get(0));
        }

        // This will deduct 1 chat credit if applicable, or we might say the first
        // message doesn't cost a chat credit?
        // User said: "Má»—i bÃ i Ä‘Äƒng Ä‘i kÃ¨m 5 lÆ°á»£t chat". So it might cost a chat credit.
        // Actually, AiChatService will create a new session and count=1.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);

        // 4. Fetch or Generate Template
        CategoryQuestionTemplate template = templateRepository
                .findByCategoryIdAndItemId(request.getCategoryId(), request.getItemId())
                .orElseGet(() -> {
                    // Generate new template
                    String promptText = String.format(
                            "Báº¡n lÃ  chuyÃªn gia vá» Ä‘á»“ cÅ©. HÃ£y táº¡o má»™t Ä‘oáº¡n máº«u cÃ¢u há»i (template) báº±ng tiáº¿ng Viá»‡t Ä‘á»ƒ há»i ngÆ°á»i dÃ¹ng cÃ¡c thÃ´ng tin cáº§n thiáº¿t khi há» muá»‘n Ä‘Äƒng bÃ¡n má»™t sáº£n pháº©m thuá»™c danh má»¥c '%s', loáº¡i sáº£n pháº©m '%s'. \n" +
                                    "\n" +
                                    "YÃŠU Cáº¦U Äá»ŠNH Dáº NG Báº®T BUá»˜C:\n" +
                                    "Má»—i cÃ¢u há»i pháº£i theo ÄÃšNG Ä‘á»‹nh dáº¡ng sau, trÃªn tá»«ng dÃ²ng riÃªng biá»‡t:\n" +
                                    "**[TÃªn thÃ´ng tin cáº§n há»i]** (VÃ­ dá»¥: gá»£i Ã½ 1, gá»£i Ã½ 2, gá»£i Ã½ 3)\n" +
                                    "\n" +
                                    "LÆ°u Ã½:\n" +
                                    "- Báº¯t buá»™c pháº£i cÃ³ tá»« 'VÃ­ dá»¥:' trong ngoáº·c Ä‘Æ¡n, cÃ¡c gá»£i Ã½ tráº£ lá»i (quick replies) ngÄƒn cÃ¡ch nhau báº±ng dáº¥u pháº©y.\n" +
                                    "- ÄÆ°a ra 3-5 cÃ¢u há»i quan trá»ng nháº¥t vá» tÃ¬nh tráº¡ng sáº£n pháº©m.\n" +
                                    "- KHÃ”NG ÄÆ¯á»¢C Há»ŽI vá» thÆ°Æ¡ng hiá»‡u, hÃ£ng sáº£n xuáº¥t hay tÃªn sáº£n pháº©m vÃ¬ ngÆ°á»i dÃ¹ng Ä‘Ã£ chá»n hoáº·c cung cáº¥p thÃ´ng tin nÃ y rá»“i.\n" +
                                    "- KhÃ´ng viáº¿t thÃªm cÃ¢u dáº«n dáº¯t hay káº¿t luáº­n. Chá»‰ tráº£ vá» danh sÃ¡ch cÃ¢u há»i.",
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
        if (aiResponse.getReply().contains("[Cáº¢NH BÃO]")) {
            combinedResponse = aiResponse.getReply() + "\n\n*(Há»‡ thá»‘ng Ä‘Ã£ táº¡m dá»«ng Ä‘Æ°a ra cÃ¢u há»i gá»£i Ã½ vÃ¬ hÃ¬nh áº£nh khÃ´ng khá»›p. Vui lÃ²ng kiá»ƒm tra láº¡i hÃ¬nh áº£nh hoáº·c danh má»¥c báº¡n Ä‘Ã£ chá»n!)*";
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

        // Cáº­p nháº­t thÃ´ng tin ngÆ°á»i dÃ¹ng chá»‘t
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setPrice(request.getPrice());
        postRepository.save(post);

        // BÆ¯á»šC A: AI Scan bÃ i Ä‘Äƒng
        String scanResult = aiScanPost(post);
        if (scanResult.startsWith("REJECTED")) {
            String reason = scanResult.contains(":") ? scanResult.substring(scanResult.indexOf(":") + 1).trim()
                    : "Ná»™i dung bÃ i Ä‘Äƒng khÃ´ng há»£p lá»‡";
            post.setStatus("REJECTED");
            post.setRejectionReason("AI tá»± Ä‘á»™ng tá»« chá»‘i: " + reason);
            postRepository.save(post);
            return new PostSubmitResponse(
                    "REJECTED",
                    false,
                    null,
                    null,
                    "BÃ i Ä‘Äƒng bá»‹ tá»« chá»‘i bá»Ÿi há»‡ thá»‘ng AI: " + reason,
                    null);
        }

        // BÆ¯á»šC B: Kiá»ƒm tra ngÆ°á»¡ng giÃ¡
        if (post.getPrice() != null && post.getPrice().compareTo(highValueThreshold) > 0) {
            // HÃ ng giÃ¡ trá»‹ cao â†’ kiá»ƒm Ä‘á»‹nh
            BigDecimal totalFee = inspectionFee.add(shippingFee);

            // Kiá»ƒm tra credit cá»§a Seller
            UserCredit userCredit = creditService.getUserCredit(userId);
            // DÃ¹ng post_credits nhÆ° má»™t Ä‘Æ¡n vá»‹ tÆ°Æ¡ng Ä‘Æ°Æ¡ng tiá»n (1 credit = 1000 VNÄ)
            // Náº¿u khÃ´ng Ä‘á»§ credit, tráº£ vá» response kÃ¨m sá»‘ tiá»n cÃ²n thiáº¿u Ä‘á»ƒ FE hiá»ƒn thá»‹ lá»±a
            // chá»n
            long requiredCredits = totalFee.longValue() / 1000;
            if (userCredit.getPostCredits() < requiredCredits) {
                BigDecimal shortfall = totalFee.subtract(BigDecimal.valueOf(userCredit.getPostCredits() * 1000L));
                return new PostSubmitResponse(
                        "INSUFFICIENT_CREDIT",
                        true,
                        inspectionFee,
                        shippingFee,
                        "KhÃ´ng Ä‘á»§ credit Ä‘á»ƒ thanh toÃ¡n phÃ­ kiá»ƒm Ä‘á»‹nh vÃ  váº­n chuyá»ƒn. Vui lÃ²ng náº¡p thÃªm hoáº·c thanh toÃ¡n trá»±c tiáº¿p qua chuyá»ƒn khoáº£n.",
                        shortfall);
            }

            // Trá»« credit
            userCredit.setPostCredits((int) (userCredit.getPostCredits() - requiredCredits));

            // Táº¡o InspectionOrder
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
                    "Sáº£n pháº©m cÃ³ giÃ¡ trá»‹ cao. ÄÃ£ táº¡o Ä‘Æ¡n kiá»ƒm Ä‘á»‹nh. Vui lÃ²ng gá»­i sáº£n pháº©m Ä‘áº¿n trung tÃ¢m kiá»ƒm Ä‘á»‹nh theo hÆ°á»›ng dáº«n.",
                    null);
        } else {
            // HÃ ng giÃ¡ thÆ°á»ng â†’ AI Ä‘Ã£ duyá»‡t
            // BÆ¯á»šC C: Kiá»ƒm tra trÃ¹ng láº·p áº£nh
            boolean isDuplicate = checkDuplicateImage(post);
            if (isDuplicate) {
                post.setStatus("PENDING");
                postRepository.save(post);
                return new PostSubmitResponse(
                        "PENDING",
                        false,
                        null,
                        null,
                        "Há»‡ thá»‘ng phÃ¡t hiá»‡n áº£nh cÃ³ dáº¥u hiá»‡u trÃ¹ng láº·p. BÃ i Ä‘Äƒng Ä‘ang chá» nhÃ¢n viÃªn kiá»ƒm duyá»‡t thá»§ cÃ´ng.",
                        null);
            } else {
                post.setStatus("ACTIVE");
                postRepository.save(post);

                return new PostSubmitResponse(
                        "ACTIVE",
                        false,
                        null,
                        null,
                        "BÃ i Ä‘Äƒng Ä‘Ã£ Ä‘Æ°á»£c duyá»‡t vÃ  hiá»ƒn thá»‹ trÃªn sÃ n.",
                        null);
            }
        }
    }

    private boolean checkDuplicateImage(Post post) {
        // TODO: Thay tháº¿ báº±ng code gá»i API check trÃ¹ng áº£nh thá»±c táº¿
        // (Do hiá»‡n táº¡i chÆ°a tháº¥y class gá»i API trong source code)
        return false;
    }

    /**
     * Gá»i Google Gemini Ä‘á»ƒ scan bÃ i Ä‘Äƒng.
     * Returns "APPROVED" hoáº·c "REJECTED:<reason>"
     */
    private String aiScanPost(Post post) {
        ChatClient client = ChatClient.builder(googleChatModel).build();
        String prompt = String.format(
                "Báº¡n lÃ  há»‡ thá»‘ng kiá»ƒm duyá»‡t tá»± Ä‘á»™ng cá»§a sÃ n mua bÃ¡n Ä‘á»“ cÅ© SecondLife. " +
                        "HÃ£y Ä‘Ã¡nh giÃ¡ bÃ i Ä‘Äƒng sáº£n pháº©m sau cÃ³ phÃ¹ há»£p Ä‘á»ƒ hiá»ƒn thá»‹ trÃªn sÃ n khÃ´ng? " +
                        "Kiá»ƒm tra: (1) MÃ´ táº£ cÃ³ khá»›p vá»›i loáº¡i sáº£n pháº©m, (2) KhÃ´ng cÃ³ dáº¥u hiá»‡u lá»«a Ä‘áº£o, " +
                        "(3) KhÃ´ng vi pháº¡m ná»™i quy (hÃ ng cáº¥m, hÃ ng giáº£, thÃ´ng tin sai lá»‡ch). " +
                        "TiÃªu Ä‘á»: %s. MÃ´ táº£: %s. GiÃ¡: %s VNÄ. " +
                        "CHá»ˆ tráº£ vá» Ä‘Ãºng má»™t trong hai dáº¡ng sau, khÃ´ng giáº£i thÃ­ch thÃªm: " +
                        "APPROVED hoáº·c REJECTED:<lÃ½ do ngáº¯n gá»n báº±ng tiáº¿ng Viá»‡t>",
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
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb.conjunction();
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
                        .map(com.secondlife.secondlife.entity.Item::getName).orElse("KhÃ´ng xÃ¡c Ä‘á»‹nh")
                : "KhÃ´ng xÃ¡c Ä‘á»‹nh";
        String description = post.getDescription() != null ? post.getDescription() : "Không có mô tả";
        String priceContext = post.getAiSuggestedPrice() != null
                ? "Giá mà người bán đã đề xuất: " + post.getAiSuggestedPrice() + " VNĐ."
                : "";

        StringBuilder chatContext = new StringBuilder();
        if (requestId != null && !requestId.trim().isEmpty()) {
            try {
                java.util.UUID sessionId = java.util.UUID.fromString(requestId);
                java.util.List<com.secondlife.secondlife.entity.AiChatMessage> messages = chatMessageRepository.findBySessionIdOrderBySentAtAsc(sessionId);
                for (com.secondlife.secondlife.entity.AiChatMessage msg : messages) {
                    chatContext.append(msg.getRole()).append(": ").append(msg.getMessageContent()).append("\n");
                }
            } catch (Exception e) {
                // Ignore invalid requestId or other exceptions
            }
        }

        String pricePrompt = String.format(
                "Bạn là chuyên gia định giá đồ gia dụng cũ đã qua sử dụng tại thị trường Việt Nam. " +
                "Hãy định giá sản phẩm sau dựa trên thông tin được cung cấp. " +
                "Sản phẩm: %s.\nMô tả hiện tại: %s.\n%s\nThông tin người bán đã cung cấp thêm:\n%s\n\n" +
                "QUY TẮC ĐỊNH GIÁ BẮT BUỘC:\n" +
                "- Giá đồ cũ PHẢI THẤP HƠN giá mua mới từ 30%% đến 70%% tuỳ tình trạng.\n" +
                "- Nếu sản phẩm mới 99%% (dùng < 1 tuần): giảm 20-30%% so với giá mua mới.\n" +
                "- Nếu sản phẩm tốt (dùng vài tháng): giảm 40-50%% so với giá mua mới.\n" +
                "- Nếu sản phẩm đã dùng lâu (> 1 năm): giảm 50-70%%.\n" +
                "- TUYỆT ĐỐI KHÔNG đưa ra giá bằng hoặc cao hơn giá mua mới.\n\n" +
                "Trả về KẾT QUẢ THEO ĐÚNG ĐỊNH DẠNG JSON sau, không giải thích gì thêm:\n" +
                "{\"fairPriceMin\": <số nguyên VND>, \"fairPriceMax\": <số nguyên VND>, \"suggestedPrice\": <số nguyên VND>, \"expectedSellTime\": \"<ví dụ: 3-5 ngày>\"}" ,
                itemName, description, priceContext, chatContext.toString());
        ChatClient googleClient = ChatClient.builder(googleChatModel).build();
        String aiResult = googleClient.prompt(pricePrompt).call().content();

        BigDecimal fairPriceMin = BigDecimal.ZERO;
        BigDecimal fairPriceMax = BigDecimal.ZERO;
        BigDecimal suggestedPrice = BigDecimal.ZERO;
        String expectedSellTime = "3-5 ngÃ y";

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

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostDto updateDraft(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.UpdateDraftRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }
        
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setItemCondition(request.getItemCondition());
        post.setPrice(request.getPrice());
        
        // Status may remain DRAFT or whatever it is, unless we explicitly change it.
        // Frontend sends "status": 'DRAFT', let's assume it should stay DRAFT if it was DRAFT
        post = postRepository.save(post);
        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostDto acceptDescription(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.AcceptDescriptionRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }
        
        post.setDescription(request.getDescription());
        // For 'descriptionAccepted' behavior, we save it to description.
        // If there is an 'aiDescription' we might want to keep it unchanged.
        post = postRepository.save(post);
        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);
    }
}
