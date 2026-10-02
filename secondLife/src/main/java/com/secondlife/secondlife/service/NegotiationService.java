package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.NegotiationRequestDTO;
import com.secondlife.secondlife.dto.response.NegotiationResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NegotiationService {
    NegotiationResponseDTO createNegotiation(UUID buyerId, NegotiationRequestDTO requestDTO);
    NegotiationResponseDTO acceptNegotiation(UUID sellerId, UUID negotiationId);
    NegotiationResponseDTO rejectNegotiation(UUID sellerId, UUID negotiationId);
    NegotiationResponseDTO cancelNegotiation(UUID buyerId, UUID negotiationId);
    Page<NegotiationResponseDTO> getBuyerNegotiations(UUID buyerId, Pageable pageable);
    Page<NegotiationResponseDTO> getSellerNegotiations(UUID sellerId, Pageable pageable);
}
