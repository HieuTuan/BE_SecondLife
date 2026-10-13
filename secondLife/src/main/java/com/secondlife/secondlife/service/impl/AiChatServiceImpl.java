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
import com.secondlife.secondlife.service.ListingCreditService;
import com.secondlife.secondlife.enums.CreditType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
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
    private final ListingCreditService listingCreditService;

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
            @Value("${spring.ai.ollama.chat.options.model}") String ollamaModel,
            ListingCreditService listingCreditService) {
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
        this.listingCreditService = listingCreditService;
    }

    @Override
    public AiChatResponse processChat(AiChatRequest request, UUID currentUserId) {
        if (request.getMessage() == null || request.getMessage().isBlank() || request.getMessage().length() > 4000)
            throw new com.secondlife.secondlife.exception.BadRequestException("Message is required and must not exceed 4000 characters");

        // Build media list early (before any TX) so validation errors are thrown before DB writes
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

        // Phase 1: Validate + save user message in a COMMITTED transaction
        // (so user message is persisted even if the AI call subsequently fails)
        UUID sessionId = saveUserMessageAndGetSessionId(request, currentUserId);
        
        // Count message to use as idempotency key for credit deduction
        int currentMessageCount = sessionRepository.findById(sessionId).map(AiChatSession::getMessageCount).orElse(1);

        // Phase 2: Load history and call AI — OUTSIDE any transaction, no DB locks held
        List<Message> aiMessages = buildAiMessageHistory(sessionId, request.getMessage(), media);

        String aiReply;
        try {
            if (!media.isEmpty()) {
                aiReply = googleChatClient.prompt(new Prompt(aiMessages)).call().content();
            } else {
                try {
                    aiReply = ollamaChatClient.prompt(
                            new Prompt(aiMessages, OllamaChatOptions.builder().model(ollamaModel).build())
                    ).call().content();
                } catch (Exception ollamaEx) {
                    log.warn("Ollama chat failed, falling back to Google Gemini: {}", ollamaEx.getMessage());
                    aiReply = googleChatClient.prompt(new Prompt(aiMessages)).call().content();
                }
            }
        } catch (Exception ex) {
            refundAiChatCredit(currentUserId, sessionId, currentMessageCount);
            throw new AiProviderException("AI chat unavailable; please retry", ex);
        }

        if (aiReply == null || aiReply.isBlank()) {
            refundAiChatCredit(currentUserId, sessionId, currentMessageCount);
            throw new AiProviderException("AI chat returned an empty response");
        }

        // Phase 3: Persist AI reply in a new short transaction
        saveAiReply(sessionId, aiReply);

        return AiChatResponse.builder()
                .sessionId(sessionId)
                .reply(aiReply)
                .build();
    }

    /** Phase 1: validate, rate-limit check, create/load session, save user message. Commits immediately. */
    @Transactional
    protected UUID saveUserMessageAndGetSessionId(AiChatRequest request, UUID currentUserId) {
        User user = userRepository.findById(currentUserId)
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
        
        // Consume credit for this message (except the first automated image analysis message)
        if (session.getMessageCount() > 1) {
            String idempotencyKey = "AI_CHAT_MSG:" + session.getId() + ":" + session.getMessageCount();
            listingCreditService.consume(currentUserId, CreditType.AI_CHAT, idempotencyKey);
        }

        return session.getId();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundAiChatCredit(UUID userId, UUID sessionId, int messageCount) {
        String idempotencyKey = "AI_CHAT_MSG:" + sessionId + ":" + messageCount + "_REFUND";
        listingCreditService.refund(userId, CreditType.AI_CHAT, idempotencyKey);
    }

    /** Build prompt message list from persisted history + current user message. Read-only. */
    @Transactional(readOnly = true)
    protected List<Message> buildAiMessageHistory(UUID sessionId, String userMessage,
                                                  java.util.List<org.springframework.ai.content.Media> media) {
        List<Message> aiMessages = new ArrayList<>();
        String systemPromptText = """
                Bạn là trợ lý AI chuyên nghiệp của sàn SecondLife, chuyên hỗ trợ người bán mua bán đồ gia dụng cũ.
                NHIỆM VỤ CỦA BẠN:
                1. Khi nhận hình ảnh ban đầu: Phân tích khách quan tình trạng ngoại quan, màu sắc, chi tiết nhìn thấy của sản phẩm. Không suy đoán thông số không thể nhìn thấy trên ảnh.
                2. Khi người dùng trò chuyện: Đóng vai trò chuyên viên tư vấn thu thập thông tin còn thiếu (thời gian đã sử dụng, tình trạng hoạt động thực tế, lỗi nếu có, phụ kiện kèm theo).
                QUY TẮC BẮT BUỘC:
                - Đi thẳng vào nội dung chính, KHÔNG bắt đầu câu trả lời bằng các câu dẫn dắt rườm rà như 'Dưới đây là...', 'Đây là...', 'Sau đây là...', 'Vâng, đây là...'.
                - Ngôn ngữ: Tiếng Việt tự nhiên, súc tích, lịch sự.
                - Phạm vi: Chỉ phục vụ các mặt hàng đồ gia dụng cũ, thiết bị đời sống; từ chối lịch sự nếu người dùng hỏi về các chủ đề khác hoặc hàng cấm.
                - KHÔNG BAO GIỜ đưa ra mức giá hoặc khoảng giá cụ thể trong cuộc trò chuyện. Khi người dùng muốn định giá, hãy hướng dẫn họ sử dụng chức năng 'Định giá AI' riêng trên bài đăng sau khi hoàn tất mô tả.
                """;
        aiMessages.add(new SystemMessage(systemPromptText));

        List<AiChatMessage> history = messageRepository.findBySessionIdOrderBySentAtAsc(sessionId);
        if (history.isEmpty()) {
            if (media != null && !media.isEmpty()) {
                aiMessages.add(UserMessage.builder().text(userMessage).media(media).build());
            } else {
                aiMessages.add(new UserMessage(userMessage));
            }
        } else {
            for (int i = 0; i < history.size(); i++) {
                AiChatMessage msg = history.get(i);
                if ("USER".equals(msg.getRole())) {
                    if (i == history.size() - 1 && media != null && !media.isEmpty()) {
                        aiMessages.add(UserMessage.builder().text(msg.getMessageContent()).media(media).build());
                    } else {
                        aiMessages.add(new UserMessage(msg.getMessageContent()));
                    }
                } else {
                    aiMessages.add(new AssistantMessage(msg.getMessageContent()));
                }
            }
        }
        return aiMessages;
    }

    /** Phase 3: persist AI reply in a new committed transaction. */
    @Transactional
    protected void saveAiReply(UUID sessionId, String aiReply) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        AiChatMessage aiDbMessage = new AiChatMessage();
        aiDbMessage.setSession(session);
        aiDbMessage.setRole("ASSISTANT");
        aiDbMessage.setMessageContent(aiReply);
        messageRepository.save(aiDbMessage);
    }

    @Transactional(readOnly = true)
    protected void validateFinalizeRequest(UUID sessionId, UUID currentUserId) {
        userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found"));
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        requireSessionOwner(session, currentUserId);
        // Removed requirePostOwner since it's already checked in PostServiceImpl
        if (session.isCompleted()) {
            throw new ConflictException("Session is already completed");
        }
    }

    @Override
    public PostFinalizeResponse regenerateDescription(UUID sessionId, UUID currentUserId) {
        validateAndResetSessionForRegen(sessionId, currentUserId);
        return finalizeChat(sessionId, currentUserId);
    }

    @Transactional
    protected void validateAndResetSessionForRegen(UUID sessionId, UUID currentUserId) {
        userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found"));
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        requireSessionOwner(session, currentUserId);
        
        session.setCompleted(false);
        sessionRepository.save(session);
    }

    @Override
    public PostFinalizeResponse finalizeChat(UUID sessionId, UUID currentUserId) {
        validateFinalizeRequest(sessionId, currentUserId);
        
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        List<Message> aiMessages = new ArrayList<>();
        aiMessages.add(new SystemMessage("""
                Bạn là chuyên gia biên tập mô tả sản phẩm cho sàn thương mại điện tử đồ gia dụng cũ SecondLife.
                Nhiệm vụ của bạn là tổng hợp toàn bộ thông tin đã trao đổi thành một bài viết mô tả sản phẩm hoàn chỉnh, trung thực, chuyên nghiệp và hấp dẫn người mua.
                """));

        // Load history
        List<AiChatMessage> history = messageRepository.findBySessionIdOrderBySentAtAsc(session.getId());
        for (AiChatMessage msg : history) {
            if ("USER".equals(msg.getRole())) {
                aiMessages.add(new UserMessage(msg.getMessageContent()));
            } else {
                aiMessages.add(new AssistantMessage(msg.getMessageContent()));
            }
        }

        // Add final prompt to summarize both engaging title and professional description
        String finalizePrompt = """
                Dựa vào toàn bộ cuộc trò chuyện trên, hãy tổng hợp bài đăng bán sản phẩm gồm TIÊU ĐỀ HẤP DẪN và ĐOẠN VĂN MÔ TẢ CHUYÊN NGHIỆP:

                YÊU CẦU QUAN TRỌNG:
                1. TIÊU ĐỀ BÀI ĐĂNG (BẮT BUỘC đặt trong cặp thẻ <title> và </title>):
                   - Tiêu đề phải hấp dẫn, chuyên nghiệp chuẩn sàn TMĐT theo cấu trúc:
                     [Tên loại thiết bị] [Thương hiệu] [Dung tích/Công suất/Model cốt lõi] - [Tình trạng độ mới nổi bật].
                   - Ví dụ:
                     * 'Nồi Cơm Điện BlueStone 1.8L - Hàng Lướt Like New 99%'
                     * 'Bàn Ăn Hòa Phát Gỗ Tự Nhiên - Mới 99% Chắc Chắn Bền Đẹp'
                     * 'Máy Giặt Electrolux Inverter 8.5kg Cửa Ngang - Vắt Êm Chạy Tốt'
                   - Tuyệt đối KHÔNG viết tiêu đề chung chung sơ sài như 'BlueStone Nồi cơm điện' hay 'Bàn ăn'.

                2. ĐOẠN VĂN MÔ TẢ (BẮT BUỘC đặt trong cặp thẻ <desc> và </desc>):
                   - Tóm tắt tình trạng chung, ngoại quan và điểm nổi bật của sản phẩm.
                   - Chi tiết thông số và hiện trạng thực tế (độ mới, trầy xước nếu có, phụ kiện đi kèm).
                   - Lý do thanh lý hoặc lưu ý sử dụng cho người mua.
                   - Diễn đạt bằng lời văn tự nhiên kết hợp các gạch đầu dòng rõ ràng, không sao chép dạng hỏi-đáp thô sơ.
                   - Có thể dùng Markdown (in đậm, xuống dòng, gạch đầu dòng).

                3. TUYỆT ĐỐI không viết gì thêm bên ngoài các cặp thẻ này.
                """;
        aiMessages.add(new UserMessage(finalizePrompt));

        Prompt prompt = new Prompt(aiMessages, OllamaChatOptions.builder().model(ollamaModel).build());
        String rawOutput = call(ollamaChatClient, prompt);
        String finalTitle = null;
        String finalDescription = null;

        if (rawOutput != null) {
            rawOutput = rawOutput.trim();

            int tStart = rawOutput.indexOf("<title>");
            int tEnd = rawOutput.lastIndexOf("</title>");
            if (tStart != -1 && tEnd != -1 && tEnd > tStart + 7) {
                finalTitle = rawOutput.substring(tStart + 7, tEnd).trim();
            }

            int dStart = rawOutput.indexOf("<desc>");
            int dEnd = rawOutput.lastIndexOf("</desc>");
            if (dStart != -1 && dEnd != -1 && dEnd > dStart + 6) {
                finalDescription = rawOutput.substring(dStart + 6, dEnd).trim();
            } else if (dStart != -1 && dEnd == -1) {
                finalDescription = rawOutput.substring(dStart + 6).trim();
            } else {
                finalDescription = rawOutput;
            }

            if (finalDescription.startsWith("```")) {
                finalDescription = finalDescription.replaceFirst("^```(?:markdown|xml|text)?\\s*", "")
                        .replaceFirst("\\s*```$", "").trim();
            }

            finalDescription = finalDescription.replaceAll(
                    "(?i)^(Dưới đây là|Đây là|Sau đây là|Chắc chắn rồi|Vâng|Dạ|Tôi đã|Vâng, đây là)[\\s\\S]*?:\\s*", "").trim();
        }

        if (finalDescription == null || finalDescription.isBlank() || finalDescription.length() > 10000)
            throw new AiProviderException("AI description is empty or too long");
            
        return saveFinalizeResult(sessionId, finalTitle, finalDescription);
    }
    
    @Transactional
    protected PostFinalizeResponse saveFinalizeResult(UUID sessionId, String finalTitle, String finalDescription) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        session.setCompleted(true);
        sessionRepository.save(session);
        return new PostFinalizeResponse(finalTitle, finalDescription, null);
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
        try { 
            return client.prompt(prompt).call().content(); 
        } catch (Exception ex) { 
            if (client != googleChatClient && googleChatClient != null) {
                try {
                    log.warn("Ollama call failed in finalizeChat, falling back to Google Gemini: {}", ex.getMessage());
                    return googleChatClient.prompt(prompt).call().content();
                } catch (Exception ignored) {}
            }
            throw new AiProviderException("AI chat unavailable; please retry", ex); 
        }
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
