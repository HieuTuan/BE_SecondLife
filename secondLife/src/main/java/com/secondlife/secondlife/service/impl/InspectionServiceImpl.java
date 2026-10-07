package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserRole;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionServiceImpl implements InspectionService {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final PostRepository postRepository;
    private final com.secondlife.secondlife.repository.ShipmentRepository shipments;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final com.secondlife.secondlife.service.ListingPublicationService publication;
    @Qualifier("googleGenAiChatModel")
    private final org.springframework.ai.chat.model.ChatModel googleChatModel;

    @Override
    @Transactional(readOnly = true)
    public List<InspectionOrderResponse> getMyOrders(UUID inspectorId) {
        return inspectionOrderRepository.findByInspectorId(inspectorId)
                .stream()
                .map(InspectionOrderResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return inspectionOrderRepository.findByStatus(status, pageable)
                    .map(InspectionOrderResponse::from);
        }
        return inspectionOrderRepository.findAll(pageable)
                .map(InspectionOrderResponse::from);
    }

    @Override
    @Transactional
    public InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Inspection order not found"));

        if (!"PENDING".equals(order.getStatus())) throw new ConflictException("Order is already completed");

        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> new NotFoundException("Inspector not found"));

        if (inspector.getAccountStatus() != AccountStatus.ACTIVE || !inspector.hasRole("INSPECTOR")) {
            throw new BadRequestException("Assigned user must be an active INSPECTOR");
        }

        order.setInspector(inspector);
        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public InspectionOrderResponse submitResult(UUID orderId, UUID inspectorId, InspectionResultRequest request) {
        UUID owner = inspectionOrderRepository.findOwnerId(orderId).orElseThrow(() -> new NotFoundException("Inspection order not found"));
        userRepository.findByIdForRoleUpdate(owner).orElseThrow(() -> new NotFoundException("User not found"));
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Inspection order not found"));

        if (order.getInspector() == null || !order.getInspector().getId().equals(inspectorId)) {
            throw new ForbiddenException("This order is not assigned to you");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new ConflictException("Order is already completed");
        }
        var carrierLegs=shipments.findByInspectionOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus())).toList();
        if (!carrierLegs.isEmpty() && !"DELIVERED".equals(carrierLegs.getLast().getStatus()))
            throw new ConflictException("GHN must confirm delivery to the inspection center before the inspection result");

        order.setNote(request.getNote());

        Post post = postRepository.findByIdForUpdate(order.getPost().getId()).orElseThrow(() -> new NotFoundException("Post not found"));

        if ("FAILED".equals(request.getStatus())) {
            order.setStatus("FAILED");
            post.setStatus("REJECTED");
            post.setRejectionReason("Inspection failed: " + request.getNote());
            postRepository.save(post);
        } else if ("PASSED".equals(request.getStatus())) {
            order.setStatus("PASSED");
            // AI Scan lần 2 để xác nhận trước khi ACTIVE
            String scanResult = aiScanPost(post);
            if ("APPROVED".equals(scanResult)) {
                publication.activate(post);
            } else {
                String reason = scanResult.contains(":") ? scanResult.substring(scanResult.indexOf(":") + 1).trim() : "AI final check failed";
                post.setStatus("REJECTED");
                post.setRejectionReason("Post failed final AI scan after inspection: " + reason);
            }
            postRepository.save(post);
        } else {
            throw new BadRequestException("Invalid status. Must be PASSED or FAILED");
        }

        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public void createInspectorAccount(String email, String fullName, String password, UUID managerId) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already exists");
        }

        Role inspectorRole = roleRepository.findByCode("INSPECTOR")
                .orElseThrow(() -> new RuntimeException("Role INSPECTOR not found in database"));

        User newInspector = new User();
        newInspector.setEmail(email);
        newInspector.setPasswordHash(passwordEncoder.encode(password));
        newInspector.setAccountStatus(AccountStatus.ACTIVE);
        newInspector.setEmailVerified(true);
        newInspector = userRepository.save(newInspector);

        UserRole userRole = new UserRole(newInspector, inspectorRole);
        newInspector.getUserRoles().add(userRole);
        userRepository.save(newInspector);
    }

    @Override
    @Transactional(readOnly = true)
    public List<?> getStaffList(UUID managerId) {
        // Trả về danh sách User có role INSPECTOR
        return userRepository.findAll().stream()
                .filter(u -> u.getUserRoles().stream()
                        .anyMatch(ur -> "INSPECTOR".equals(ur.getRole().getCode())))
                .map(userMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gọi Google Gemini để scan nội dung bài đăng.
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
            post.getTitle(), post.getDescription(), post.getPrice()
        );

        Prompt aiPrompt = new Prompt(new UserMessage(prompt));
        String result;
        try { result = client.prompt(aiPrompt).call().content(); }
        catch (Exception ex) { throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex); }
        if (result == null) throw new AiProviderException("AI moderation returned no result");
        result = result.trim();
        if (!"APPROVED".equals(result) && !(result.startsWith("REJECTED:") && !result.substring(9).trim().isEmpty()))
            throw new AiProviderException("AI moderation returned an invalid result");
        return result;
    }
}
