package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.AiChatRequest;
import com.secondlife.secondlife.dto.AiChatResponse;
/*
import com.secondlife.secondlife.entity.AiChatMessage;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.UserRepository;
*/
import com.secondlife.secondlife.service.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;
    /*
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    */

    public AiChatServiceImpl(ChatClient.Builder chatClientBuilder/*,
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository,
            UserRepository userRepository*/) {
        this.chatClient = chatClientBuilder.build();
        /*
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        */
    }

    @Override
    // @Transactional
    public AiChatResponse processChat(AiChatRequest request, UUID currentUserId) {
        /*
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AiChatSession session;
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Session not found"));
        } else {
            session = new AiChatSession();
            session.setUser(user);
            session.setTitle("Chat " + Instant.now().toString());
            session = sessionRepository.save(session);
        }
        */

        // 0. Add System Prompt
        List<Message> aiMessages = new ArrayList<>();
        String systemPromptText = """
                Bạn là một trợ lý AI chat bot cho hệ thống Secondlife, nơi người dùng có thể mua bán đồ gia dụng cũ đã qua sử dụng.
                Dựa vào ngôn ngữ của user mà lựa chọn ngôn ngữ trả lời cho phù hợp. Ưu tiên tiếng Việt
                Chỉ trả lời những mặt hàng liên quan đến đồ gia dụng cũ, nếu user hỏi về các vấn đề khác thì từ chối trả lời một cách lịch sự và tinh tế.
                Câu trả lời chuyên nghiệp, không ưu tiên dùng emoji để phản hồi người dùng.
                Đối với những câu hỏi nhờ bạn định giá, vui lòng yêu cầu người dùng cung cấp thông tin chi tiết về sản phẩm để có thể định giá chính xác nhất.
                """;
        aiMessages.add(new SystemMessage(systemPromptText));

        /*
        // 1. Load history
        List<AiChatMessage> history = messageRepository.findBySessionIdOrderBySentAtAsc(session.getId());

        for (AiChatMessage msg : history) {
            if ("USER".equals(msg.getRole())) {
                aiMessages.add(new UserMessage(msg.getMessageContent()));
            } else {
                aiMessages.add(new AssistantMessage(msg.getMessageContent()));
            }
        }
        */

        // 2. Add current message
        aiMessages.add(new UserMessage(request.getMessage()));

        /*
        // 3. Save user message to DB
        AiChatMessage userDbMessage = new AiChatMessage();
        userDbMessage.setSession(session);
        userDbMessage.setRole("USER");
        userDbMessage.setMessageContent(request.getMessage());
        messageRepository.save(userDbMessage);
        */

        // 4. Call Ollama
        Prompt prompt = new Prompt(aiMessages);
        String aiReply = chatClient.prompt(prompt).call().content();

        /*
        // 5. Save AI response to DB
        AiChatMessage aiDbMessage = new AiChatMessage();
        aiDbMessage.setSession(session);
        aiDbMessage.setRole("ASSISTANT");
        aiDbMessage.setMessageContent(aiReply);
        messageRepository.save(aiDbMessage);
        */

        return AiChatResponse.builder()
                .sessionId(request.getSessionId()) // Return the requested sessionId or null
                .reply(aiReply)
                .build();
    }
}
