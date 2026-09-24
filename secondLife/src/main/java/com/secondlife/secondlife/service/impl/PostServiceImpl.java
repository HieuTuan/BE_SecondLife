package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.PostService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PostServiceImpl implements PostService {

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

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           CreditService creditService,
                           AiChatService aiChatService,
                           CategoryQuestionTemplateRepository templateRepository,
                           com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository,
                           CategoryRepository categoryRepository,
                           ItemRepository itemRepository,
                           ChatClient.Builder chatClientBuilder,
                           com.secondlife.secondlife.service.CloudinaryService cloudinaryService) {
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
        
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            try {
                String imageUrl = cloudinaryService.uploadImage(request.getImage());
                post.setImageUrl(imageUrl);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to upload image to Cloudinary", e);
            }
        }
        
        post = postRepository.save(post);

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        aiRequest.setPostId(post.getId());
        // Prompt for Llava to only describe the visual condition
        aiRequest.setMessage("Dựa vào hình ảnh được cung cấp, hãy chỉ nhận xét ngắn gọn về ngoại hình và tình trạng vật lý của sản phẩm này. Không cần thêm lời chào hay bình luận gì khác.");
        aiRequest.setImage(request.getImage());

        // This will deduct 1 chat credit if applicable, or we might say the first message doesn't cost a chat credit? 
        // User said: "Mỗi bài đăng đi kèm 5 lượt chat". So it might cost a chat credit. 
        // Actually, AiChatService will create a new session and count=1.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);

        // 4. Fetch or Generate Template
        String templateText = templateRepository.findByCategoryIdAndItemId(request.getCategoryId(), request.getItemId())
                .map(CategoryQuestionTemplate::getTemplateText)
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
                    templateRepository.save(newTemplate);

                    return generatedTemplate;
                });

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
    public String finalizeChatAndDescription(UUID userId, UUID sessionId) {
        // 1. Tell AiChatService to summarize
        String finalDescription = aiChatService.finalizeChat(sessionId, userId);

        // 2. Fetch the session to get postId
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getPostId() != null) {
            Post post = postRepository.findById(session.getPostId())
                    .orElseThrow(() -> new RuntimeException("Post not found"));
            post.setDescription(finalDescription);
            postRepository.save(post);
        }
        
        return finalDescription;
    }

    @Override
    @Transactional
    public void submitPost(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (!post.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        post.setStatus("PENDING");
        postRepository.save(post);
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
        // We could also store the reject reason in the entity if there is a field for it
        // and potentially refund the post_credit to the user.
        postRepository.save(post);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Post> getAdminPosts(String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
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
        return postRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Post> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {
        // Public posts should only return ACTIVE ones
        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
        
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        return postRepository.findAll(spec, pageable);
    }
}
