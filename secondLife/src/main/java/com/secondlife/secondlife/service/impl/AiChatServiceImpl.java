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
            if (session.getMessageCount() >= 5) {
                try {
                    creditService.deductChatCredit(currentUserId);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Chat limit exceeded (max 5 messages). Please purchase more chat credits to continue.");
                }
            }
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
                Bạn là một trợ lý AI chat bot cho hệ thống Secondlife, nơi người dùng có thể mua bán đồ gia dụng cũ đã qua sử dụng.
                Dựa vào ngôn ngữ của user mà lựa chọn ngôn ngữ trả lời cho phù hợp. Ưu tiên tiếng Việt.
                Chỉ trả lời những mặt hàng liên quan đến đồ gia dụng cũ, nếu user hỏi về các vấn đề khác thì từ chối trả lời một cách lịch sự và tinh tế.
                Câu trả lời chuyên nghiệp, không ưu tiên dùng emoji để phản hồi người dùng.
                Đối với những câu hỏi nhờ bạn định giá, vui lòng yêu cầu người dùng cung cấp thông tin chi tiết về sản phẩm và hướng dẫn để có thể định giá chính xác nhất.
                QUY TẮC BẮT BUỘC TUYỆT ĐỐI: KHÔNG BAO GIỜ được bắt đầu câu trả lời bằng các câu dẫn dắt như 'Dưới đây là...', 'Đây là...', 'Sau đây là...', 'Vâng, đây là...', 'Tôi đã viết...', 'Chắc chắn rồi,...' hoặc bất kỳ câu giới thiệu tương tự nào. Đi thẳng vào nội dung chính ngay lập tức.
                Chỉ hỗ trợ thu thập thông tin và viết mô tả. Tuyệt đối không đưa ra giá hay khoảng giá.
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
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(
                    imageBytes);
            org.springframework.ai.content.Media media = new org.springframework.ai.content.Media(
                    org.springframework.util.MimeTypeUtils.IMAGE_JPEG, resource);
            aiMessages.add(UserMessage.builder().text(request.getMessage()).media(java.util.List.of(media)).build());
        } else {
            aiMessages.add(new UserMessage(request.getMessage()));
        }

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
                    org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("gemma4:31b-cloud").build());
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

        Prompt prompt = new Prompt(aiMessages);
        String finalDescription = googleChatClient.prompt(prompt).call().content();

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
        // Add prompt for price
        String pricePrompt = "Dựa vào tình trạng và mô tả sản phẩm ở trên, hãy đưa ra một mức giá hợp lý (bằng số, đơn vị VNĐ) để bán đồ cũ thanh lý. LƯU Ý QUAN TRỌNG: Giá đồ cũ thanh lý LUÔN PHẢI THẤP HƠN giá mua mới (nếu trong đoạn chat có đề cập đến giá lúc mua mới). Chỉ trả về một con số duy nhất, không có chữ hay dấu phẩy. Ví dụ: 500000";
        aiMessages.add(new AssistantMessage(finalDescription));
        aiMessages.add(new UserMessage(pricePrompt));
        Prompt priceAiPrompt = new Prompt(aiMessages);
        String suggestedPriceStr = googleChatClient.prompt(priceAiPrompt).call().content();

        java.math.BigDecimal suggestedPrice = java.math.BigDecimal.ZERO;
        try {
            suggestedPrice = new java.math.BigDecimal(suggestedPriceStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            // fallback if AI fails to return just number
        }

        session.setCompleted(true);
        sessionRepository.save(session);
        
        return new PostFinalizeResponse(finalDescription, null);
    }

    private static void requirePositive(String property, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }

        return new PostFinalizeResponse(finalDescription, suggestedPrice);
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
