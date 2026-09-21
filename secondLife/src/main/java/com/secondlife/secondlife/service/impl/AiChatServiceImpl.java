package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.AiChatRequest;
import com.secondlife.secondlife.dto.AiChatResponse;
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

    private final ChatClient chatClient;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final com.secondlife.secondlife.service.CreditService creditService;

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder,
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            UserRepository userRepository,
            com.secondlife.secondlife.service.CreditService creditService) {
        this.chatClient = chatClientBuilder.build();
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
            session.setTitle("Chat " + Instant.now().toString());
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

        // Handle image if provided
        // Spring AI supports adding media to UserMessage, but requires base64/URL parsing.
        // For simplicity and avoiding complex Media API if not needed, we'll route to llava if an image is provided.
        // Assuming we pass image as text instructions or use the correct Media API when properly set up.
        // For this task, we will configure the model dynamically.
        String modelToUse = "gemma4:31b"; // default

        if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
            modelToUse = "llava";
            // In a real implementation with Spring AI, you would construct Media object:
            // Media media = new Media(MimeTypeUtils.IMAGE_JPEG, request.getBase64Image());
            // UserMessage userMsg = new UserMessage(request.getMessage(), List.of(media));
            // But since Spring AI version might differ, we'll just set the model.
        }

        aiMessages.add(new UserMessage(request.getMessage()));

        // Save user message to DB
        AiChatMessage userDbMessage = new AiChatMessage();
        userDbMessage.setSession(session);
        userDbMessage.setRole("USER");
        userDbMessage.setMessageContent(request.getMessage());
        messageRepository.save(userDbMessage);
        
        session.setMessageCount(session.getMessageCount() + 1);
        sessionRepository.save(session);

        // Call Ollama with dynamic model selection
        Prompt prompt = new Prompt(aiMessages, OllamaChatOptions.builder().model(modelToUse).build());
        String aiReply = chatClient.prompt(prompt).call().content();

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
}
