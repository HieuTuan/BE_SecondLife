package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.config.GhnProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class ShippingQuoteService {
    private final PostRepository posts;
    private final NegotiationRepository negotiations;
    private final ShippingPickupAddressRepository addresses;
    private final ShippingQuoteRepository quotes;
    private final InspectionOrderRepository inspections;
    private final ShipmentRepository shipments;
    private final ShippingProvider provider;
    private final GhnProperties properties;
    private final ObjectMapper mapper;

    @Transactional public ShippingAddress savePickup(UUID userId,ShippingAddress address) {
        var row = new ShippingPickupAddress(); row.setUserId(userId); row.setAddressJson(mapper.writeValueAsString(address));
        addresses.save(row); return address;
    }
    @Transactional(readOnly=true) public ShippingAddress pickup(UUID userId) {
        return mapper.readValue(addresses.findById(userId).orElseThrow(() -> new NotFoundException("Seller pickup address is not configured")).getAddressJson(),ShippingAddress.class);
    }
    @Transactional public ShippingParcel saveParcel(UUID sellerId,UUID postId,ShippingParcel parcel) {
        var post=posts.findByIdForUpdate(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        if (!sellerId.equals(post.getUser().getId())) throw new ForbiddenException("This post belongs to another seller");
        if (!Set.of("DRAFT","REJECTED","ACTIVE","PENDING_INSPECTION").contains(post.getStatus())) throw new ConflictException("Parcel cannot be changed in the current post status");
        post.setShippingWeight(parcel.weight()); post.setShippingLength(parcel.length()); post.setShippingWidth(parcel.width()); post.setShippingHeight(parcel.height());
        posts.save(post); return parcel;
    }
    @Transactional public ShippingQuoteResponse quote(UUID buyerId,ShippingQuoteRequest request) {
        Post post=posts.findById(request.postId()).orElseThrow(() -> new NotFoundException("Post not found"));
        if (!"ACTIVE".equals(post.getStatus())) throw new ConflictException("Post is not available for shipping quotation");
        if (buyerId.equals(post.getUser().getId())) throw new BadRequestException("You cannot buy your own post");
        BigDecimal price=post.getPrice();
        if (request.negotiationId()!=null) {
            var n=negotiations.findById(request.negotiationId()).orElseThrow(() -> new NotFoundException("Negotiation not found"));
            if (!n.getBuyer().getId().equals(buyerId) || !n.getPost().getId().equals(post.getId()) || n.getStatus()!=NegotiationStatus.ACCEPTED
                    || n.getExpiredAt()==null || n.getExpiredAt().isBefore(Instant.now())) throw new ConflictException("Accepted negotiation is not valid for this quote");
            price=n.getOfferedPrice();
        }
        ShippingAddress sellerPickup=pickup(post.getUser().getId());
        ShippingAddress origin=sellerPickup;
        String leg="SELLER_TO_BUYER";
        var inspection=inspections.findByPostId(post.getId());
        if (inspection.isPresent()) {
            var centerLegs=shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspection.get().getId()).stream()
                    .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus())).toList();
            if (!centerLegs.isEmpty()) {
                var center=centerLegs.getLast();
                if (!"PASSED".equals(inspection.get().getStatus()) || !"DELIVERED".equals(center.getStatus()))
                    throw new ConflictException("The package must reach the inspection center and pass inspection before checkout");
                origin=ShippingPayloads.storedAddress(mapper.readTree(center.getPayload()),"to"); leg="CENTER_TO_BUYER";
            }
        }
        var payload=ShippingPayloads.create(origin,request.deliveryAddress(),ShippingPayloads.parcel(post),post.getTitle(),price);
        var result=provider.preview(payload);
        var total=result.get("total_fee");
        if (total==null || !total.isNumber() || total.decimalValue().signum()<0 || total.decimalValue().compareTo(BigDecimal.valueOf(1000000000))>0)
            throw new ShippingProviderException("GHN returned an invalid shipping fee");
        var row=new ShippingQuote(); row.setBuyerId(buyerId); row.setPostId(post.getId()); row.setPayload(mapper.writeValueAsString(payload));
        row.setLeg(leg);
        row.setSellerPickupAddress(mapper.writeValueAsString(sellerPickup));
        row.setDeliveryAddress(mapper.writeValueAsString(request.deliveryAddress())); row.setFee(total.decimalValue());
        row.setExpiresAt(Instant.now().plusSeconds(properties.getQuoteTtlSeconds()));
        var eta=result.get("expected_delivery_time");
        try { if (eta!=null && !eta.isNull()) row.setExpectedDeliveryTime(eta.isNumber()?Instant.ofEpochSecond(eta.longValue()):Instant.parse(eta.asText())); }
        catch (RuntimeException ex) { throw new ShippingProviderException("GHN returned an invalid delivery estimate"); }
        quotes.save(row);
        return new ShippingQuoteResponse(row.getId(),row.getLeg(),row.getFee(),price,price.add(row.getFee()),
            BigDecimal.valueOf(((Number)payload.get("insurance_value")).longValue()),row.getExpiresAt(),row.getExpectedDeliveryTime());
    }
    @Transactional public ShippingQuote consume(UUID buyerId,UUID postId,UUID quoteId,UUID orderId,BigDecimal price) {
        if (quoteId==null) throw new BadRequestException("shippingQuoteId is required; obtain a GHN quote before placing the order");
        var quote=quotes.findByIdForUpdate(quoteId).orElseThrow(() -> new NotFoundException("Shipping quote not found"));
        if (!buyerId.equals(quote.getBuyerId()) || !postId.equals(quote.getPostId())) throw new ForbiddenException("Shipping quote belongs to another buyer or post");
        if (quote.getConsumedOrderId()!=null || !quote.getExpiresAt().isAfter(Instant.now())) throw new ConflictException("Shipping quote was consumed or expired; request a new quote");
        var stored=mapper.readTree(quote.getPayload());
        if (stored.get("order_value").decimalValue().compareTo(price)!=0) throw new ConflictException("Product price changed; request a new shipping quote");
        // Parcel changes invalidate the quote instead of creating a shipment with outdated dimensions.
        var post=posts.findById(postId).orElseThrow(); var parcel=ShippingPayloads.parcel(post);
        if (stored.get("weight").asInt()!=parcel.weight() || stored.get("length").asInt()!=parcel.length()
            || stored.get("width").asInt()!=parcel.width() || stored.get("height").asInt()!=parcel.height()) throw new ConflictException("Parcel changed; request a new shipping quote");
        quote.setConsumedOrderId(orderId); quotes.save(quote); return quote;
    }
}
