package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.AiChatRequest;
import com.secondlife.secondlife.dto.AiChatResponse;
import com.secondlife.secondlife.dto.PostInitRequest;
import com.secondlife.secondlife.dto.PostInitResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AiChatService;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.PostService;
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

    public PostServiceImpl(PostRepository postRepository,
                           UserRepository userRepository,
                           CreditService creditService,
                           AiChatService aiChatService,
                           CategoryQuestionTemplateRepository templateRepository,
                           com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
        this.aiChatService = aiChatService;
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
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
        post.setStatus("DRAFT");
        // could set imageUrl if it's uploaded to cloud storage, but here we have base64
        post = postRepository.save(post);

        // 3. Call AI to analyze image
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setSessionId(null);
        // Prompt for Llava to only describe the visual condition
        aiRequest.setMessage("Please describe the physical appearance and condition of this product based on the image.");
        aiRequest.setBase64Image(request.getBase64Image());

        // This will deduct 1 chat credit if applicable, or we might say the first message doesn't cost a chat credit? 
        // User said: "Mỗi bài đăng đi kèm 5 lượt chat". So it might cost a chat credit. 
        // Actually, AiChatService will create a new session and count=1.
        AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);

        // 4. Append Template
        String templateText = templateRepository.findByCategoryId(request.getCategoryId())
                .map(CategoryQuestionTemplate::getTemplateText)
                .orElse("Please provide more details about this item (e.g. brand, age, condition, origin) so I can help write a description.");

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
}
