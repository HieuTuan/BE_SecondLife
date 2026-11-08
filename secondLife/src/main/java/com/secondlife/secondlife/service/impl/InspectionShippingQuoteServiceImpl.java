package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionShippingQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.InspectionShippingQuote;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.exception.ShippingRouteUnavailableException;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.InspectionShippingQuoteMapper;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.mapper.ShippingVehicleSelectionMapper;
import com.secondlife.secondlife.repository.InspectionShippingQuoteRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import com.secondlife.secondlife.service.ShippingQuoteService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionShippingQuoteServiceImpl implements InspectionShippingQuoteService {
    private final InspectionShippingQuoteRepository quotes;
    private final InspectionOrderRepository orders;
    private final PostRepository posts;
    private final InspectionCenterProfileService centers;
    private final ShippingQuoteService shipping;
    private final ShippingProvider provider;
    private final InspectionShippingQuoteMapper mapper;
    private final ShippingVehicleSelectionMapper vehicles;
    private final ObjectMapper json;

    private record Context(UUID postId, UUID centerId, ShippingAddress from, ShippingAddress to,
            Map<String, Object> outbound, Map<String, Object> reverse) { }

    @Override
    @Transactional
    public InspectionShippingQuoteResponse quote(UUID postId, UUID centerId, UUID quoteId) {
        Context context = context(postId, centerId);
        if (quoteId != null) {
            InspectionShippingQuote row = quotes.findById(quoteId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy báo giá vận chuyển kiểm định"));
            requireMatch(row, context);
            if (!row.getExpiresAt().isAfter(Instant.now())) {
                throw new ConflictException("Báo giá Lalamove đã hết hạn; vui lòng lấy báo giá kiểm định mới");
            }
            return response(row);
        }

        // Buyer request creation reuses this snapshot without another carrier request.
        JsonNode outbound = preview(context.outbound(), context.from(), context.to());
        InspectionShippingQuoteResponse.Leg outboundLeg = leg(outbound, "SELLER_TO_CENTER", context.from(), context.to());
        JsonNode reverse = preview(context.reverse(), context.to(), context.from());
        InspectionShippingQuoteResponse.Leg returnLeg = leg(reverse, "CENTER_TO_SELLER", context.to(), context.from());
        InspectionShippingQuote row = new InspectionShippingQuote();
        row.setPostId(postId);
        row.setInspectionCenterId(centerId);
        row.setOutboundPayload(json.writeValueAsString(context.outbound()));
        row.setReturnPayload(json.writeValueAsString(context.reverse()));
        row.setOutboundResponse(json.writeValueAsString(outboundLeg));
        row.setReturnResponse(json.writeValueAsString(returnLeg));
        row.setOutboundFee(outboundLeg.shippingFee());
        row.setReturnFee(returnLeg.shippingFee());
        row.setExpiresAt(outboundLeg.expiresAt().isBefore(returnLeg.expiresAt()) ? outboundLeg.expiresAt() : returnLeg.expiresAt());
        if (!row.getExpiresAt().isAfter(Instant.now())) throw new ConflictException("Báo giá Lalamove đã hết hạn; vui lòng thử lại");
        InspectionShippingQuote saved = quotes.saveAndFlush(row);
        return response(saved);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(InspectionOrder order, InspectionShippingQuoteResponse quote) {
        if (quote == null) {
            order.setInspectionShippingQuoteId(null);
            return;
        }
        InspectionShippingQuote row = quotes.findById(quote.id()).orElseThrow(() -> new NotFoundException("Inspection shipping quote not found"));
        requireMatch(row, context(order.getPost().getId(), quote.inspectionCenterId()));
        if (!row.getExpiresAt().isAfter(Instant.now())) throw new ConflictException("Báo giá vận chuyển đã hết hạn; lấy báo giá mới");
        orders.findByInspectionShippingQuoteId(row.getId()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), order.getId())) {
                throw new ConflictException("Báo giá đã được dùng cho đơn kiểm định khác; vui lòng lấy báo giá mới");
            }
        });
        if (row.getOutboundFee().compareTo(order.getShippingFee()) != 0 || row.getReturnFee().compareTo(order.getReturnShippingReserveFee()) != 0) {
            throw new ConflictException("Phí vận chuyển không khớp báo giá đã xác nhận");
        }
        order.setInspectionShippingQuoteId(row.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> outboundPayload(InspectionOrder order) {
        InspectionShippingQuote row = quotes.findById(order.getInspectionShippingQuoteId())
                .orElseThrow(() -> new ConflictException("Thiếu báo giá vận chuyển đã gắn với đơn kiểm định"));
        requireMatch(row, context(order.getPost().getId(), order.getInspectionCenter().getId()));
        return json.readValue(row.getOutboundPayload(), new TypeReference<Map<String, Object>>() { });
    }

    private Context context(UUID postId, UUID centerId) {
        var post = posts.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        ShippingAddress from = shipping.pickup(post.getUser().getId());
        ShippingAddress to = centers.requireShippingAddress(centerId);
        ShippingPayloads.requireCoordinates(from, "Seller pickup");
        var parcel = ShippingPayloads.parcel(post);
        return new Context(postId, centerId, from, to,
                ShippingPayloads.create(from, to, parcel, post.getTitle(), post.getPrice()),
                ShippingPayloads.create(to, from, parcel, post.getTitle(), post.getPrice()));
    }

    private void requireMatch(InspectionShippingQuote row, Context context) {
        if (!context.postId().equals(row.getPostId()) || !context.centerId().equals(row.getInspectionCenterId())
                || !json.readTree(row.getOutboundPayload()).equals(json.readTree(json.writeValueAsString(context.outbound())))
                || !json.readTree(row.getReturnPayload()).equals(json.readTree(json.writeValueAsString(context.reverse())))) {
            throw new ConflictException("Địa chỉ, trung tâm, giá bài hoặc kiện hàng đã thay đổi; cần lấy báo giá kiểm định mới");
        }
    }

    private JsonNode preview(Map<String, Object> payload, ShippingAddress from, ShippingAddress to) {
        if (!"LALAMOVE".equals(provider.code())) throw new ConflictException("Phí vận chuyển kiểm định cần tích hợp Lalamove");
        try {
            return provider.preview(payload);
        } catch (ShippingRouteUnavailableException error) {
            throw error.withCities(vehicles.city(from), vehicles.city(to));
        }
    }

    private InspectionShippingQuoteResponse.Leg leg(JsonNode result, String name, ShippingAddress from, ShippingAddress to) {
        JsonNode fee = result.path("total_fee");
        String service = result.path("provider_quote").path("serviceType").asText();
        String id = result.path("provider_quote").path("quotationId").asText();
        Instant expiry;
        try { expiry = Instant.parse(result.path("expires_at").asText()); }
        catch (RuntimeException error) { throw new ShippingProviderException("Lalamove quotation has no valid expiry"); }
        if (!fee.isNumber() || fee.decimalValue().signum() < 0 || fee.decimalValue().compareTo(new BigDecimal("1000000000")) > 0
                || fee.decimalValue().stripTrailingZeros().scale() > 0 || !service.matches("[A-Z][A-Z0-9_]{0,59}")
                || id.isBlank() || id.length() > 100 || !expiry.isAfter(Instant.now())) {
            throw new ShippingProviderException("Lalamove returned an invalid inspection shipping quotation");
        }
        return mapper.toLeg(name, fee.decimalValue(), service, id, expiry, vehicles.toResponse(result, from, to));
    }

    private InspectionShippingQuoteResponse response(InspectionShippingQuote row) {
        return mapper.toResponse(row,
                json.readValue(row.getOutboundResponse(), InspectionShippingQuoteResponse.Leg.class),
                json.readValue(row.getReturnResponse(), InspectionShippingQuoteResponse.Leg.class));
    }
}
