package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ConfirmShipmentCashRequest;
import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ReportRequests;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.response.ShipmentResponse;
import com.secondlife.secondlife.dto.response.InspectionReturnQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.InspectionSealLog;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShipmentEvent;
import com.secondlife.secondlife.entity.UserReport;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.LalamoveRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.ShipmentMapper;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionSealLogRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.ShipmentEventRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.service.ListingFingerprint;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.OrderDisputeService;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ReturnShippingFeeService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.ShipmentService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.WalletService;

import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service("shipmentService")
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentMapper shipmentMapper;
    private final OrderDisputeService disputes;
    private final ReturnShippingFeeService returnShippingFees;

    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final OrderRepository orders;
    private final InspectionOrderRepository inspections;
    private final ShippingQuoteRepository quotes;
    private final ShippingQuoteService quoteService;
    private final ReverseGeocodingService geocoding;
    private final ShippingProvider provider;
    private final ObjectMapper mapper;
    private final WalletService wallets;
    private final PlatformTransactionManager transactions;
    private final SystemWalletService systemWallet;
    private final SellerReputationService reputation;
    private final ListingFingerprint listingFingerprint;
    private final NotificationService notificationService;
    private final InspectionSealLogRepository sealLogs;
    private final InspectionCenterProfileService centerProfiles;
    private final InspectionShippingQuoteService inspectionShipping;
    private final InspectionFeeSettlementService inspectionFeeSettlement;
    private final boolean cashShippingSimulationEnabled;

    public ShipmentServiceImpl(ShipmentRepository shipments, ShipmentEventRepository events, OrderRepository orders,
                           InspectionOrderRepository inspections, ShippingQuoteRepository quotes, ShippingQuoteService quoteService,
                           ReverseGeocodingService geocoding, ShippingProvider provider, ObjectMapper mapper,
                           WalletService wallets, PlatformTransactionManager transactions,
                           SystemWalletService systemWallet, SellerReputationService reputation, ListingFingerprint listingFingerprint, ShipmentMapper shipmentMapper, OrderDisputeService disputes, ReturnShippingFeeService returnShippingFees,
                           InspectionCenterProfileService centerProfiles, InspectionShippingQuoteService inspectionShipping,
                           InspectionFeeSettlementService inspectionFeeSettlement) {
        this(shipments, events, orders, inspections, quotes, quoteService, geocoding, provider, mapper, wallets, transactions, systemWallet, reputation, null, null, listingFingerprint, shipmentMapper, disputes, returnShippingFees, centerProfiles, inspectionShipping, inspectionFeeSettlement, false);
    }

    @Autowired
    public ShipmentServiceImpl(ShipmentRepository shipments, ShipmentEventRepository events, OrderRepository orders,
                           InspectionOrderRepository inspections, ShippingQuoteRepository quotes, ShippingQuoteService quoteService,
                           ReverseGeocodingService geocoding, ShippingProvider provider, ObjectMapper mapper,
                           WalletService wallets, PlatformTransactionManager transactions,
                           SystemWalletService systemWallet, SellerReputationService reputation,
                           @Autowired(required = false) NotificationService notificationService,
                           @Autowired(required = false) InspectionSealLogRepository sealLogs,
                           ListingFingerprint listingFingerprint, ShipmentMapper shipmentMapper, OrderDisputeService disputes, ReturnShippingFeeService returnShippingFees,
                           InspectionCenterProfileService centerProfiles, InspectionShippingQuoteService inspectionShipping,
                           InspectionFeeSettlementService inspectionFeeSettlement,
                           @Value("${app.inspection.cash-shipping-simulation-enabled:false}") boolean cashShippingSimulationEnabled) {
        this.shipmentMapper = shipmentMapper;
        this.disputes = Objects.requireNonNull(disputes, "OrderDisputeService is required");
        this.returnShippingFees = Objects.requireNonNull(returnShippingFees, "ReturnShippingFeeService is required");

        this.shipments = shipments;
        this.events = events;
        this.orders = orders;
        this.inspections = inspections;
        this.quotes = quotes;
        this.quoteService = quoteService;
        this.geocoding = geocoding;
        this.provider = provider;
        this.mapper = mapper;
        this.wallets = wallets;
        this.transactions = transactions;
        this.systemWallet = systemWallet;
        this.reputation = Objects.requireNonNull(reputation, "SellerReputationService is required");
        this.notificationService = notificationService;
        this.sealLogs = sealLogs;
        this.centerProfiles = centerProfiles;
        this.inspectionShipping = inspectionShipping;
        this.inspectionFeeSettlement = inspectionFeeSettlement;
        this.cashShippingSimulationEnabled = cashShippingSimulationEnabled;
        this.listingFingerprint = listingFingerprint;
    }

    private record Prepared(UUID id, boolean submit) {}

    private static final Set<String> LALAMOVE_STATUSES=Set.of("ASSIGNING_DRIVER","ON_GOING","PICKED_UP","COMPLETED","CANCELED","REJECTED","EXPIRED");

    public ShipmentResponse createForOrder(UUID actor,boolean staff,UUID orderId,CreateShipmentRequest request) {
        if ("SELLER_TO_CENTER".equals(request.leg())) {
            return createForInspection(actor, staff, orderId, request);
        }
        Prepared prepared=new TransactionTemplate(transactions).execute(tx -> prepareForOrder(actor,staff,orderId,request));
        return submit(prepared);
    }
    private Prepared prepareForOrder(UUID actor,boolean staff,UUID orderId,CreateShipmentRequest request) {
        return prepareForOrder(actor, staff, orderId, request, null);
    }

    private Prepared prepareForOrder(UUID actor, boolean staff, UUID orderId, CreateShipmentRequest request,
            InspectionOrder centerDelivery) {
        var order=orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!staff && centerDelivery == null && !actor.equals(order.getSeller().getId()))
            throw new ForbiddenException("Only the seller can create an outbound shipment");
        if (order.getShippingQuoteId()==null) throw new ConflictException("Order has no paid shipping quote");
        if (order.getStatus()==OrderStatus.CANCELLED || order.getStatus()==OrderStatus.COMPLETED) throw new ConflictException("Order is already closed");
        if (!Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER","BUYER_TO_SELLER").contains(request.leg())) throw new BadRequestException("Invalid order shipment leg");
        var quote=quotes.findById(order.getShippingQuoteId()).orElseThrow();
        Map<String,Object> payload=readMap(quote.getPayload());
        BigDecimal quotedFee=quote.getFee();
        UserReport returnDecision=null;
        UUID linkedInspectionId = null;

        if ("BUYER_TO_SELLER".equals(request.leg())) {
            if (!staff || request.reason()==null || request.reason().isBlank()) throw new ForbiddenException("A STAFF/ADMIN return decision with reason is required");
            if (order.getShippingDeliveredAt()==null) throw new ConflictException("Outbound delivery must be confirmed before creating a buyer return");
            if (order.getEscrowStatus()!=EscrowStatus.HELD && order.getEscrowStatus()!=EscrowStatus.FROZEN) throw new ConflictException("Funds are already settled");
            returnDecision=disputes.approvedReturn(order,request.reportId());
            if (order.getEscrowStatus()!=EscrowStatus.FROZEN) throw new ConflictException("An approved return requires frozen escrow");
            var from=mapper.readValue(order.getDeliveryAddress(),ShippingAddress.class);
            var original=mapper.readTree(quote.getPayload());
            var to=mapper.readValue(quote.getSellerPickupAddress(),ShippingAddress.class);
            payload=ShippingPayloads.create(from,to,ShippingPayloads.storedParcel(original),order.getPost().getTitle(),order.getFinalPrice());
            if (original.hasNonNull("service_type")) payload.put("service_type",original.path("service_type").asText());
            quotedFee=null;
            order.setEscrowStatus(EscrowStatus.FROZEN); orders.save(order);
        } else {
            if (!request.leg().equals(quote.getLeg())) throw new ConflictException("Shipment leg must match the paid shipping quote: "+quote.getLeg());
            if (order.getConfirmationDeadline()!=null && order.getSellerConfirmedAt()==null)
                throw new ConflictException("SELLER must confirm the order before creating an outbound shipment");
            if (order.getEscrowStatus()!=EscrowStatus.HELD || order.getShippingDeliveredAt()!=null) throw new ConflictException("Order is not eligible for outbound shipping");
            if ("CENTER_TO_BUYER".equals(request.leg())) {
                if (!staff && centerDelivery == null)
                    throw new ForbiddenException("Only the assigned inspection center or STAFF/ADMIN can create this shipment");
                var inspection = centerDelivery != null ? centerDelivery : inspections.findByOrderId(order.getId())
                        .or(() -> inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(order.getPost().getId(), "PASSED"))
                        .or(() -> inspections.findActiveByPostId(order.getPost().getId()))
                        .orElseThrow(() -> new ConflictException("Inspection is required for the center leg"));
                if (!"PASSED".equals(inspection.getStatus()) && !"DELIVERING_TO_BUYER".equals(inspection.getStatus()))
                    throw new ConflictException("Inspection has not passed");
                if (request.fromAddress()!=null && !matchesAddress(request.fromAddress(),ShippingPayloads.storedAddress(mapper.readTree(quote.getPayload()),"from")))
                    throw new ConflictException("Inspection center address must match the paid shipping quote");
                linkedInspectionId = inspection.getId();
            } else if ("SELLER_TO_BUYER".equals(request.leg())) {
                linkedInspectionId = inspections.findByOrderId(order.getId())
                        .or(() -> inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(order.getPost().getId(), "PASSED"))
                        .map(InspectionOrder::getId).orElse(null);
            }
        }
        var prepared=prepare(actor,orderId,linkedInspectionId,order.getSeller().getId(),order.getBuyer().getId(),request,payload,quotedFee,quote.getProvider(),null);
        if (returnDecision!=null) disputes.bindReturn(order,returnDecision,shipments.findById(prepared.id()).orElseThrow());
        return prepared;
    }

    @Override
    public ShipmentResponse createCenterDelivery(UUID centerId, UUID inspectionId, CreateShipmentRequest request) {
        boolean returning = "CENTER_TO_SELLER".equals(request.leg());
        if (!returning && !"CENTER_TO_BUYER".equals(request.leg()))
            throw new ForbiddenException("An inspection center can only create shipments to the buyer or back to the seller");
        if (request.fromAddress() != null || request.toAddress() != null)
            throw new BadRequestException("Use the saved shipment addresses");

        Prepared prepared = new TransactionTemplate(transactions).execute(tx -> {
            var inspection = (returning ? inspections.findByIdForUpdate(inspectionId) : inspections.findById(inspectionId))
                    .orElseThrow(() -> new NotFoundException("Inspection order not found"));
            requireAssignedCenter(centerId, inspection);
            if (returning) {
                UUID sellerId = inspection.getSeller() == null
                        ? inspection.getPost().getUser().getId() : inspection.getSeller().getId();
                return prepareInspectionReturn(centerId, inspection, sellerId, request, true);
            }
            if (inspection.getOrderType() != InspectionOrderType.AT_CENTER
                    || !"DELIVERING_TO_BUYER".equals(inspection.getStatus())
                    || inspection.getFinalPaymentPaidAt() == null || inspection.getOrder() == null)
                throw new ConflictException("Người mua phải hoàn tất thanh toán đơn kiểm định tại trung tâm trước khi tạo chuyến");
            return prepareForOrder(centerId, false, inspection.getOrder().getId(), request, inspection);
        });
        return submit(prepared);
    }

    public ShipmentResponse createForInspection(UUID actor,boolean staff,UUID inspectionId,CreateShipmentRequest request) {
        Prepared prepared=new TransactionTemplate(transactions).execute(tx -> prepareForInspection(actor,staff,inspectionId,request));
        return submit(prepared);
    }
    private Prepared prepareForInspection(UUID actor,boolean staff,UUID inspectionId,CreateShipmentRequest request) {
        UUID targetOrderId = null;
        UUID seller = inspections.findOwnerId(inspectionId).orElse(null);
        if (seller == null) {
            var byOrder = inspections.findByOrderId(inspectionId).orElse(null);
            if (byOrder != null) {
                targetOrderId = inspectionId;
                inspectionId = byOrder.getId();
                seller = byOrder.getPost().getUser().getId();
            } else {
                var ord = orders.findById(inspectionId).orElse(null);
                if (ord != null) {
                    targetOrderId = ord.getId();
                    var byPost = inspections.findFirstByPostIdOrderByCreatedAtDesc(ord.getPost().getId()).orElse(null);
                    if (byPost != null) {
                        inspectionId = byPost.getId();
                        seller = byPost.getPost().getUser().getId();
                    }
                }
            }
        }
        if (seller == null) throw new NotFoundException("Inspection order not found");
        if (!staff && !actor.equals(seller)) throw new ForbiddenException("This inspection belongs to another seller");
        if (!"SELLER_TO_CENTER".equals(request.leg()) && !"CENTER_TO_SELLER".equals(request.leg()) && !"CENTER_TO_BUYER".equals(request.leg())) {
            throw new BadRequestException("Only SELLER_TO_CENTER, CENTER_TO_SELLER or CENTER_TO_BUYER legs are supported for inspection shipments");
        }
        if (!staff && !"SELLER_TO_CENTER".equals(request.leg())) {
            throw new ForbiddenException("STAFF/ADMIN initiates shipments from inspection center");
        }
        if ("CENTER_TO_BUYER".equals(request.leg())) {
            var inspection = inspections.findById(inspectionId).orElseThrow();
            if (inspection.getInspectionShippingQuoteId() != null) {
                if (inspection.getOrder() == null || inspection.getFinalPaymentPaidAt() == null) {
                    throw new ConflictException("Người mua phải thanh toán tiền hàng và cước giao từ trung tâm trước khi tạo chuyến");
                }
                return prepareForOrder(actor, staff, inspection.getOrder().getId(), request);
            }
        }
        var inspection=inspections.findByIdForUpdate(inspectionId).orElseThrow();
        var post=inspection.getPost();
        var saved=shipments.findById(shipmentId(inspectionId,request));
        Map<String,Object> payload;
        BigDecimal fee = null;

        if ("CENTER_TO_SELLER".equals(request.leg())) {
            if (inspection.getInspectionCenter() != null) {
                throw new ForbiddenException("Trung tâm phụ trách đơn kiểm định phải tạo chuyến trả hàng");
            }
            return prepareInspectionReturn(actor, inspection, seller, request, false);
        } else if ("CENTER_TO_BUYER".equals(request.leg())) {
            if (inspection.getOrderType() != InspectionOrderType.AT_CENTER) {
                throw new ConflictException("Only AT_CENTER inspections support center delivery shipments");
            }
            if (!Set.of("PASSED", "DELIVERING_TO_BUYER").contains(inspection.getStatus())) {
                throw new ConflictException("Only PASSED inspections are eligible for buyer delivery");
            }
            var centerLegs = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId).stream()
                    .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && "DELIVERED".equals(s.getStatus())).toList();
            if (saved.isPresent()) {
                payload = readMap(saved.get().getPayload());
            } else {
                ShippingAddress from = legacyCenterPickup(inspection, request, centerLegs);
                ShippingAddress to = legacyBuyerDelivery(inspection, request);
                payload = ShippingPayloads.create(from, to, ShippingPayloads.parcel(post), post.getTitle(), post.getPrice());
            }
            fee = inspection.getShippingFee();
        } else {
            if (inspection.getOrderType() != InspectionOrderType.AT_CENTER) {
                throw new ConflictException("Only AT_CENTER inspections support shipments to an inspection center");
            }
            if (Set.of("CANCELLED", "FAILED", "PAYMENT_EXPIRED", "COMPLETED").contains(inspection.getStatus())) {
                throw new ConflictException("Inspection order is not eligible for center shipment");
            }
            if (saved.isEmpty() && inspection.getInspectionShippingQuoteId() != null
                    && !Set.of("CONFIRMED_PENDING_DISPATCH", "DISPATCHED", "SCHEDULED").contains(inspection.getStatus())) {
                throw new ConflictException("Đơn kiểm định phải được xác nhận và thanh toán đủ trước khi gửi hàng đến trung tâm");
            }
            if (saved.isPresent()) {
                var original=saved.get();
                var originalPayload = mapper.readTree(original.getPayload());
                if (!Objects.equals(original.getReason(), request.reason())
                        || (request.toAddress() != null && !matchesAddress(request.toAddress(), ShippingPayloads.storedAddress(originalPayload, "to")))
                        || (request.fromAddress() != null && !matchesAddress(request.fromAddress(), ShippingPayloads.storedAddress(originalPayload, "from"))))
                    throw new ConflictException("requestId was already used with different shipment details");
                payload=readMap(original.getPayload());
            } else {
                ShippingAddress destination;
                if (inspection.getInspectionCenter() != null) {
                    destination = centerProfiles.requireShippingAddress(inspection.getInspectionCenter().getId());
                    if (request.toAddress() != null && !matchesAddress(request.toAddress(), destination)) {
                        throw new ConflictException("Điểm đến phải khớp hồ sơ trung tâm của đơn kiểm định; tải lại thông tin trung tâm hoặc bỏ toAddress");
                    }
                } else {
                    // Legacy orders have no assigned center; use the explicit destination.
                    if (request.toAddress() == null) {
                        throw new BadRequestException("Đơn kiểm định cũ chưa gắn trung tâm; cần cung cấp toAddress đã xác minh");
                    }
                    destination = geocoding.shipping(request.toAddress());
                }
                if (inspection.getInspectionShippingQuoteId() != null) {
                    payload = inspectionShipping.outboundPayload(inspection);
                    if (request.fromAddress() != null && !matchesAddress(request.fromAddress(),
                            ShippingPayloads.storedAddress(mapper.valueToTree(payload), "from"))) {
                        throw new ConflictException("Điểm lấy hàng phải khớp báo giá vận chuyển đã chốt");
                    }
                    fee = inspection.getShippingFee();
                } else {
                    ShippingAddress origin = request.fromAddress() != null
                            ? geocoding.shipping(request.fromAddress()) : quoteService.pickup(seller);
                    payload = ShippingPayloads.create(origin, destination,
                            ShippingPayloads.parcel(post), post.getTitle(), post.getPrice());
                }
            }
        }

        if (targetOrderId == null && inspection.getOrder() != null) {
            targetOrderId = inspection.getOrder().getId();
        }

        UUID buyer = inspection.getBuyer() == null ? null : inspection.getBuyer().getId();
        UUID cashPayer = inspection.isCashShippingSimulation() && "SELLER_TO_CENTER".equals(request.leg())
                ? seller : null;
        return prepare(actor, targetOrderId, inspectionId, seller, buyer, request, payload, fee,
                saved.map(Shipment::getProvider).orElse(activeCarrier()), cashPayer);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionReturnQuoteResponse quoteCenterReturn(UUID centerId, UUID inspectionId) {
        var inspection = inspections.findById(inspectionId)
                .orElseThrow(() -> new NotFoundException("Inspection order not found"));
        requireAssignedCenter(centerId, inspection);
        var existing = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId);
        var inbound = requireReturnInbound(inspection, true, existing);
        if (existing.stream().anyMatch(s -> "CENTER_TO_SELLER".equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus()))) {
            throw new ConflictException("Đơn đã có vận đơn trả hàng hoặc đang xác minh chuyến trả.");
        }

        var payload = centerReturnPayload(inspection, inbound.getLast());
        var route = mapper.valueToTree(payload);
        String carrierCode = activeCarrier();
        var preview = carrier(carrierCode).preview(payload);
        return new InspectionReturnQuoteResponse(
                ShippingPayloads.storedAddress(route, "from"), ShippingPayloads.storedAddress(route, "to"),
                returnQuoteFee(preview), inspection.getReturnShippingReserveFee(), carrierCode,
                preview.path("provider_quote").path("serviceType").asText(null),
                preview.path("expires_at").asText(null));
    }

    private void requireAssignedCenter(UUID centerId, InspectionOrder inspection) {
        if (inspection.getInspectionCenter() == null || !centerId.equals(inspection.getInspectionCenter().getId())) {
            throw new ForbiddenException("This inspection belongs to another center");
        }
    }

    private List<Shipment> requireReturnInbound(InspectionOrder inspection, boolean centerActor, List<Shipment> existing) {
        if (inspection.getOrderType() != InspectionOrderType.AT_CENTER) {
            throw new ConflictException("Only AT_CENTER inspections support return shipments");
        }
        if (inspection.getFinalPaymentPaidAt() != null
                || !Set.of("FAILED", "CANCELLED", "PASSED").contains(inspection.getStatus())) {
            throw new ConflictException("Inspection is not eligible for return shipping");
        }

        var deliveredInbound = existing.stream()
                .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && "DELIVERED".equals(s.getStatus())).toList();
        if ((centerActor || inspection.getInspectionShippingQuoteId() != null)
                && (inspection.getFeeSettledAt() == null || deliveredInbound.isEmpty()
                    || "PASSED".equals(inspection.getStatus()) || inspection.getReturnShippingReserveFee() == null)) {
            throw new ConflictException("Chỉ đặt chuyến hoàn khi hàng đã đến trung tâm và đơn kiểm định đã chốt trả hàng");
        }

        return deliveredInbound;
    }

    private Map<String, Object> centerReturnPayload(InspectionOrder inspection, Shipment inbound) {
        var inboundPayload = mapper.readTree(inbound.getPayload());
        var from = ShippingPayloads.storedAddress(inboundPayload, "to");
        var to = ShippingPayloads.storedAddress(inboundPayload, "from");
        var post = inspection.getPost();
        return ShippingPayloads.create(from, to, ShippingPayloads.parcel(post), post.getTitle(), post.getPrice());
    }

    private Prepared prepareInspectionReturn(UUID actor, InspectionOrder inspection, UUID seller,
                                             CreateShipmentRequest request, boolean centerActor) {
        UUID inspectionId = inspection.getId();
        var deliveredInbound = requireReturnInbound(inspection, centerActor,
                shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId));
        var saved = shipments.findById(shipmentId(inspectionId, request));
        Map<String, Object> payload;
        if (saved.isPresent()) {
            payload = readMap(saved.get().getPayload());
        } else if (!deliveredInbound.isEmpty()) {
            payload = centerReturnPayload(inspection, deliveredInbound.getLast());
        } else {
            ShippingAddress to = request.toAddress() == null
                    ? quoteService.pickup(seller) : geocoding.shipping(request.toAddress());
            ShippingAddress from = legacyCenterPickup(inspection, request, deliveredInbound);
            var post = inspection.getPost();
            payload = ShippingPayloads.create(from, to, ShippingPayloads.parcel(post), post.getTitle(), post.getPrice());
        }
        UUID buyer = inspection.getBuyer() == null ? null : inspection.getBuyer().getId();
        UUID cashPayer = inspection.isCashShippingSimulation() && centerActor ? actor : null;
        return prepare(actor, null, inspectionId, seller, buyer, request, payload,
                inspection.getReturnShippingReserveFee(), saved.map(Shipment::getProvider).orElse(activeCarrier()), cashPayer);
    }

    private ShippingAddress legacyCenterPickup(InspectionOrder inspection, CreateShipmentRequest request,
                                               List<Shipment> deliveredInbound) {
        if (!deliveredInbound.isEmpty()) {
            return ShippingPayloads.storedAddress(mapper.readTree(deliveredInbound.getLast().getPayload()), "to");
        }
        if (inspection.getInspectionCenter() != null) {
            return centerProfiles.requireShippingAddress(inspection.getInspectionCenter().getId());
        }
        if (request.fromAddress() != null) return geocoding.shipping(request.fromAddress());
        throw new BadRequestException("Đơn kiểm định cũ chưa có điểm lấy hàng tại trung tâm; cần cung cấp fromAddress");
    }

    private ShippingAddress legacyBuyerDelivery(InspectionOrder inspection, CreateShipmentRequest request) {
        if (request.toAddress() != null) return geocoding.shipping(request.toAddress());
        if (inspection.getOrder() != null && inspection.getOrder().getDeliveryAddress() != null) {
            return mapper.readValue(inspection.getOrder().getDeliveryAddress(), ShippingAddress.class);
        }
        if (inspection.getBuyerDeliveryAddressJson() != null) {
            return geocoding.shipping(mapper.readValue(inspection.getBuyerDeliveryAddressJson(), ShippingAddressRequest.class));
        }
        throw new BadRequestException("Chưa có địa chỉ nhận hàng của người mua; cần cung cấp toAddress");
    }

    private BigDecimal returnQuoteFee(JsonNode preview) {
        var total = preview.get("total_fee");
        if (total == null || !total.isNumber() || total.decimalValue().signum() < 0
                || total.decimalValue().compareTo(BigDecimal.valueOf(1000000000)) > 0) {
            throw new ShippingProviderException("Carrier returned an invalid shipping fee");
        }
        return total.decimalValue();
    }

    private Prepared prepare(UUID actor, UUID orderId, UUID inspectionId, UUID seller, UUID buyer,
                             CreateShipmentRequest request, Map<String, Object> payload, BigDecimal fee,
                             String carrierCode, UUID cashPayer) {
        UUID parent=orderId!=null?orderId:inspectionId;
        UUID id=shipmentId(parent,request);
        payload.put("client_order_code","SL"+id);
        String serialized=mapper.writeValueAsString(payload);
        var existing=shipments.findById(id);
        Shipment shipment;
        if (existing.isPresent()) {
            shipment=existing.get();
            if (!sameRoute(payload,readMap(shipment.getPayload())) || !Objects.equals(request.reason(),shipment.getReason())) throw new ConflictException("requestId was already used with different shipment details");
            if (shipment.getOrderCode()!=null || "LALAMOVE".equals(shipment.getProvider())) return new Prepared(id,false);
        } else {
            var siblings=orderId!=null?shipments.findByOrderIdOrderByCreatedAtAsc(orderId):shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId);
            if (orderId != null && inspectionId != null && "CENTER_TO_BUYER".equals(request.leg())
                    && shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId).stream()
                        .anyMatch(s -> s.getOrderId() == null && request.leg().equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus()))) {
                throw new ConflictException("A shipment is already active or uncertain for this inspection");
            }
            boolean outbound=!"BUYER_TO_SELLER".equals(request.leg());
            if (siblings.stream().anyMatch(s -> !"CANCELLED".equals(s.getStatus())
                    && !("RETURNED".equals(s.getStatus()) && Set.of("BUYER_TO_SELLER","SELLER_TO_CENTER").contains(s.getLeg()))
                    && (s.getLeg().equals(request.leg())
                    || (outbound && Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(s.getLeg())))))
                throw new ConflictException("A shipment is already active or uncertain; retry its original requestId");
            // A paid quotation can still be unexpired after its pickup schedule has passed.
            // Refresh only new booking attempts; existing requests return above without another provider mutation.
            boolean checkConfirmedReturnFee = "CENTER_TO_SELLER".equals(request.leg()) && request.maxReturnFee() != null;
            if (fee==null || "LALAMOVE".equals(carrierCode) || checkConfirmedReturnFee) {
                var preview=carrier(carrierCode).preview(payload);
                var total=returnQuoteFee(preview);
                if (checkConfirmedReturnFee && total.compareTo(request.maxReturnFee()) > 0) {
                    throw new ConflictException("Cước chiều về đã tăng. Vui lòng lấy lại báo giá trước khi đặt chuyến.");
                }
                if (fee != null && total.compareTo(fee) > 0) {
                    if ("CENTER_TO_SELLER".equals(request.leg())) {
                        throw new ConflictException("Cước Lalamove chiều về vượt khoản dự phòng đã thu; cần xử lý chênh lệch trước khi đặt chuyến");
                    }
                    throw new ConflictException(inspectionId != null
                            ? "Phí Lalamove hiện tại vượt ngân sách vận chuyển của đơn kiểm định; cần xử lý hủy/hoàn phí đơn chưa gửi và xác nhận lại báo giá mới trước khi đặt chuyến"
                            : "The current Lalamove fee exceeds the paid shipping fee; cancel the unshipped order and checkout with a new quote");
                }
                if (fee==null) fee=total;
                if ("LALAMOVE".equals(carrierCode)) {
                    if (!preview.hasNonNull("provider_quote")) throw new ShippingProviderException("Lalamove returned no quotation context");
                    payload.put("provider_quote",preview.get("provider_quote"));
                    serialized=mapper.writeValueAsString(payload);
                }
            }
            shipment=new Shipment(); shipment.setId(id); shipment.setOrderId(orderId); shipment.setInspectionOrderId(inspectionId);
            shipment.setSellerId(seller); shipment.setBuyerId(buyer); shipment.setRequestId(request.requestId()); shipment.setLeg(request.leg());
            shipment.setClientOrderCode("SL"+id); shipment.setPayload(serialized); shipment.setReason(request.reason()); shipment.setRequestedBy(actor);
            shipment.setStatus("CREATING"); shipment.setQuotedFee(fee);
            shipment.setProvider(carrierCode);
            shipment.setCashPayerId(cashPayer);
            var route = mapper.valueToTree(payload);
            shipmentMapper.snapshotRoute(shipment, ShippingPayloads.storedAddress(route, "from"),
                    ShippingPayloads.storedAddress(route, "to"));
            shipment=shipments.saveAndFlush(shipment);
        }
        return new Prepared(id,true);
    }
    private ShipmentResponse submit(Prepared prepared) {
        return new TransactionTemplate(transactions).execute(tx -> {
        var shipment=lock(prepared.id());
        if (!prepared.submit() || shipment.getOrderCode()!=null) return shipmentMapper.toResponse(shipment);
        Map<String,Object> payload=readMap(shipment.getPayload());
        if (shipment.getCashPayerId() != null) {
            payload.put("inspection_cash_simulation", true);
            String payerNote = "SELLER_TO_CENTER".equals(shipment.getLeg())
                    ? "Người bán là bên trả cước tiền mặt. Vui lòng xác nhận với người bán trước khi giao."
                    : "Trung tâm kiểm định là bên trả cước tiền mặt. Vui lòng xác nhận với trung tâm trước khi giao.";
            payload.put("content", payerNote + "\n" + Objects.toString(payload.get("content"), ""));
        }
        try {
            var result=carrier(shipment.getProvider()).create(payload);
            var code=result.get("order_code");
            if (code==null || !code.isString() || !code.asText().matches("[A-Za-z0-9_-]{1,50}")) throw new ShippingProviderException("Carrier returned no valid tracking code");
            shipment.setOrderCode(code.asText());
            String status="LALAMOVE".equals(shipment.getProvider())?result.path("status").asText():"ready_to_pick";
            if ("LALAMOVE".equals(shipment.getProvider()) && !LALAMOVE_STATUSES.contains(status)) throw new ShippingProviderException("Lalamove returned an unknown status");
            shipment.setProviderStatus(status); shipment.setStatus("LALAMOVE".equals(shipment.getProvider())?canonicalLalamove(status):"READY_TO_PICK"); shipment.setLastError(null);
            applyFeeAndEta(shipment,result);
            applyCarrierLinks(shipment,result);
            if ("LALAMOVE".equals(shipment.getProvider())) {
                Instant delivered=parseProviderTime(result.get("delivered_at"));
                applyLalamoveStatus(shipment,status,delivered!=null?delivered:Instant.now(), null);
                // The HTTP response has no carrier update timestamp. Do not use its receipt time to discard delayed callbacks.
                if (Set.of("ASSIGNING_DRIVER","ON_GOING").contains(status)) shipment.setLastEventAt(null);
            }
        } catch (ShippingProviderException ex) {
            boolean rejected="LALAMOVE".equals(shipment.getProvider()) && shipment.getOrderCode()==null
                    && Set.of(400,401,402,403,404,422,429).contains(ex.getUpstreamStatus());
            shipment.setStatus(rejected?"CANCELLED":"CREATION_UNCERTAIN");
            String detail=ex instanceof LalamoveRequestException?ex.getMessage():"Lalamove rejected the booking (HTTP "+ex.getUpstreamStatus()+")";
            shipment.setLastError(rejected?detail+"; correct configuration/request before trying a fresh requestId":
                "Creation uncertain; reconcile the existing Lalamove order, do not create another booking");
        } catch (ConflictException | BadRequestException ex) {
            // These exceptions reject local validation before the adapter sends a creation request.
            String detail=switch(ex.getMessage()==null?"":ex.getMessage()) {
                case "Seller pickup phone number must be a valid Vietnamese number",
                     "Buyer delivery phone number must be a valid Vietnamese number",
                     "Seller pickup phone number is required and must fit the carrier limit",
                     "Buyer delivery phone number is required and must fit the carrier limit",
                     "Seller pickup phone number requires an international country code",
                     "Buyer delivery phone number requires an international country code",
                     "Lalamove quotation expired; refresh the shipping quotation before creating the shipment",
                     "Obtain a current Lalamove quotation before creating the shipment" -> ex.getMessage();
                default -> "Booking validation failed before submission";
            };
            shipment.setStatus("CANCELLED"); shipment.setLastError(detail+"; correct the request and use a fresh requestId");
        }
        shipment.setUpdatedAt(Instant.now()); shipments.save(shipment); return shipmentMapper.toResponse(shipment);
        });
    }

    @Transactional(readOnly=true) public List<ShipmentResponse> orderShipments(UUID actor,boolean staff,UUID id) {
        var order=orders.findById(id).orElse(null);
        if (order == null) {
            InspectionOrder inspection = findInspectionForShipmentRead(id);
            return readInspectionShipments(actor, staff, inspection, isAssignedCenter(actor, inspection));
        }
        requireOrderActor(actor,staff,order);
        var list = shipments.findByOrderIdOrderByCreatedAtAsc(id);
        if (list.isEmpty()) {
            var insp = inspections.findByOrderId(id).orElse(null);
            if (insp != null) {
                var inspList = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(insp.getId());
                if (!inspList.isEmpty()) {
                    return inspList.stream().map(shipmentMapper::toResponse).toList();
                }
            }
        }
        return list.stream().map(shipmentMapper::toResponse).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<ShipmentResponse> inspectionShipments(UUID actor, boolean staff, boolean center, UUID id) {
        InspectionOrder inspection = findInspectionForShipmentRead(id);
        return readInspectionShipments(actor, staff, inspection, center && isVisibleToCenter(actor, inspection));
    }

    private InspectionOrder findInspectionForShipmentRead(UUID id) {
        return inspections.findById(id)
                .or(() -> inspections.findByOrderId(id))
                .or(() -> orders.findById(id)
                        .flatMap(order -> inspections.findFirstByPostIdOrderByCreatedAtDesc(order.getPost().getId())))
                .orElse(null);
    }

    private boolean isAssignedCenter(UUID actor, InspectionOrder inspection) {
        return inspection != null && inspection.getInspectionCenter() != null
                && actor.equals(inspection.getInspectionCenter().getId());
    }

    private boolean isVisibleToCenter(UUID actor, InspectionOrder inspection) {
        if (inspection == null) return false;
        if (inspection.getInspectionCenter() != null) return isAssignedCenter(actor, inspection);

        var inspector = inspection.getInspector();
        return inspector != null && inspector.getManager() != null
                && actor.equals(inspector.getManager().getId());
    }

    private List<ShipmentResponse> readInspectionShipments(
            UUID actor, boolean staff, InspectionOrder inspection, boolean centerAccess) {
        if (inspection == null) return List.of();
        if (!staff && !centerAccess) {
            UUID sellerId = inspection.getSeller() != null ? inspection.getSeller().getId()
                    : (inspection.getPost() != null && inspection.getPost().getUser() != null
                            ? inspection.getPost().getUser().getId() : null);
            UUID buyerId = inspection.getBuyer() != null ? inspection.getBuyer().getId()
                    : (inspection.getOrder() != null && inspection.getOrder().getBuyer() != null
                            ? inspection.getOrder().getBuyer().getId() : null);
            if (!actor.equals(sellerId) && !actor.equals(buyerId)) {
                throw new ForbiddenException("Inspection belongs to another user");
            }
        }
        UUID orderId = inspection.getOrder() == null ? null : inspection.getOrder().getId();
        return shipments.findInspectionRoute(inspection.getId(), orderId).stream()
                .map(shipmentMapper::toResponse).toList();
    }
    @Transactional(readOnly=true) public List<ShipmentEvent> timeline(UUID actor,boolean staff,UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found")); requireRead(actor,staff,shipment);
        return events.findByShipmentIdOrderByOccurredAtAsc(id);
    }
    @Transactional public ShipmentResponse cancel(UUID actor,boolean staff,UUID id) {
        var shipment=lock(id); if (!staff && !actor.equals(shipment.getSellerId())) throw new ForbiddenException("Only the seller can cancel this shipment");
        if (!staff && "BUYER_TO_SELLER".equals(shipment.getLeg())) throw new ForbiddenException("STAFF/ADMIN must cancel an approved return shipment");
        if ("CANCELLED".equals(shipment.getStatus())) return shipmentMapper.toResponse(shipment);
        boolean allowed="LALAMOVE".equals(shipment.getProvider()) && Set.of("ASSIGNING_DRIVER","ON_GOING").contains(shipment.getProviderStatus());
        if (!allowed || shipment.getOrderCode()==null) throw new ConflictException("Carrier pickup has started or creation is uncertain; cancellation requires reconciliation");
        carrier(shipment.getProvider()).cancel(shipment.getOrderCode()); shipment.setStatus("CANCELLED"); shipment.setProviderStatus("CANCELED"); shipment.setUpdatedAt(Instant.now());
        shipments.save(shipment); return shipmentMapper.toResponse(shipment);
    }
    @Transactional public ShipmentResponse refundReturned(UUID actor,boolean staff,UUID id,String reason) {
        return refundReturned(actor,staff,id,reason,null);
    }
    @Transactional public ShipmentResponse refundReturned(UUID actor,boolean staff,UUID id,String reason,
            ReportRequests.ResponsibleParty responsibleParty) {
        if (!staff) throw new ForbiddenException("STAFF/ADMIN must approve the refund");
        var shipment=lock(id);
        if (shipment.getOrderId()==null) throw new ConflictException("Inspection shipping cannot refund a product order");
        boolean buyerReturn="BUYER_TO_SELLER".equals(shipment.getLeg());
        if (!(buyerReturn && "DELIVERED".equals(shipment.getStatus()))
                && !(!buyerReturn && "RETURNED".equals(shipment.getStatus())))
            throw new ConflictException("Product must be physically returned before refund");
        var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        UserReport returnDecision=buyerReturn?disputes.refundDecision(order,shipment,responsibleParty):null;
        if (returnDecision!=null) responsibleParty=returnDecision.getResponsibleParty();
        String party=responsibleParty==null?"NONE":responsibleParty.name();
        if(order.getEscrowStatus()==EscrowStatus.REFUNDED) {
            if(order.getReturnResponsibleParty()!=null && !order.getReturnResponsibleParty().equals(party))
                throw new ConflictException("Refund responsibility was already finalized; use the report review flow for a later finding");
            if (returnDecision!=null) returnShippingFees.collectPendingForRefund(order,shipment,returnDecision,actor);
            return shipmentMapper.toResponse(shipment);
        }
        if (order.getEscrowStatus()!=EscrowStatus.FROZEN) throw new ConflictException("Return refund requires frozen funds");
        systemWallet.refund(order, order.getFinalPrice());
        wallets.processRefund(order.getBuyer().getId(),order.getFinalPrice(),order.getId());
        order.setStatus(OrderStatus.CANCELLED); order.setEscrowStatus(EscrowStatus.REFUNDED); order.setReturnResponsibleParty(party); orders.saveAndFlush(order);
        if (returnDecision!=null) returnShippingFees.recordForRefund(order,shipment,returnDecision,actor);
        reputation.recordClosedOrder(order,actor,reason,returnDecision==null && "SELLER".equals(party));
        if (returnDecision!=null) disputes.recordRefund(returnDecision,actor,order.getFinalPrice());
        shipment.setReason(reason); shipment.setRequestedBy(actor); shipments.save(shipment);
        // Do not re-publish returned/disputed goods automatically; the seller must resolve their condition.
        return shipmentMapper.toResponse(shipment);
    }

    @Override
    @Transactional
    public ShipmentResponse confirmCashPayment(UUID actor, boolean reviewer, UUID shipmentId, ConfirmShipmentCashRequest request) {
        if (!reviewer) throw new ForbiddenException("Cần quyền duyệt quyết toán cước vận chuyển");
        if (!cashShippingSimulationEnabled) {
            throw new ConflictException("Môi trường chưa bật hoàn ứng cước mô phỏng");
        }
        Shipment shipment = lock(shipmentId);
        if (shipment.getCashPayerId() == null || shipment.getInspectionOrderId() == null
                || shipment.getOrderId() != null || !"LALAMOVE".equals(shipment.getProvider())) {
            throw new ConflictException("Vận đơn không thuộc luồng hoàn ứng cước mô phỏng");
        }

        String receiptReference = request.receiptReference().strip();
        BigDecimal paidAmount = request.paidAmount();
        if (shipment.getCashConfirmedAt() != null) {
            if (shipment.getCashPaidAmount().compareTo(paidAmount) == 0
                    && shipment.getCashReceiptReference().equals(receiptReference)) {
                return shipmentMapper.toResponse(shipment);
            }
            throw new ConflictException("Vận đơn đã được quyết toán với thông tin khác");
        }

        if (!"DELIVERED".equals(shipment.getStatus()) || !"COMPLETED".equals(shipment.getProviderStatus())
                || shipment.getOrderCode() == null) {
            throw new ConflictException("Chỉ quyết toán khi Lalamove xác nhận giao hàng thành công");
        }
        if (shipment.getActualFee() == null || shipment.getActualFee().compareTo(paidAmount) != 0) {
            throw new ConflictException("Số tiền biên nhận chưa khớp cước Lalamove. Hãy đồng bộ vận đơn trước khi xác nhận");
        }
        if (shipments.existsByInspectionOrderIdAndLegAndCashConfirmedAtIsNotNull(
                shipment.getInspectionOrderId(), shipment.getLeg())) {
            throw new ConflictException("Chặng vận chuyển này đã được quyết toán");
        }

        // lock() acquired the inspection before locking and refreshing the shipment.
        InspectionOrder inspection = inspections.findById(shipment.getInspectionOrderId()).orElseThrow();
        BigDecimal reimbursed = inspectionFeeSettlement.reimburseCashShipping(inspection, shipment, paidAmount);
        shipment.setCashConfirmedBy(actor);
        shipment.setCashReceiptReference(receiptReference);
        shipment.setCashPaidAmount(paidAmount);
        shipment.setCashReimbursedAmount(reimbursed);
        shipment.setCashConfirmedAt(Instant.now());
        shipment.setUpdatedAt(shipment.getCashConfirmedAt());
        return shipmentMapper.toResponse(shipments.save(shipment));
    }

    @Transactional public ShipmentResponse sync(UUID actor,boolean staff,UUID id) {
        var shipment=lock(id); requireRead(actor,staff,shipment);
        if(shipment.getOrderCode()==null) throw new ConflictException("Creation is uncertain; STAFF must reconcile the carrier order code");
        var data=carrier(shipment.getProvider()).detail(shipment.getOrderCode());
        if(!shipment.getOrderCode().equals(data.path("order_code").asText())) throw new ShippingProviderException("Lalamove detail does not match the shipment");
        reconcileLalamove(shipment,data);
        return shipmentMapper.toResponse(shipment);
    }

    @Transactional public void receiveLalamove(JsonNode body) {
        String code=body.path("order_code").asText();
        var found=shipments.findByProviderAndOrderCode("LALAMOVE",code);
        if (found.isEmpty()) throw new ShippingProviderException("Shipment is not linked yet; retry callback",503);
        var shipment=lock(found.get().getId());
        String key=listingFingerprint.sha256("LALAMOVE|"+body.path("event_id").asText());
        if (events.existsByEventKey(key)) return;
        Instant time=parseProviderTime(body.get("occurred_at"));
        if (time==null) throw new BadRequestException("Lalamove event has no valid update time");
        String type=body.path("event_type").asText(),status=body.path("status").asText();
        if (!status.isBlank() && !LALAMOVE_STATUSES.contains(status)) throw new BadRequestException("Unknown Lalamove status");
        var event=new ShipmentEvent(); event.setShipmentId(shipment.getId()); event.setEventKey(key); event.setType(type);
        event.setStatus(status); event.setOccurredAt(time); event.setPayload(mapper.writeValueAsString(body)); events.save(event);
        // A delayed authenticated pickup event can fill the actual pickup time without regressing
        // current shipment status. Sync/booking observation timestamps are never pickup evidence.
        if("ORDER_STATUS_CHANGED".equals(type) && "PICKED_UP".equals(status)) recordVerifiedPickup(shipment,time);
        if (shipment.getLastReconciledAt()!=null && !time.isAfter(shipment.getLastReconciledAt())) return;
        if (body.hasNonNull("replacement_order_code")) {
            String replacement=body.path("replacement_order_code").asText();
            var detail=carrier("LALAMOVE").detail(replacement);
            requireLalamoveReference(shipment,detail,replacement);
            shipment.setOrderCode(replacement); reconcileLalamove(shipment,detail); return;
        }
        var fee=body.get("fee");
        boolean currentFee = fee != null && !fee.isNull()
                && (shipment.getLastFeeEventAt() == null || !time.isBefore(shipment.getLastFeeEventAt()));
        if (currentFee) {
            requireFee(fee); shipment.setActualFee(fee.decimalValue()); shipment.setLastFeeEventAt(time);
        }
        if (shipment.getLastEventAt()!=null && time.isBefore(shipment.getLastEventAt())) return;
        if (body.hasNonNull("driver_id")) shipment.setDriverId(body.path("driver_id").asText());
        if (body.hasNonNull("share_link") && body.path("share_link").asText().startsWith("https://")) shipment.setShareLink(body.path("share_link").asText());
        if (body.hasNonNull("pod_url") && body.path("pod_url").asText().startsWith("https://")) shipment.setPodUrl(body.path("pod_url").asText());
        if ("FAILED".equals(body.path("pod_status").asText())) freeze(shipment);
        if ("COMPLETED".equals(status) && !currentFee && awaitsAutomaticReturnReimbursement(shipment)) {
            var detail = carrier("LALAMOVE").detail(code);
            if (!code.equals(detail.path("order_code").asText())
                    || !"COMPLETED".equals(detail.path("status").asText()) || !detail.hasNonNull("total_fee")) {
                throw new ShippingProviderException("Lalamove has not confirmed the final return fee; retry callback", 503);
            }
            reconcileLalamove(shipment, detail);
            return;
        }
        if (!status.isBlank()) applyLalamoveStatus(shipment,status,time,currentFee ? fee.decimalValue() : null);
        shipment.setUpdatedAt(Instant.now()); shipments.save(shipment);
    }

    @Transactional public ShipmentResponse reconcileCreation(UUID actor,boolean staff,UUID id,String code) {
        if (!staff) throw new ForbiddenException("STAFF/ADMIN must reconcile an uncertain carrier booking");
        var shipment=lock(id);
        if (!"LALAMOVE".equals(shipment.getProvider()) || !Set.of("CREATING","CREATION_UNCERTAIN").contains(shipment.getStatus()))
            throw new ConflictException("Only an uncertain Lalamove booking can be reconciled");
        if (shipments.findByProviderAndOrderCode("LALAMOVE",code).isPresent()) throw new ConflictException("Lalamove order is already linked");
        var detail=carrier("LALAMOVE").detail(code); requireLalamoveReference(shipment,detail,code);
        shipment.setOrderCode(code); shipment.setRequestedBy(actor); reconcileLalamove(shipment,detail);
        return shipmentMapper.toResponse(shipment);
    }

    @Transactional(readOnly=true) public JsonNode tracking(UUID actor,boolean staff,UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found")); requireRead(actor,staff,shipment);
        var data=mapper.createObjectNode(); data.put("shipmentId",id.toString()); data.put("provider",shipment.getProvider());
        data.put("orderCode",shipment.getOrderCode()); data.put("status",shipment.getStatus()); data.put("providerStatus",shipment.getProviderStatus());
        data.put("shareLink",shipment.getShareLink()); data.put("driverId",shipment.getDriverId()); data.put("locationStatus","UNAVAILABLE"); data.putNull("driver");
        if ("LALAMOVE".equals(shipment.getProvider()) && shipment.getOrderCode()!=null && shipment.getDriverId()!=null && !shipment.getDriverId().isBlank()
                && Set.of("ON_GOING","PICKED_UP").contains(shipment.getProviderStatus())) {
            try {
                var driver=carrier(shipment.getProvider()).driver(shipment.getOrderCode(),shipment.getDriverId());
                if(driver!=null && driver.isObject()) {
                    var visible=(ObjectNode)driver.deepCopy();
                    if(usableDriverCoordinates(driver.path("coordinates"))) data.put("locationStatus","AVAILABLE");
                    else visible.putNull("coordinates");
                    data.set("driver",visible);
                }
            } catch (ShippingProviderException error) {
                if (error.getUpstreamStatus()!=403 && error.getUpstreamStatus()!=404) throw error;
            }
        }
        return data;
    }

    private boolean usableDriverCoordinates(JsonNode coordinates) {
        if(coordinates==null || !coordinates.isObject()) return false;
        try {
            BigDecimal latitude=new BigDecimal(coordinates.path("lat").asText());
            BigDecimal longitude=new BigDecimal(coordinates.path("lng").asText());
            if(latitude.abs().compareTo(BigDecimal.valueOf(90))>0 || longitude.abs().compareTo(BigDecimal.valueOf(180))>0
                || (latitude.signum()==0 && longitude.signum()==0)) return false;
            Instant updatedAt=parseProviderTime(coordinates.get("updatedAt"));
            return updatedAt!=null && updatedAt.isAfter(Instant.EPOCH) && !updatedAt.isAfter(Instant.now());
        } catch(RuntimeException invalidCoordinates) { return false; }
    }

    private void reconcileLalamove(Shipment shipment,JsonNode data) {
        String status=data.path("status").asText();
        if (!LALAMOVE_STATUSES.contains(status)) throw new ShippingProviderException("Unknown Lalamove shipment status");
        Instant observedAt=Instant.now();
        applyFeeAndEta(shipment,data); applyCarrierLinks(shipment,data);
        Instant delivered=parseProviderTime(data.get("delivered_at"));
        BigDecimal observedFee = data.hasNonNull("total_fee") ? shipment.getActualFee() : null;
        applyLalamoveStatus(shipment,status,delivered!=null?delivered:observedAt,observedFee);
        shipment.setLastReconciledAt(observedAt); shipment.setUpdatedAt(observedAt); shipment.setLastError(null); shipments.save(shipment);
        var event=new ShipmentEvent(); event.setShipmentId(shipment.getId()); event.setEventKey(listingFingerprint.sha256("LALAMOVE|sync|"+shipment.getId()+"|"+observedAt));
        event.setType("RECONCILIATION"); event.setStatus(status); event.setOccurredAt(observedAt); event.setPayload(mapper.writeValueAsString(data)); events.save(event);
    }
    private void requireLalamoveReference(Shipment shipment,JsonNode data,String code) {
        if (!code.equals(data.path("order_code").asText()) || !shipment.getClientOrderCode().equals(data.path("raw").path("metadata").path("secondlifeClientOrderCode").asText()))
            throw new ConflictException("Carrier order does not contain this shipment's verified client reference");
    }
    private void applyLalamoveStatus(Shipment shipment,String status,Instant time,BigDecimal observedFee) {
        if (Set.of("DELIVERED","CANCELLED").contains(shipment.getStatus()) && !Objects.equals(shipment.getProviderStatus(),status)) {
            if (Set.of("REJECTED","EXPIRED").contains(status)) freeze(shipment);
            return;
        }
        shipment.setProviderStatus(status); shipment.setStatus(canonicalLalamove(status)); shipment.setLastEventAt(time); shipment.setLastError(null);
        if ("COMPLETED".equals(status) && shipment.getDeliveredAt()==null) shipment.setDeliveredAt(time);
        if (Set.of("REJECTED","EXPIRED").contains(status)) freeze(shipment);

        if (shipment.getOrderId()!=null) {
            var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
            if (!Set.of(OrderStatus.COMPLETED,OrderStatus.CANCELLED).contains(order.getStatus())) {
                boolean toBuyer=Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(shipment.getLeg());
                if (toBuyer && "PICKED_UP".equals(status) && order.getShippingDeliveredAt()==null) {
                    boolean statusChanged = order.getStatus() != OrderStatus.SHIPPED;
                    order.setStatus(OrderStatus.SHIPPED);
                    if (statusChanged && notificationService != null && order.getBuyer() != null) {
                        String code = order.getId().toString();
                        notificationService.notify(
                            order.getBuyer().getId(),
                            "Đơn hàng đang được giao #" + code,
                            "Đơn hàng #" + code + " đã được tài xế lấy và đang trên đường giao tới bạn.",
                            NotificationType.SHIPPING,
                            order.getId(),
                            "ORDER",
                            "/buyer/orders/" + order.getId()
                        );
                    }
                }
                if (toBuyer && "COMPLETED".equals(status)) {
                    boolean statusChanged = order.getStatus() != OrderStatus.DELIVERED;
                    order.setStatus(OrderStatus.DELIVERED);
                    if (order.getShippingDeliveredAt()==null) order.setShippingDeliveredAt(shipment.getDeliveredAt());
                    if (statusChanged && notificationService != null) {
                        String code = order.getId().toString();
                        if (order.getBuyer() != null) {
                            notificationService.notify(
                                order.getBuyer().getId(),
                                "Đơn hàng đã giao thành công #" + code,
                                "Đơn hàng #" + code + " đã được giao thành công. Vui lòng kiểm tra sản phẩm và xác nhận trong 48h.",
                                NotificationType.SHIPPING,
                                order.getId(),
                                "ORDER",
                                "/buyer/orders/" + order.getId()
                            );
                        }
                        if (order.getSeller() != null) {
                            notificationService.notify(
                                order.getSeller().getId(),
                                "Đơn hàng đã giao thành công #" + code,
                                "Đơn hàng #" + code + " đã được giao tới người mua.",
                                NotificationType.SHIPPING,
                                order.getId(),
                                "ORDER",
                                "/seller/orders/" + order.getId()
                            );
                        }
                    }
                }
                orders.save(order);
            }
        }

        if (shipment.getInspectionOrderId() != null) {
            applyLalamoveInspectionStatus(shipment, status, observedFee);
        }
    }

    private void applyLalamoveInspectionStatus(Shipment shipment, String status, BigDecimal observedFee) {
        var inspectionOpt = inspections.findByIdForUpdate(shipment.getInspectionOrderId());
        if (inspectionOpt.isEmpty()) return;
        var inspection = inspectionOpt.get();

        if ("SELLER_TO_CENTER".equals(shipment.getLeg())) {
            if ("PICKED_UP".equals(status)) {
                inspection.setStatus("SHIPPING_TO_CENTER");
                if (sealLogs != null && inspection.getTamperSealId() != null
                        && !sealLogs.existsByTamperSealIdAndScanStage(inspection.getTamperSealId(), "CARRIER_HANDOVER")) {
                    var sealLog = new InspectionSealLog(
                            inspection,
                            inspection.getTamperSealId(),
                            null,
                            "CARRIER_HANDOVER",
                            "INTACT",
                            "Tài xế đã nhận hàng từ Người bán",
                            "Ghi nhận tự động từ Webhook đối tác vận chuyển"
                    );
                    sealLogs.save(sealLog);
                }
            } else if ("COMPLETED".equals(status)) {
                if (!Set.of("PASSED", "FAILED", "COMPLETED").contains(inspection.getStatus())) {
                    if (inspection.getInspector() == null) {
                        inspection.setStatus("CONFIRMED_PENDING_DISPATCH");
                    } else {
                        inspection.setStatus("INSPECTING");
                    }
                }
            }
        } else if (Set.of("CENTER_TO_BUYER", "SELLER_TO_BUYER").contains(shipment.getLeg())) {
            if ("PICKED_UP".equals(status)) {
                inspection.setStatus("DELIVERING_TO_BUYER");
                if (sealLogs != null && inspection.getTamperSealId() != null
                        && !sealLogs.existsByTamperSealIdAndScanStage(inspection.getTamperSealId(), "CARRIER_HANDOVER")) {
                    var sealLog = new InspectionSealLog(
                            inspection,
                            inspection.getTamperSealId(),
                            null,
                            "CARRIER_HANDOVER",
                            "INTACT",
                            "Tài xế nhận hàng để giao tới Người mua",
                            "Ghi nhận tự động từ Webhook đối tác vận chuyển"
                    );
                    sealLogs.save(sealLog);
                }
            } else if ("COMPLETED".equals(status)) {
                if (sealLogs != null && inspection.getTamperSealId() != null
                        && !sealLogs.existsByTamperSealIdAndScanStage(inspection.getTamperSealId(), "DELIVERED_BUYER")) {
                    var sealLog = new InspectionSealLog(
                            inspection,
                            inspection.getTamperSealId(),
                            null,
                            "DELIVERED_BUYER",
                            "INTACT",
                            "Đã giao tận tay Người mua",
                            "Ghi nhận tự động từ Webhook đối tác vận chuyển"
                    );
                    sealLogs.save(sealLog);
                }
            }
        } else if ("CENTER_TO_SELLER".equals(shipment.getLeg()) && "COMPLETED".equals(status)) {
            if ("PASSED".equals(inspection.getStatus())) {
                inspection.setStatus("CANCELLED");
            }
            if (observedFee != null) {
                if (inspection.isCashShippingSimulation()) {
                    reimburseDeliveredCashReturn(inspection, shipment, observedFee);
                } else {
                    inspectionFeeSettlement.settleReturnShipping(inspection, observedFee);
                }
            }
        }
        inspections.save(inspection);
    }

    private boolean awaitsAutomaticReturnReimbursement(Shipment shipment) {
        return cashShippingSimulationEnabled && "LALAMOVE".equals(shipment.getProvider())
                && "CENTER_TO_SELLER".equals(shipment.getLeg()) && shipment.getInspectionOrderId() != null
                && shipment.getCashPayerId() != null && shipment.getCashConfirmedAt() == null;
    }

    private void reimburseDeliveredCashReturn(InspectionOrder inspection, Shipment shipment, BigDecimal actualFee) {
        if (!awaitsAutomaticReturnReimbursement(shipment) || shipment.getOrderCode() == null
                || shipments.existsByInspectionOrderIdAndLegAndCashConfirmedAtIsNotNull(
                        inspection.getId(), shipment.getLeg())
                || !inspectionFeeSettlement.reimburseDeliveredReturn(inspection, shipment, actualFee)) {
            return;
        }

        shipment.setCashReceiptReference("LALAMOVE_COMPLETED:" + shipment.getOrderCode());
        shipment.setCashPaidAmount(actualFee);
        shipment.setCashReimbursedAmount(actualFee);
        shipment.setCashConfirmedAt(Instant.now());
    }

    private void recordVerifiedPickup(Shipment shipment,Instant actualTime) {
        if(shipment.getOrderId()==null || !"SELLER_TO_BUYER".equals(shipment.getLeg())) return;
        var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        Instant pickup=actualTime.truncatedTo(ChronoUnit.MICROS);
        if(order.getCarrierPickedUpAt()==null || pickup.isBefore(order.getCarrierPickedUpAt())) {
            order.setCarrierPickedUpAt(pickup); orders.save(order);
        }
    }
    private void freeze(Shipment shipment) {
        if (shipment.getOrderId()!=null) {
            var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
            if (order.getEscrowStatus()==EscrowStatus.HELD || order.getEscrowStatus()==EscrowStatus.FROZEN) {
                order.setEscrowStatus(EscrowStatus.FROZEN);
                order.setShippingHold(true);
                orders.save(order);
            }
        }
    }
    private String canonicalLalamove(String status) {
        return switch(status) {
            case "ASSIGNING_DRIVER" -> "READY_TO_PICK";
            case "ON_GOING" -> "ASSIGNED";
            case "PICKED_UP" -> "PICKED";
            case "COMPLETED" -> "DELIVERED";
            case "CANCELED" -> "CANCELLED";
            case "REJECTED","EXPIRED" -> "FAILED";
            default -> throw new ShippingProviderException("Unknown Lalamove shipment status");
        };
    }
    private boolean sameRoute(Map<String,Object> a,Map<String,Object> b) {
        var left=new LinkedHashMap<>(a); var right=new LinkedHashMap<>(b);
        left.remove("provider_quote"); right.remove("provider_quote");
        return mapper.readTree(mapper.writeValueAsString(left)).equals(mapper.readTree(mapper.writeValueAsString(right)));
    }
    private String activeCarrier() { return provider.code()==null?"LALAMOVE":provider.code(); }
    private boolean matchesAddress(ShippingAddressRequest request,ShippingAddress stored) {
        return new ShippingAddress(request.name().strip(),request.phone().strip(),stored.address(),request.latitude(),request.longitude()).equals(stored);
    }
    private ShippingProvider carrier(String code) { var chosen=provider.forProvider(code); return chosen==null?provider:chosen; }
    private void applyCarrierLinks(Shipment shipment,JsonNode data) {
        if (data.hasNonNull("driver_id")) shipment.setDriverId(data.path("driver_id").asText());
        if (data.path("share_link").asText().startsWith("https://")) shipment.setShareLink(data.path("share_link").asText());
        if (data.path("pod_url").asText().startsWith("https://")) shipment.setPodUrl(data.path("pod_url").asText());
    }
    private void requireFee(JsonNode fee) {
        if (!fee.isNumber() || fee.decimalValue().signum()<0 || fee.decimalValue().compareTo(BigDecimal.valueOf(1000000000))>0)
            throw new ShippingProviderException("Carrier returned an invalid shipping fee");
    }
    private Shipment lock(UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (shipment.getOrderId()!=null) orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        else inspections.findByIdForUpdate(shipment.getInspectionOrderId()).orElseThrow();
        shipments.refreshManaged(shipment, LockModeType.PESSIMISTIC_WRITE);
        return shipment;
    }
    private UUID shipmentId(UUID parent,CreateShipmentRequest request) {
        return UUID.nameUUIDFromBytes((parent+":"+request.leg()+":"+request.requestId()).getBytes(StandardCharsets.UTF_8));
    }
    private void requireRead(UUID actor,boolean staff,Shipment shipment) {
        if (staff || actor.equals(shipment.getSellerId()) || actor.equals(shipment.getBuyerId())) return;
        if (shipment.getInspectionOrderId() != null) {
            var inspection = inspections.findById(shipment.getInspectionOrderId()).orElse(null);
            if (inspection != null && ((inspection.getInspectionCenter() != null && actor.equals(inspection.getInspectionCenter().getId()))
                    || (inspection.getBuyer() != null && actor.equals(inspection.getBuyer().getId())))) return;
        }
        throw new ForbiddenException("Shipment belongs to another user");
    }
    private void requireOrderActor(UUID actor,boolean staff,Order order) {
        if (!staff && !actor.equals(order.getBuyer().getId()) && !actor.equals(order.getSeller().getId())) throw new ForbiddenException("Order belongs to another user");
    }
    @SuppressWarnings("unchecked") private Map<String,Object> readMap(String json) { return mapper.readValue(json,LinkedHashMap.class); }
    private void applyFeeAndEta(Shipment shipment,JsonNode data) {
        var fee=data.get("total_fee"); if (fee!=null && !fee.isNull()) { requireFee(fee); shipment.setActualFee(fee.decimalValue()); }
        shipment.setExpectedDeliveryTime(parseProviderTime(data.get("expected_delivery_time")));
    }
    private Instant parseProviderTime(JsonNode node) {
        if (node==null || node.isNull()) return null;
        try {
            if (node.isNumber()) {
                long v = node.longValue();
                return v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
            }
            String text = node.asText().trim();
            if (text.isEmpty()) return null;
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return OffsetDateTime.parse(text.contains(" ") ? text.replace(" ", "T") : text).toInstant();
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
