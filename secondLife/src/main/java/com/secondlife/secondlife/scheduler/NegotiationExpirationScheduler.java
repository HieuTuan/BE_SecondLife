package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NegotiationExpirationScheduler {

    private final NegotiationRepository negotiationRepository;
    private final PostRepository postRepository;
    private final ChatService chatService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    // Run every 5 minutes
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void expireNegotiations() {
        List<Negotiation> expiredNegotiations = negotiationRepository.findByStatusAndExpiredAtBefore(NegotiationStatus.ACCEPTED, Instant.now());
        
        if (expiredNegotiations.isEmpty()) {
            return;
        }
        
        log.info("Found {} expired negotiations. Reverting post statuses.", expiredNegotiations.size());
        
        for (Negotiation negotiation : expiredNegotiations) {
            Post post=postRepository.findByIdForUpdate(negotiation.getPost().getId()).orElseThrow();
            entityManager.refresh(post);entityManager.refresh(negotiation);
            // Checkout owns the reservation until its bank-payment deadline is processed.
            if("PAYMENT_PENDING".equals(post.getStatus()) || negotiation.getStatus()!=NegotiationStatus.ACCEPTED) continue;
            negotiation.setStatus(NegotiationStatus.EXPIRED);
            negotiationRepository.save(negotiation);
            
            if ("RESERVED".equals(post.getStatus())) {
                post.setStatus("ACTIVE");
                postRepository.save(post);
                
                // Notify seller
                chatService.sendSystemMessage(post.getId(), negotiation.getBuyer().getId(),
                    String.format("{\"type\":\"SYSTEM\", \"message\":\"The negotiation with %s has expired because they didn't check out in time. Your product is now ACTIVE again.\"}", negotiation.getBuyer().getEmail()));
                
                // Notify buyer
                chatService.sendSystemMessage(post.getId(), negotiation.getBuyer().getId(),
                    String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}",
                    negotiation.getStatus(), negotiation.getOfferedPrice(), negotiation.getId()));
            }
        }
    }
}
