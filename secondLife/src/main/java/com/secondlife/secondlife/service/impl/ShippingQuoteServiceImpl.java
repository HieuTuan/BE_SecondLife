package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.dto.response.PickupAddressUpdateStatusResponse;
import com.secondlife.secondlife.dto.response.ShippingQuoteResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.exception.ShippingRouteUnavailableException;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.mapper.ShippingVehicleSelectionMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.SellerOnboardingRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingPickupAddressRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service("shippingQuoteService")
@RequiredArgsConstructor
public class ShippingQuoteServiceImpl implements ShippingQuoteService {
    private static final List<String> RESERVED_POST_STATUSES = List.of("RESERVED", "PAYMENT_PENDING");

    private final PostRepository posts;
    private final NegotiationRepository negotiations;
    private final ShippingPickupAddressRepository addresses;
    private final ShippingQuoteRepository quotes;
    private final InspectionOrderRepository inspections;
    private final ShipmentRepository shipments;
    private final ShippingProvider provider;
    private final ObjectMapper mapper;
    private final ReverseGeocodingService geocoding;
    private final ShippingVehicleSelectionMapper vehicleSelectionMapper;
    private final UserRepository users;
    private final SellerOnboardingRepository onboarding;
    private final OrderRepository orders;
    private final UserReportRepository reports;

