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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NegotiationServiceImpl implements NegotiationService {

    private final NegotiationRepository negotiationRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private static final int MAX_REJECTION_LIMIT = 3;

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
        if (rejectedCount >= MAX_REJECTION_LIMIT) {
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
        negotiation.setExpiredAt(Instant.now().plus(24, ChronoUnit.HOURS));

        negotiation = negotiationRepository.save(negotiation);
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
        return mapToDTO(negotiation);
    }

    @Override
    public Page<NegotiationResponseDTO> getBuyerNegotiations(UUID buyerId, Pageable pageable) {
        return negotiationRepository.findByBuyerId(buyerId, pageable).map(this::mapToDTO);
    }

    @Override
    public Page<NegotiationResponseDTO> getSellerNegotiations(UUID sellerId, Pageable pageable) {
        return negotiationRepository.findByPostUserId(sellerId, pageable).map(this::mapToDTO);
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
