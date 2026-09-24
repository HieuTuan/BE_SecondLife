package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.entity.AiChatMessage;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
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
    private final com.secondlife.secondlife.service.CreditService creditService;

    public AiChatServiceImpl(@org.springframework.beans.factory.annotation.Qualifier("ollamaChatModel") org.springframework.ai.chat.model.ChatModel ollamaChatModel,
            @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel") org.springframework.ai.chat.model.ChatModel googleChatModel,
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            UserRepository userRepository,
            com.secondlife.secondlife.service.CreditService creditService) {
        this.ollamaChatClient = ChatClient.builder(ollamaChatModel).build();
        this.googleChatClient = ChatClient.builder(googleChatModel).build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
    }

    @Override
    @Transactional
    public AiChatResponse processChat(AiChatRequest request, UUID currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AiChatSession session;
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            if (session.getMessageCount() >= 5) {
                try {
                    creditService.deductChatCredit(currentUserId);
                } catch (Exception e) {
                    throw new RuntimeException("Chat limit exceeded (max 5 messages). Please purchase more chat credits to continue.");
                }
            }
        } else {
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
                Dựa vào ngôn ngữ của user mà lựa chọn ngôn ngữ trả lời cho phù hợp. Ưu tiên tiếng Việt
                Chỉ trả lời những mặt hàng liên quan đến đồ gia dụng cũ, nếu user hỏi về các vấn đề khác thì từ chối trả lời một cách lịch sự và tinh tế.
                Câu trả lời chuyên nghiệp, không ưu tiên dùng emoji để phản hồi người dùng.
                Đối với những câu hỏi nhờ bạn định giá, vui lòng yêu cầu người dùng cung cấp thông tin chi tiết về sản phẩm và hướng dẫn để có thể định giá chính xác nhất.
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

        if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
            // Strip data:image/...;base64, prefix if it exists
            String base64Data = request.getBase64Image();
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(imageBytes);
            org.springframework.ai.content.Media media = new org.springframework.ai.content.Media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG, resource);
            aiMessages.add(UserMessage.builder().text(request.getMessage()).media(java.util.List.of(media)).build());
        } else {
            aiMessages.add(new UserMessage(request.getMessage()));
        }

        // Save user message to DB
        AiChatMessage userDbMessage = new AiChatMessage();
        userDbMessage.setSession(session);
        userDbMessage.setRole("USER");
        userDbMessage.setMessageContent(request.getMessage());
        messageRepository.save(userDbMessage);
        
        session.setMessageCount(session.getMessageCount() + 1);
        sessionRepository.save(session);

        String aiReply;
        if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
            // Use Google Gemini for image
            Prompt prompt = new Prompt(aiMessages);
            aiReply = googleChatClient.prompt(prompt).call().content();
        } else {
            // Use Ollama for text
            Prompt prompt = new Prompt(aiMessages, org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("gemma4:31b-cloud").build());
            aiReply = ollamaChatClient.prompt(prompt).call().content();
        }

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
    public String finalizeChat(UUID sessionId, UUID currentUserId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        if (!session.getUser().getId().equals(currentUserId)) {
            throw new RuntimeException("Unauthorized to finalize this session");
        }
        
        if (session.isCompleted()) {
            throw new RuntimeException("Session is already completed");
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
        String finalizePrompt = "Dựa vào toàn bộ cuộc trò chuyện trên, hãy tổng hợp và viết ra một đoạn mô tả hoàn chỉnh, hấp dẫn cho sản phẩm này để đăng bán. Trả về đúng nội dung mô tả, không cần giải thích hay thêm bình luận gì khác.";
        aiMessages.add(new UserMessage(finalizePrompt));

        Prompt prompt = new Prompt(aiMessages, org.springframework.ai.ollama.api.OllamaChatOptions.builder().model("gemma4:31b-cloud").build());
        String finalDescription = ollamaChatClient.prompt(prompt).call().content();

        session.setCompleted(true);
        sessionRepository.save(session);
        
        return finalDescription;
    }
}
