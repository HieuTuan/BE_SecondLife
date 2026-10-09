package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.NegotiationRequestDTO;
import com.secondlife.secondlife.dto.response.NegotiationResponseDTO;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NegotiationService;
import com.secondlife.secondlife.service.ChatService;
import com.secondlife.secondlife.common.PageableUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NegotiationServiceImpl implements NegotiationService {

    private static final Set<String> ALLOWED_NEGOTIATION_SORT_PROPERTIES = Set.of(
            "id", "offeredPrice", "status", "createdAt", "updatedAt", "expiredAt"
    );
    private static final Sort DEFAULT_NEGOTIATION_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final NegotiationRepository negotiationRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    private final int maxRejections;
    private final long acceptedTtlHours;

    public NegotiationServiceImpl(NegotiationRepository negotiationRepository, PostRepository postRepository,
                                  UserRepository userRepository, ChatService chatService,
                                  @Value("${app.negotiation.max-rejections}") int maxRejections,
                                  @Value("${app.negotiation.accepted-ttl-hours}") long acceptedTtlHours) {
        if (maxRejections <= 0 || acceptedTtlHours <= 0)
            throw new IllegalArgumentException("Negotiation rejection limit and accepted TTL must be positive");
        this.negotiationRepository = negotiationRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.maxRejections = maxRejections;
        this.acceptedTtlHours = acceptedTtlHours;
    }

    @Override
    @Transactional
    public NegotiationResponseDTO createNegotiation(UUID buyerId, NegotiationRequestDTO requestDTO) {
        Post post = postRepository.findById(requestDTO.getPostId())
                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (!"ACTIVE".equals(post.getStatus())) {
            throw new BadRequestException("Post is not active for negotiation");
        }

        if (post.getUser().getId().equals(buyerId)) {
            throw new BadRequestException("You cannot negotiate your own post");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new NotFoundException("Buyer not found"));

        // Check if there's any pending or accepted negotiation
        boolean hasActive = negotiationRepository.existsByPostIdAndBuyerIdAndStatusIn(
                post.getId(), buyerId, List.of(NegotiationStatus.PENDING, NegotiationStatus.ACCEPTED)
        );
        if (hasActive) {
            throw new BadRequestException("You already have an active negotiation for this post");
        }

        // Check reject limits
        long rejectedCount = negotiationRepository.countRejectedNegotiations(post.getId(), buyerId);
        if (rejectedCount >= maxRejections) {
            throw new BadRequestException("You have reached the maximum number of rejected negotiations for this post");
        }

        BigDecimal offeredPrice = requestDTO.getOfferedPrice();

        // Check max rejected price
        BigDecimal maxRejectedPrice = negotiationRepository.findMaxRejectedPrice(post.getId(), buyerId).orElse(BigDecimal.ZERO);
        if (offeredPrice.compareTo(maxRejectedPrice) <= 0) {
            throw new BadRequestException("Offered price must be strictly greater than your highest rejected price: " + maxRejectedPrice);
        }

        if (offeredPrice.compareTo(post.getPrice()) >= 0) {
            throw new BadRequestException("Offered price must be lower than the original price");
        }

        Negotiation negotiation = new Negotiation();
        negotiation.setPost(post);
        negotiation.setBuyer(buyer);
        negotiation.setOfferedPrice(offeredPrice);
        negotiation.setStatus(NegotiationStatus.PENDING);

        negotiation = negotiationRepository.save(negotiation);
        
        chatService.sendSystemMessage(post.getId(), buyerId, 
            String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}", 
            negotiation.getStatus(), negotiation.getOfferedPrice(), negotiation.getId()));

        return mapToDTO(negotiation);
    }

    @Override
    @Transactional
    public NegotiationResponseDTO acceptNegotiation(UUID sellerId, UUID negotiationId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new NotFoundException("Negotiation not found"));

        if (!negotiation.getPost().getUser().getId().equals(sellerId)) {
            throw new BadRequestException("You are not the seller of this post");
        }

        if (negotiation.getStatus() != NegotiationStatus.PENDING) {
            throw new BadRequestException("Negotiation is not in PENDING state");
        }

        negotiation.setStatus(NegotiationStatus.ACCEPTED);
        negotiation.setExpiredAt(Instant.now().plus(acceptedTtlHours, ChronoUnit.HOURS));

        negotiation = negotiationRepository.save(negotiation);

        // Reserve the post
        Post post = negotiation.getPost();
        post.setStatus("RESERVED");
        postRepository.save(post);

        // Bulk reject other pending negotiations for this post
        List<Negotiation> otherPending = negotiationRepository.findByPostIdAndStatus(post.getId(), NegotiationStatus.PENDING);
        for (Negotiation other : otherPending) {
            if (!other.getId().equals(negotiation.getId())) {
                other.setStatus(NegotiationStatus.REJECTED);
                negotiationRepository.save(other);
                chatService.sendSystemMessage(post.getId(), other.getBuyer().getId(),
                    String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}",
                    other.getStatus(), other.getOfferedPrice(), other.getId()));
            }
        }
        
        chatService.sendSystemMessage(post.getId(), negotiation.getBuyer().getId(), 
            String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}", 
            negotiation.getStatus(), negotiation.getOfferedPrice(), negotiation.getId()));

        return mapToDTO(negotiation);
    }

    @Override
    @Transactional
    public NegotiationResponseDTO rejectNegotiation(UUID sellerId, UUID negotiationId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new NotFoundException("Negotiation not found"));

        if (!negotiation.getPost().getUser().getId().equals(sellerId)) {
            throw new BadRequestException("You are not the seller of this post");
        }

        if (negotiation.getStatus() != NegotiationStatus.PENDING) {
            throw new BadRequestException("Negotiation is not in PENDING state");
        }

        negotiation.setStatus(NegotiationStatus.REJECTED);

        negotiation = negotiationRepository.save(negotiation);
        
        chatService.sendSystemMessage(negotiation.getPost().getId(), negotiation.getBuyer().getId(), 
            String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}", 
            negotiation.getStatus(), negotiation.getOfferedPrice(), negotiation.getId()));

        return mapToDTO(negotiation);
    }

    @Override
    @Transactional
    public NegotiationResponseDTO cancelNegotiation(UUID buyerId, UUID negotiationId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new NotFoundException("Negotiation not found"));

        if (!negotiation.getBuyer().getId().equals(buyerId)) {
            throw new BadRequestException("You are not the buyer of this negotiation");
        }

        if (negotiation.getStatus() != NegotiationStatus.PENDING) {
            throw new BadRequestException("Only PENDING negotiations can be cancelled");
        }

        negotiation.setStatus(NegotiationStatus.CANCELLED);

        negotiation = negotiationRepository.save(negotiation);
        
        chatService.sendSystemMessage(negotiation.getPost().getId(), negotiation.getBuyer().getId(), 
            String.format("{\"type\":\"OFFER\", \"status\":\"%s\", \"price\":%s, \"negotiationId\":\"%s\"}", 
            negotiation.getStatus(), negotiation.getOfferedPrice(), negotiation.getId()));

        return mapToDTO(negotiation);
    }

    @Override
    public Page<NegotiationResponseDTO> getBuyerNegotiations(UUID buyerId, Pageable pageable) {
        Pageable safePageable = PageableUtils.sanitize(pageable, ALLOWED_NEGOTIATION_SORT_PROPERTIES, DEFAULT_NEGOTIATION_SORT);
        return negotiationRepository.findByBuyerId(buyerId, safePageable).map(this::mapToDTO);
    }

    @Override
    public Page<NegotiationResponseDTO> getSellerNegotiations(UUID sellerId, Pageable pageable) {
        Pageable safePageable = PageableUtils.sanitize(pageable, ALLOWED_NEGOTIATION_SORT_PROPERTIES, DEFAULT_NEGOTIATION_SORT);
        return negotiationRepository.findByPostUserId(sellerId, safePageable).map(this::mapToDTO);
    }

    private NegotiationResponseDTO mapToDTO(Negotiation negotiation) {
        NegotiationResponseDTO dto = new NegotiationResponseDTO();
        dto.setId(negotiation.getId());
        dto.setPostId(negotiation.getPost().getId());
        dto.setPostTitle(negotiation.getPost().getTitle());
        dto.setBuyerId(negotiation.getBuyer().getId());
        dto.setSellerId(negotiation.getPost().getUser().getId());
        dto.setOfferedPrice(negotiation.getOfferedPrice());
        dto.setStatus(negotiation.getStatus());
        dto.setCreatedAt(negotiation.getCreatedAt());
        dto.setExpiredAt(negotiation.getExpiredAt());
        return dto;
    }
}
