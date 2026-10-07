package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.entity.AiChatMessage;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.service.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatClient ollamaChatClient;
    private final ChatClient googleChatClient;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final long maxImageBytes;
    private final int maxImages;
    private final int rateLimitMaxMessages;
    private final long rateLimitWindowSeconds;
    private final int sessionMaxMessages;
    private final String ollamaModel;

    public AiChatServiceImpl(
            @org.springframework.beans.factory.annotation.Qualifier("ollamaChatModel") org.springframework.ai.chat.model.ChatModel ollamaChatModel,
            @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel") org.springframework.ai.chat.model.ChatModel googleChatModel,
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            UserRepository userRepository,
            PostRepository postRepository,
            @Value("${app.listing.max-image-bytes}") long maxImageBytes,
            @Value("${app.listing.max-images}") int maxImages,
            @Value("${app.ai.chat.rate-limit-max-messages}") int rateLimitMaxMessages,
            @Value("${app.ai.chat.rate-limit-window-seconds}") long rateLimitWindowSeconds,
            @Value("${app.ai.chat.session-max-messages}") int sessionMaxMessages,
            @Value("${spring.ai.ollama.chat.options.model}") String ollamaModel) {
        requirePositive("app.listing.max-image-bytes", maxImageBytes);
        requirePositive("app.listing.max-images", maxImages);
        requirePositive("app.ai.chat.rate-limit-max-messages", rateLimitMaxMessages);
        requirePositive("app.ai.chat.rate-limit-window-seconds", rateLimitWindowSeconds);
        requirePositive("app.ai.chat.session-max-messages", sessionMaxMessages);
        if (ollamaModel == null || ollamaModel.isBlank()) {
            throw new IllegalArgumentException("spring.ai.ollama.chat.options.model must not be blank");
        }
        this.ollamaChatClient = ChatClient.builder(ollamaChatModel).build();
        this.googleChatClient = ChatClient.builder(googleChatModel).build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.maxImageBytes = maxImageBytes;
        this.maxImages = maxImages;
        this.rateLimitMaxMessages = rateLimitMaxMessages;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.sessionMaxMessages = sessionMaxMessages;
        this.ollamaModel = ollamaModel;
    }

    @Override
    @Transactional
    public AiChatResponse processChat(AiChatRequest request, UUID currentUserId) {
        if (request.getMessage() == null || request.getMessage().isBlank() || request.getMessage().length() > 4000)
            throw new com.secondlife.secondlife.exception.BadRequestException("Message is required and must not exceed 4000 characters");
        User user = userRepository.findByIdForRoleUpdate(currentUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (messageRepository.countRecentUserMessages(currentUserId, Instant.now().minusSeconds(rateLimitWindowSeconds)) >= rateLimitMaxMessages)
            throw new ConflictException("AI chat rate limit reached; retry after " + rateLimitWindowSeconds + " seconds");

        AiChatSession session;
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new NotFoundException("Session not found"));
            requireSessionOwner(session, currentUserId);
            requirePostOwner(session.getPostId(), currentUserId);
            if (session.isCompleted())
                throw new ConflictException("Session is already completed");
            if (session.getMessageCount() >= sessionMaxMessages)
                throw new ConflictException("AI chat session limit reached; finalize the current description");
        } else {
            requirePostOwner(request.getPostId(), currentUserId);
            session = new AiChatSession();
            session.setUser(user);
            session.setPostId(request.getPostId());
            session.setMessageCount(0);
            session.setCompleted(false);
            session = sessionRepository.save(session);
        }

        List<Message> aiMessages = new ArrayList<>();
        String systemPromptText = """
                Bạn là một trợ lý AI chat bot cho hệ thống Secondlife, chuyên hỗ trợ người dùng mua bán đồ gia dụng cũ.
                Nhiệm vụ của bạn là hỗ trợ thu thập thông tin chuyên sâu dựa trên loại sản phẩm (item) và danh mục (category) mà người dùng đang muốn bán để viết mô tả hoặc định giá.
                Hãy chủ động đặt các câu hỏi chi tiết, bám sát vào đặc thù kỹ thuật của từng loại sản phẩm. 
                Ví dụ: 
                - Nếu là máy hút mùi: hỏi về mã sản phẩm, công suất hút, điều khiển bằng gì, chất lượng, độ ồn nhiều hay ít, kích thước máy hút mùi, màu sắc, phụ kiện đi kèm, thời gian bảo hành còn lại, động cơ gì, xuất xứ...
                - Nếu là tủ lạnh: hỏi về dung tích, công nghệ Inverter, đóng tuyết hay không, kích thước, thời gian đã sử dụng...
                - Áp dụng tư duy tương tự cho các sản phẩm khác. Không hỏi dồn dập một danh sách dài cùng lúc, hãy hỏi một cách tinh tế và giao tiếp tự nhiên.
                Dựa vào ngôn ngữ của user mà lựa chọn ngôn ngữ trả lời phù hợp (ưu tiên tiếng Việt).
                Chỉ trả lời những mặt hàng liên quan đến đồ gia dụng cũ, từ chối lịch sự các chủ đề khác.
                QUY TẮC BẮT BUỘC: KHÔNG BAO GIỜ bắt đầu câu trả lời bằng các câu dẫn dắt như 'Dưới đây là...', 'Đây là...', 'Sau đây là...', 'Vâng, đây là...'. Đi thẳng vào nội dung chính.
                Chỉ hỗ trợ thu thập thông tin và viết mô tả. Tuyệt đối không đưa ra giá hay khoảng giá trực tiếp trong chat.
                Khi người dùng muốn định giá, hướng dẫn sử dụng chức năng Định giá AI riêng trên bài đăng.
                """;
        aiMessages.add(new SystemMessage(systemPromptText));

        // Load history
        List<AiChatMessage> history = messageRepository.findBySessionIdOrderBySentAtAsc(session.getId());
        for (AiChatMessage msg : history) {
            if ("USER".equals(msg.getRole())) {
                aiMessages.add(new UserMessage(msg.getMessageContent()));
            } else {
                aiMessages.add(new AssistantMessage(msg.getMessageContent()));
            }
        }

        var files = new ArrayList<org.springframework.web.multipart.MultipartFile>();
        if (request.getImages() != null) files.addAll(request.getImages());
        files.removeIf(file -> file == null || file.isEmpty());
        if (files.size() > maxImages) throw new com.secondlife.secondlife.exception.BadRequestException("At most " + maxImages + " images are allowed");
        var media = new ArrayList<org.springframework.ai.content.Media>();
        for (var image : files) {
            if (image == null || image.isEmpty() || image.getContentType() == null || image.getSize() > maxImageBytes
                    || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType()))
                throw new com.secondlife.secondlife.exception.BadRequestException("Image must be a non-empty JPEG, PNG or WebP no larger than " + maxImageBytes + " bytes");
            try {
                media.add(new org.springframework.ai.content.Media(
                        org.springframework.util.MimeTypeUtils.parseMimeType(image.getContentType()),
                        new org.springframework.core.io.ByteArrayResource(image.getBytes())));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read image file", e);
            }
        }
        aiMessages.add(UserMessage.builder().text(request.getMessage()).media(media).build());

        // Save user message to DB
        if (session.getPostId() != null) {
            var post = postRepository.findById(session.getPostId()).orElseThrow(() -> new NotFoundException("Post not found"));
            post.setDescriptionAccepted(false);
            postRepository.save(post);
        }
        AiChatMessage userDbMessage = new AiChatMessage();
        userDbMessage.setSession(session);
        userDbMessage.setRole("USER");
        userDbMessage.setMessageContent(request.getMessage());
        messageRepository.save(userDbMessage);

        session.setMessageCount(session.getMessageCount() + 1);
        sessionRepository.save(session);

        String aiReply;
        if (!media.isEmpty()) {
            // Use Google Gemini for image
            Prompt prompt = new Prompt(aiMessages);
            aiReply = googleChatClient.prompt(prompt).call().content();
        } else {
            // Use Ollama for text
            Prompt prompt = new Prompt(aiMessages,
                    OllamaChatOptions.builder().model(ollamaModel).build());
            aiReply = ollamaChatClient.prompt(prompt).call().content();
        }

        if (aiReply == null || aiReply.isBlank()) throw new AiProviderException("AI chat returned an empty response");
        // Save AI response to DB
        AiChatMessage aiDbMessage = new AiChatMessage();
        aiDbMessage.setSession(session);
        aiDbMessage.setRole("ASSISTANT");
        aiDbMessage.setMessageContent(aiReply);
        messageRepository.save(aiDbMessage);

        return AiChatResponse.builder()
                .sessionId(session.getId())
                .reply(aiReply)
                .build();
    }

    @Override
    @Transactional
    public PostFinalizeResponse finalizeChat(UUID sessionId, UUID currentUserId) {
        userRepository.findByIdForRoleUpdate(currentUserId).orElseThrow(() -> new NotFoundException("User not found"));
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        requireSessionOwner(session, currentUserId);
        requirePostOwner(session.getPostId(), currentUserId);

        if (session.isCompleted()) {
            throw new ConflictException("Session is already completed");
        }

        List<Message> aiMessages = new ArrayList<>();
        // Load history
        List<AiChatMessage> history = messageRepository.findBySessionIdOrderBySentAtAsc(session.getId());
        for (AiChatMessage msg : history) {
            if ("USER".equals(msg.getRole())) {
                aiMessages.add(new UserMessage(msg.getMessageContent()));
            } else {
                aiMessages.add(new AssistantMessage(msg.getMessageContent()));
            }
        }

        // Add final prompt to summarize
        String finalizePrompt = """
                Dựa vào toàn bộ cuộc trò chuyện trên, hãy tổng hợp và viết MỘT ĐOẠN VĂN MÔ TẢ NGẮN GỌN, CHUYÊN NGHIỆP về sản phẩm để đăng bán.
                
                YÊU CẦU QUAN TRỌNG:
                1. BẮT BUỘC đặt toàn bộ nội dung mô tả vào trong cặp thẻ <desc> và </desc>.
                2. TUYỆT ĐỐI không viết gì thêm bên ngoài cặp thẻ này.
                3. Nội dung bên trong thẻ <desc> phải là VĂN XUÔI THUẦN TÚY, súc tích, thu hút người mua.
                4. TUYỆT ĐỐI KHÔNG sao chép lại dạng bảng hỏi-đáp, không có dòng kiểu '• Hãng?: ...', '• Tình trạng?: ...' vào trong mô tả.
                5. Các thông tin từ cuộc trò chuyện phải được tích hợp tự nhiên vào đoạn văn mô tả.
                6. Có thể dùng Markdown (in đậm, xuống dòng) để làm nổi bật nội dung bên trong thẻ.
                """;
        aiMessages.add(new UserMessage(finalizePrompt));

        Prompt prompt = new Prompt(aiMessages, OllamaChatOptions.builder().model(ollamaModel).build());
        String finalDescription = call(ollamaChatClient, prompt);

        if (finalDescription != null) {
            // Bước 1: Rút trích nội dung trong thẻ <desc> nếu có
            if (finalDescription.contains("<desc>") && finalDescription.contains("</desc>")) {
                finalDescription = finalDescription.substring(
                        finalDescription.indexOf("<desc>") + 6,
                        finalDescription.lastIndexOf("</desc>")).trim();
            }

            // Bước 2: BẮT BUỘC áp dụng Regex xoá câu dẫn dắt ở mọi trường hợp (vì AI có thể nhét câu dẫn dắt vào cả bên trong thẻ <desc>)
            finalDescription = finalDescription.replaceAll(
                    "(?i)^(Dưới đây là|Đây là|Sau đây là|Chắc chắn rồi|Vâng|Dạ|Tôi đã|Vâng, đây là)[\\s\\S]*?:\\s*", "").trim();
        }

        if (finalDescription == null || finalDescription.isBlank() || finalDescription.length() > 10000)
            throw new AiProviderException("AI description is empty or too long");
        session.setCompleted(true);
        sessionRepository.save(session);
        
        return new PostFinalizeResponse(finalDescription, null);
    }

    private static void requirePositive(String property, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }

    }

    private void requireSessionOwner(AiChatSession session, UUID userId) {
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }
    }

    private String call(ChatClient client, Prompt prompt) {
        try { return client.prompt(prompt).call().content(); }
        catch (Exception ex) { throw new AiProviderException("AI chat unavailable; please retry", ex); }
    }

    private void requirePostOwner(UUID postId, UUID userId) {
        if (postId == null)
            return;
        var post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }
    }
}