    @Override
    @Transactional
    public ShippingAddress savePickup(UUID userId, ShippingAddress address) {
        if ("LALAMOVE".equals(provider.code())) ShippingPayloads.requireCoordinates(address, "Seller pickup");
        users.findByIdForRoleUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));

        var existingRow = addresses.findById(userId).orElse(null);
        ShippingAddress existingAddress = existingRow == null ? null : readPickupAddress(existingRow.getAddressJson());
        if (existingAddress == null || !samePickupAddress(existingAddress, address)) {
            String blockReason = resolvePickupAddressBlockReason(userId);
            if (blockReason != null) {
                throw new ConflictException(blockReason);
            }
        }

        String addressJson = mapper.writeValueAsString(address);
        onboarding.findById(userId).ifPresent(profile -> {
            profile.setPickupAddressJson(addressJson);
            profile.setPickupProvinceId(null);
            profile.setPickupWardId(null);
        });

        var row = existingRow != null ? existingRow : new ShippingPickupAddress();
        row.setUserId(userId);
        row.setAddressJson(addressJson);
        addresses.save(row);
        return address;
    }

    @Override
    @Transactional(readOnly = true)
    public PickupAddressUpdateStatusResponse pickupUpdateStatus(UUID userId) {
        String reason = resolvePickupAddressBlockReason(userId);
        return new PickupAddressUpdateStatusResponse(reason == null, reason);
    }

    private String resolvePickupAddressBlockReason(UUID userId) {
        if (reports.hasActiveSellerOrderDispute(userId)) {
            return "Không thể thay đổi địa chỉ lấy hàng khi đang có đơn hàng khiếu nại hoặc tranh chấp chưa xử lý xong.";
        }
        if (orders.hasUnfinishedSellerOrder(userId)) {
            return "Không thể thay đổi địa chỉ lấy hàng khi đang có đơn hàng chưa hoàn thành hoặc chưa hoàn tiền xong.";
        }
        if (inspections.hasActiveOrUnreturnedSellerInspection(userId)) {
            return "Không thể thay đổi địa chỉ lấy hàng khi đang có đơn kiểm định tại nhà/trung tâm hoặc đơn hoàn hàng về người bán chưa hoàn tất.";
        }
        if (shipments.hasInFlightSellerShipment(userId)) {
            return "Không thể thay đổi địa chỉ lấy hàng khi đang có chuyến vận chuyển chưa giao hoàn tất.";
        }
        if (posts.countByUser_IdAndStatusIn(userId, RESERVED_POST_STATUSES) > 0) {
            return "Không thể thay đổi địa chỉ lấy hàng khi đang có sản phẩm được giữ chỗ hoặc chờ thanh toán.";
        }
        return null;
    }

    private ShippingAddress readPickupAddress(String addressJson) {
        if (addressJson == null || addressJson.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(addressJson, ShippingAddress.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean samePickupAddress(ShippingAddress a, ShippingAddress b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.name(), b.name())
                && Objects.equals(a.phone(), b.phone())
                && Objects.equals(a.address(), b.address())
                && sameCoordinate(a.latitude(), b.latitude())
                && sameCoordinate(a.longitude(), b.longitude());
    }

    private boolean sameCoordinate(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }
    @Transactional(readOnly=true) public ShippingAddress pickup(UUID userId) {
        return addresses.findById(userId).map(row -> mapper.readValue(row.getAddressJson(),ShippingAddress.class)).orElse(null);
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
        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new ConflictException("Post is not available for shipping quotation");
        }

        if (posts.isAwaitingHubReturn(post.getId())) {
            throw new ConflictException("Sản phẩm đang được hoàn từ Hub về người bán");
        }

        var passedInspection = inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(post.getId(), "PASSED");
        boolean isPassedInspectionBuyer = passedInspection.isPresent()
                && passedInspection.get().getBuyer() != null
                && buyerId.equals(passedInspection.get().getBuyer().getId())
                && passedInspection.get().getFinalPaymentDeadline() != null
                && passedInspection.get().getFinalPaymentDeadline().isAfter(Instant.now());

        if ("RESERVED".equals(post.getStatus()) && request.negotiationId() == null && !isPassedInspectionBuyer) {
            throw new ConflictException("Post is reserved. You must provide a valid negotiation for shipping quotation");
        }
        if (buyerId.equals(post.getUser().getId())) throw new BadRequestException("You cannot buy your own post");

        BigDecimal price = post.getPrice();
        if (isPassedInspectionBuyer && passedInspection.get().getFinalItemPrice() != null) {
            price = passedInspection.get().getFinalItemPrice();
        } else if (request.negotiationId() != null) {
            var n = negotiations.findById(request.negotiationId()).orElseThrow(() -> new NotFoundException("Negotiation not found"));
            if (!n.getBuyer().getId().equals(buyerId) || !n.getPost().getId().equals(post.getId()) || n.getStatus() != NegotiationStatus.ACCEPTED
                    || n.getExpiredAt() == null || n.getExpiredAt().isBefore(Instant.now())) throw new ConflictException("Accepted negotiation is not valid for this quote");
            price = n.getOfferedPrice();
        }

        ShippingAddress sellerPickup=pickup(post.getUser().getId());
        if (sellerPickup==null) throw new BadRequestException("Seller pickup address is not configured");
        ShippingAddress origin=sellerPickup;
        String leg="SELLER_TO_BUYER";

        var inspection = passedInspection.or(() -> inspections.findActiveByPostId(post.getId()));
        if (inspection.isPresent()) {
            var inspOrder = inspection.get();
            boolean isAtHome = inspOrder.getOrderType() == InspectionOrderType.AT_HOME;
            var allShipments = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspOrder.getId());
            boolean hasReturnedToSeller = allShipments.stream()
                    .anyMatch(s -> "CENTER_TO_SELLER".equals(s.getLeg()) && "DELIVERED".equals(s.getStatus()));

            if (!isAtHome && !hasReturnedToSeller) {
                var centerLegs = allShipments.stream()
                        .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus()))
                        .toList();
                if (!centerLegs.isEmpty()) {
                    var center = centerLegs.getLast();
                    boolean isDeliveredToCenter = "DELIVERED".equals(center.getStatus());
                    boolean isPassed = "PASSED".equals(inspOrder.getStatus());

                    boolean isTargetInspectionQuote = isPassedInspectionBuyer
                            || (inspOrder.getBuyer() == null && isPassed)
                            || (inspOrder.getBuyer() != null && buyerId.equals(inspOrder.getBuyer().getId()));

                    if (isTargetInspectionQuote) {
                        if (!isPassed || !isDeliveredToCenter) {
                            throw new ConflictException("The package must reach the inspection center and pass inspection before checkout");
                        }
                        origin = ShippingPayloads.storedAddress(mapper.readTree(center.getPayload()), "to");
                        leg = "CENTER_TO_BUYER";
                    }
                }
            }
        }
        ShippingAddress delivery=geocoding.shipping(request.deliveryAddress());
        ShippingParcel parcel = ShippingPayloads.parcel(post);
        var payload=ShippingPayloads.create(origin,delivery,parcel,post.getTitle(),price);
        String carrier=provider.code()==null?"LALAMOVE":provider.code();
        if ("LALAMOVE".equals(carrier)) {
            ShippingPayloads.requireCoordinates(sellerPickup,"Original seller pickup for return shipping");
            ShippingPayloads.requireCoordinates(origin,"Seller/inspection center pickup");
            ShippingPayloads.requireCoordinates(delivery,"Buyer delivery");
        }
        JsonNode result;
        try {
            result = provider.preview(payload);
        } catch (ShippingRouteUnavailableException error) {
            throw error.withCities(vehicleSelectionMapper.city(origin), vehicleSelectionMapper.city(delivery));
        }
        var total=result.get("total_fee");
        if (total==null || !total.isNumber() || total.decimalValue().signum()<0 || total.decimalValue().compareTo(BigDecimal.valueOf(1000000000))>0)
            throw new ShippingProviderException("Carrier returned an invalid shipping fee");
        if (result.hasNonNull("provider_quote")) payload.put("provider_quote",result.get("provider_quote"));
        if (result.hasNonNull("vehicle_selection")) payload.put("vehicle_selection", result.get("vehicle_selection"));
        if ("LALAMOVE".equals(carrier)) {
            String selected=result.path("provider_quote").path("serviceType").asText();
            if(!selected.matches("[A-Z][A-Z0-9_]{0,59}")) throw new ShippingProviderException("Lalamove returned no selected vehicle");
            payload.put("service_type",selected);
        }
        var row=new ShippingQuote(); row.setBuyerId(buyerId); row.setPostId(post.getId()); row.setPayload(mapper.writeValueAsString(payload));
        row.setProvider(carrier);
        row.setLeg(leg);
        row.setSellerPickupAddress(mapper.writeValueAsString(sellerPickup));
        row.setDeliveryAddress(mapper.writeValueAsString(delivery)); row.setFee(total.decimalValue());
        if ("LALAMOVE".equals(carrier)) {
            try { row.setExpiresAt(Instant.parse(result.path("expires_at").asText())); }
            catch (RuntimeException error) { throw new ShippingProviderException("Lalamove quotation has no valid expiry"); }
            if (!row.getExpiresAt().isAfter(Instant.now())) throw new ShippingProviderException("Lalamove quotation already expired");
        }
        var eta=result.get("expected_delivery_time");
        try { if (eta!=null && !eta.isNull()) row.setExpectedDeliveryTime(eta.isNumber()?Instant.ofEpochSecond(eta.longValue()):Instant.parse(eta.asText())); }
        catch (RuntimeException ex) { throw new ShippingProviderException("Carrier returned an invalid delivery estimate"); }
        quotes.save(row);
        return new ShippingQuoteResponse(row.getId(),row.getLeg(),row.getFee(),price,price.add(row.getFee()),
            BigDecimal.ZERO,row.getExpiresAt(),row.getExpectedDeliveryTime(),
            carrier,result.path("provider_quote").path("serviceType").asText(null),result.path("provider_quote").path("quotationId").asText(null),
            parcel.weight(),parcel.length(),parcel.width(),parcel.height(),
            vehicleSelectionMapper.toResponse(result, origin, delivery));
    }
    @Transactional public ShippingQuote consume(UUID buyerId,UUID postId,UUID quoteId,UUID orderId,BigDecimal price) {
        if (quoteId==null) throw new BadRequestException("shippingQuoteId is required; obtain a shipping quote before placing the order");
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
