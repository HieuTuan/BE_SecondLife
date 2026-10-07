package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.GhnProperties;
import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.WalletService;
import com.secondlife.secondlife.service.ListingFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final OrderRepository orders;
    private final InspectionOrderRepository inspections;
    private final ShippingQuoteRepository quotes;
    private final ShippingQuoteService quoteService;
    private final ShippingProvider provider;
    private final GhnProperties config;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final WalletService wallets;

    private static final Set<String> SAFE_CANCEL = Set.of("READY_TO_PICK","CANCELLED");
    private static final Set<String> ISSUES = Set.of("lost","damage","exception","scrap");
    private static final Set<String> KNOWN = Set.of("ready_to_pick","picking","money_collect_picking","picked","storing","sorting","transporting",
        "delivering","money_collect_delivering","delivered","delivery_fail","waiting_to_return","return","return_transporting","return_sorting","returning","return_fail","returned","cancel","exception","lost","damage","scrap");

    @Transactional public ShipmentResponse importLegacy(UUID actor,boolean staff,UUID orderId,ImportShipmentRequest request) {
        if (!staff) throw new ForbiddenException("STAFF/ADMIN must verify a legacy carrier shipment");
        var order=orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        UUID id=UUID.nameUUIDFromBytes((orderId+":LEGACY:"+request.requestId()).getBytes(StandardCharsets.UTF_8));
        var replay=shipments.findById(id);
        if (replay.isPresent()) {
            if (!request.orderCode().equals(replay.get().getOrderCode()) || !request.reason().equals(replay.get().getReason()))
                throw new ConflictException("requestId was already used with different legacy shipment details");
            return ShipmentResponse.from(replay.get());
        }
        if (order.getShippingQuoteId()!=null || order.getEscrowStatus()!=EscrowStatus.HELD
                || !Set.of(OrderStatus.PROCESSING,OrderStatus.SHIPPED).contains(order.getStatus()))
            throw new ConflictException("Only unsettled legacy orders without shipping quotes can import a carrier shipment");
        if (shipments.existsByOrderId(orderId) || shipments.findByOrderCode(request.orderCode()).isPresent())
            throw new ConflictException("Order or carrier shipment already has a shipping association");
        var data=provider.detail(request.orderCode());
        if (!request.orderCode().equals(data.path("order_code").asText()) || data.path("shop_id").asInt()!=config.getShopId() || config.getShopId()<=0)
            throw new ConflictException("GHN shipment must belong to the configured shop");
        if (!data.path("cod_amount").isNumber() || data.path("cod_amount").decimalValue().signum()!=0)
            throw new ConflictException("An escrow order cannot import a carrier shipment with COD collection");
        String status=data.path("status").asText();
        Instant time=parseProviderTime(data.get("updated_date"));
        if (!KNOWN.contains(status) || time==null || time.isAfter(Instant.now().plusSeconds(config.getWebhookMaxFutureSkewSeconds())))
            throw new ShippingProviderException("GHN shipment has an invalid status or update time");
        String clientCode=data.path("client_order_code").asText();
        if (clientCode.isBlank()) clientCode="SL"+id;
        if (clientCode.length()>50) throw new ShippingProviderException("GHN shipment client code exceeds its limit");
        var shipment=new Shipment(); shipment.setId(id); shipment.setOrderId(orderId); shipment.setSellerId(order.getSeller().getId());
        shipment.setBuyerId(order.getBuyer().getId()); shipment.setRequestId(request.requestId()); shipment.setLeg("SELLER_TO_BUYER");
        shipment.setClientOrderCode(clientCode); shipment.setOrderCode(request.orderCode()); shipment.setStatus("IMPORTED");
        shipment.setPayload(mapper.writeValueAsString(data)); shipment.setReason(request.reason()); shipment.setRequestedBy(actor);
        shipment.setExpectedDeliveryTime(parseProviderTime(data.get("leadtime"))); shipment=shipments.saveAndFlush(shipment);
        var callback=new LinkedHashMap<String,Object>(); callback.put("ShopID",config.getShopId()); callback.put("OrderCode",request.orderCode());
        callback.put("ClientOrderCode",clientCode); callback.put("Type","legacy_import"); callback.put("Status",status); callback.put("Time",time.toString());
        callback.put("Reason",request.reason()); callback.put("VerifiedBy",actor.toString());
        if (data.has("total_fee")) callback.put("TotalFee",data.get("total_fee"));
        receive(mapper.valueToTree(callback)); return ShipmentResponse.from(shipment);
    }

    @Transactional public ShipmentResponse createForOrder(UUID actor,boolean staff,UUID orderId,CreateShipmentRequest request) {
        var order=orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!staff && !actor.equals(order.getSeller().getId())) throw new ForbiddenException("Only the seller can create an outbound shipment");
        if (order.getShippingQuoteId()==null) throw new ConflictException("Order has no paid shipping quote");
        if (order.getStatus()==OrderStatus.CANCELLED || order.getStatus()==OrderStatus.COMPLETED) throw new ConflictException("Order is already closed");
        if (!Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER","BUYER_TO_SELLER").contains(request.leg())) throw new BadRequestException("Invalid order shipment leg");
        var quote=quotes.findById(order.getShippingQuoteId()).orElseThrow();
        Map<String,Object> payload=readMap(quote.getPayload());
        BigDecimal quotedFee=quote.getFee();
        if ("BUYER_TO_SELLER".equals(request.leg())) {
            if (!staff || request.reason()==null || request.reason().isBlank()) throw new ForbiddenException("A STAFF/ADMIN return decision with reason is required");
            if (order.getShippingDeliveredAt()==null) throw new ConflictException("Outbound delivery must be confirmed before creating a buyer return");
            if (order.getEscrowStatus()!=EscrowStatus.HELD && order.getEscrowStatus()!=EscrowStatus.FROZEN) throw new ConflictException("Funds are already settled");
            var from=mapper.readValue(order.getDeliveryAddress(),ShippingAddress.class);
            var original=mapper.readTree(quote.getPayload());
            var to=mapper.readValue(quote.getSellerPickupAddress(),ShippingAddress.class);
            payload=ShippingPayloads.create(from,to,ShippingPayloads.storedParcel(original),order.getPost().getTitle(),order.getFinalPrice());
            quotedFee=null;
            order.setEscrowStatus(EscrowStatus.FROZEN); orders.save(order);
        } else {
            if (!request.leg().equals(quote.getLeg())) throw new ConflictException("Shipment leg must match the paid shipping quote: "+quote.getLeg());
            if (order.getEscrowStatus()!=EscrowStatus.HELD || order.getShippingDeliveredAt()!=null) throw new ConflictException("Order is not eligible for outbound shipping");
            if ("CENTER_TO_BUYER".equals(request.leg())) {
                if (!staff) throw new ForbiddenException("STAFF/ADMIN creates shipments from the inspection center");
                var inspection=inspections.findByPostId(order.getPost().getId()).orElseThrow(() -> new ConflictException("Inspection is required for the center leg"));
                if (!"PASSED".equals(inspection.getStatus())) throw new ConflictException("Inspection has not passed");
                if (request.fromAddress()!=null && !request.fromAddress().equals(ShippingPayloads.storedAddress(mapper.readTree(quote.getPayload()),"from")))
                    throw new ConflictException("Inspection center address must match the paid shipping quote");
            }
        }
        return create(actor,orderId,null,order.getSeller().getId(),order.getBuyer().getId(),request,payload,quotedFee);
    }

    @Transactional public ShipmentResponse createForInspection(UUID actor,boolean staff,UUID inspectionId,CreateShipmentRequest request) {
        UUID seller=inspections.findOwnerId(inspectionId).orElseThrow(() -> new NotFoundException("Inspection order not found"));
        if (!staff && !actor.equals(seller)) throw new ForbiddenException("This inspection belongs to another seller");
        if (!"SELLER_TO_CENTER".equals(request.leg()) || request.toAddress()==null) throw new BadRequestException("SELLER_TO_CENTER and an inspection center address are required");
        if (!staff) throw new ForbiddenException("STAFF/ADMIN assigns the verified inspection center destination");
        var inspection=inspections.findByIdForUpdate(inspectionId).orElseThrow();
        if (!"PENDING".equals(inspection.getStatus())) throw new ConflictException("Inspection is already completed");
        var post=inspection.getPost();
        var saved=shipments.findById(shipmentId(inspectionId,request));
        Map<String,Object> payload;
        if (saved.isPresent()) {
            var original=saved.get();
            if (!Objects.equals(original.getReason(),request.reason())
                    || !request.toAddress().equals(ShippingPayloads.storedAddress(mapper.readTree(original.getPayload()),"to")))
                throw new ConflictException("requestId was already used with different shipment details");
            payload=readMap(original.getPayload());
        } else payload=ShippingPayloads.create(quoteService.pickup(seller),request.toAddress(),ShippingPayloads.parcel(post),post.getTitle(),post.getPrice());
        return create(actor,null,inspectionId,seller,null,request,payload,null);
    }

    private ShipmentResponse create(UUID actor,UUID orderId,UUID inspectionId,UUID seller,UUID buyer,CreateShipmentRequest request,Map<String,Object> payload,BigDecimal fee) {
        UUID parent=orderId!=null?orderId:inspectionId;
        UUID id=shipmentId(parent,request);
        payload.put("client_order_code","SL"+id);
        String serialized=mapper.writeValueAsString(payload);
        var existing=shipments.findById(id);
        Shipment shipment;
        if (existing.isPresent()) {
            shipment=existing.get();
            if (!serialized.equals(shipment.getPayload()) || !Objects.equals(request.reason(),shipment.getReason())) throw new ConflictException("requestId was already used with different shipment details");
            if (shipment.getOrderCode()!=null) return ShipmentResponse.from(shipment);
        } else {
            var siblings=orderId!=null?shipments.findByOrderIdOrderByCreatedAtAsc(orderId):shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionId);
            boolean outbound=!"BUYER_TO_SELLER".equals(request.leg());
            if (siblings.stream().anyMatch(s -> !"CANCELLED".equals(s.getStatus())
                    && !("RETURNED".equals(s.getStatus()) && Set.of("BUYER_TO_SELLER","SELLER_TO_CENTER").contains(s.getLeg()))
                    && (s.getLeg().equals(request.leg())
                    || (outbound && Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(s.getLeg())))))
                throw new ConflictException("A shipment is already active or uncertain; retry its original requestId");
            if (fee==null) {
                var total=provider.preview(payload).get("total_fee");
                if (total==null || !total.isNumber() || total.decimalValue().signum()<0 || total.decimalValue().compareTo(BigDecimal.valueOf(1000000000))>0)
                    throw new ShippingProviderException("GHN returned an invalid shipping fee");
                fee=total.decimalValue();
            }
            shipment=new Shipment(); shipment.setId(id); shipment.setOrderId(orderId); shipment.setInspectionOrderId(inspectionId);
            shipment.setSellerId(seller); shipment.setBuyerId(buyer); shipment.setRequestId(request.requestId()); shipment.setLeg(request.leg());
            shipment.setClientOrderCode("SL"+id); shipment.setPayload(serialized); shipment.setReason(request.reason()); shipment.setRequestedBy(actor);
            shipment.setStatus("CREATING"); shipment.setQuotedFee(fee);
            shipment=shipments.saveAndFlush(shipment);
        }
        try {
            var result=provider.create(payload);
            var code=result.get("order_code");
            if (code==null || !code.isString() || !code.asText().matches("[A-Za-z0-9_-]{1,50}")) throw new ShippingProviderException("GHN returned no valid tracking code");
            shipment.setOrderCode(code.asText()); shipment.setProviderStatus("ready_to_pick"); shipment.setStatus("READY_TO_PICK"); shipment.setLastError(null);
            applyFeeAndEta(shipment,result);
        } catch (ShippingProviderException ex) {
            // Persist uncertainty. The same deterministic client_order_code recovers a provider-created order after timeouts.
            shipment.setStatus("CREATION_UNCERTAIN"); shipment.setLastError("GHN creation could not be confirmed; retry the same requestId");
        }
        shipment.setUpdatedAt(Instant.now()); shipments.save(shipment); return ShipmentResponse.from(shipment);
    }

    @Transactional(readOnly=true) public List<ShipmentResponse> orderShipments(UUID actor,boolean staff,UUID id) {
        var order=orders.findById(id).orElseThrow(() -> new NotFoundException("Order not found")); requireOrderActor(actor,staff,order);
        return shipments.findByOrderIdOrderByCreatedAtAsc(id).stream().map(ShipmentResponse::from).toList();
    }
    @Transactional(readOnly=true) public List<ShipmentResponse> inspectionShipments(UUID actor,boolean staff,UUID id) {
        UUID seller=inspections.findOwnerId(id).orElseThrow(() -> new NotFoundException("Inspection not found"));
        if (!staff && !seller.equals(actor)) throw new ForbiddenException("Inspection belongs to another seller");
        return shipments.findByInspectionOrderIdOrderByCreatedAtAsc(id).stream().map(ShipmentResponse::from).toList();
    }
    @Transactional(readOnly=true) public List<ShipmentEvent> timeline(UUID actor,boolean staff,UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found")); requireRead(actor,staff,shipment);
        return events.findByShipmentIdOrderByOccurredAtAsc(id);
    }
    @Transactional(readOnly=true) public JsonNode label(UUID actor,boolean staff,UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
        UUID sender="BUYER_TO_SELLER".equals(shipment.getLeg())?shipment.getBuyerId():shipment.getSellerId();
        if (!staff && !actor.equals(sender)) throw new ForbiddenException("Only the shipment sender can print the label");
        if (shipment.getOrderCode()==null) throw new ConflictException("Shipment creation is not confirmed");
        return provider.label(shipment.getOrderCode());
    }

    @Transactional public ShipmentResponse cancel(UUID actor,boolean staff,UUID id) {
        var shipment=lock(id); if (!staff && !actor.equals(shipment.getSellerId())) throw new ForbiddenException("Only the seller can cancel this shipment");
        if (!staff && "BUYER_TO_SELLER".equals(shipment.getLeg())) throw new ForbiddenException("STAFF/ADMIN must cancel an approved return shipment");
        if ("CANCELLED".equals(shipment.getStatus())) return ShipmentResponse.from(shipment);
        if (!SAFE_CANCEL.contains(shipment.getStatus()) || shipment.getOrderCode()==null) throw new ConflictException("Carrier pickup has started or creation is uncertain; cancellation requires reconciliation");
        provider.cancel(shipment.getOrderCode()); shipment.setStatus("CANCELLED"); shipment.setProviderStatus("cancel"); shipment.setUpdatedAt(Instant.now());
        shipments.save(shipment); return ShipmentResponse.from(shipment);
    }
    @Transactional public ShipmentResponse returnToSender(UUID actor,boolean staff,UUID id,String reason) {
        if (!staff) throw new ForbiddenException("STAFF/ADMIN must approve return-to-sender");
        var shipment=lock(id);
        if (reason==null || reason.isBlank()) throw new BadRequestException("Return reason is required");
        if (!Set.of("delivery_fail","storing","waiting_to_return","return").contains(shipment.getProviderStatus())) throw new ConflictException("GHN does not allow return-to-sender in this status");
        provider.returnToSender(shipment.getOrderCode()); shipment.setReason(reason); shipment.setStatus("RETURN_REQUESTED");
        if (shipment.getOrderId()!=null) {
            var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
            if (order.getEscrowStatus()==EscrowStatus.HELD) order.setEscrowStatus(EscrowStatus.FROZEN);
        }
        shipments.save(shipment); return ShipmentResponse.from(shipment);
    }
    @Transactional public ShipmentResponse refundReturned(UUID actor,boolean staff,UUID id,String reason) {
        if (!staff) throw new ForbiddenException("STAFF/ADMIN must approve the refund");
        var shipment=lock(id);
        if (shipment.getOrderId()==null) throw new ConflictException("Inspection shipping cannot refund a product order");
        boolean buyerReturn="BUYER_TO_SELLER".equals(shipment.getLeg());
        if (!(buyerReturn && "DELIVERED".equals(shipment.getStatus()))
                && !(!buyerReturn && "RETURNED".equals(shipment.getStatus())))
            throw new ConflictException("Product must be physically returned before refund");
        var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        if (order.getEscrowStatus()==EscrowStatus.REFUNDED) return ShipmentResponse.from(shipment);
        if (order.getEscrowStatus()!=EscrowStatus.FROZEN) throw new ConflictException("Return refund requires frozen funds");
        wallets.processRefund(order.getBuyer().getId(),order.getFinalPrice(),order.getId());
        order.setStatus(OrderStatus.CANCELLED); order.setEscrowStatus(EscrowStatus.REFUNDED); orders.save(order);
        shipment.setReason(reason); shipment.setRequestedBy(actor); shipments.save(shipment);
        // Do not re-publish returned/disputed goods automatically; the seller must resolve their condition.
        return ShipmentResponse.from(shipment);
    }

    @Transactional public ShipmentResponse sync(UUID actor,boolean staff,UUID id) {
        var shipment=lock(id); requireRead(actor,staff,shipment);
        if (shipment.getOrderCode()==null) throw new ConflictException("Retry the original creation request to recover its tracking code");
        var data=provider.detail(shipment.getOrderCode());
        if (!shipment.getOrderCode().equals(data.path("order_code").asText())) throw new ShippingProviderException("GHN detail does not match the shipment");
        String status=data.path("status").asText();
        if (!KNOWN.contains(status)) throw new ShippingProviderException("GHN returned an unknown shipment status");
        var callback=new LinkedHashMap<String,Object>(); callback.put("ShopID",config.getShopId()); callback.put("OrderCode",shipment.getOrderCode());
        callback.put("ClientOrderCode",shipment.getClientOrderCode()); callback.put("Type","reconciliation"); callback.put("Status",status);
        // A carrier timestamp is necessary so old callback deliveries cannot overwrite reconciliation.
        Instant time=parseProviderTime(data.get("updated_date"));
        if (time==null) throw new ShippingProviderException("GHN detail has no update timestamp");
        callback.put("Time",time.toString()); callback.put("TotalFee",data.path("total_fee"));
        receive(mapper.valueToTree(callback)); return ShipmentResponse.from(shipment);
    }

    public void verifyWebhook(String supplied) {
        String expected=config.getWebhookSecret();
        if (!config.isEnabled() || expected==null || expected.isBlank() || supplied==null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ForbiddenException("Invalid GHN webhook credentials");
    }
    @Transactional public void receive(JsonNode body) {
        if (!body.isObject() || body.path("ShopID").asInt()!=config.getShopId() || config.getShopId()<=0) throw new ForbiddenException("Webhook shop does not match");
        String code=body.path("OrderCode").asText(),type=body.path("Type").asText();
        if (type.isBlank()) type="switch_status";
        if (code.isBlank() || code.length()>100 || type.length()>100) throw new BadRequestException("Webhook order code and type are required");
        Instant time=parseProviderTime(body.get("Time"));
        if (time==null) time=Instant.now();
        else if (time.isAfter(Instant.now().plusSeconds(config.getWebhookMaxFutureSkewSeconds()))) throw new BadRequestException("Webhook time is invalid");
        var found=shipments.findByOrderCode(code);
        if (found.isEmpty()) found=shipments.findByClientOrderCode(body.path("ClientOrderCode").asText());
        if (found.isEmpty()) throw new ShippingProviderException("Shipment is not committed yet; retry callback",503);
        var shipment=lock(found.get().getId());
        if (shipment.getOrderCode()!=null && !code.equals(shipment.getOrderCode())) throw new BadRequestException("Webhook tracking code does not match");
        if (!body.path("ClientOrderCode").asText().isBlank() && !shipment.getClientOrderCode().equals(body.path("ClientOrderCode").asText())) throw new BadRequestException("Webhook client order code does not match");
        String eventKey=ListingFingerprint.sha256(code+"|"+type+"|"+time);
        if (events.existsByEventKey(eventKey)) return;
        String status=body.path("Status").asText();
        if (!KNOWN.contains(status)) throw new BadRequestException("Unknown GHN status");
        var event=new ShipmentEvent(); event.setShipmentId(shipment.getId()); event.setEventKey(eventKey); event.setType(type);
        event.setOccurredAt(time); event.setStatus(status); event.setReason(body.path("Reason").asText()); event.setPayload(mapper.writeValueAsString(body)); events.save(event);
        JsonNode fee=body.get("TotalFee");
        boolean hasFee="update_fee".equals(type) || "reconciliation".equals(type) || (body.path("Fee").isObject() && !body.path("Fee").isEmpty());
        if (hasFee && fee!=null && fee.isNumber() && fee.decimalValue().signum()>=0 && fee.decimalValue().compareTo(BigDecimal.valueOf(1000000000))<=0
                && (shipment.getLastFeeEventAt()==null || !time.isBefore(shipment.getLastFeeEventAt()))) {
            shipment.setActualFee(fee.decimalValue()); shipment.setLastFeeEventAt(time);
        }
        if (shipment.getLastEventAt()!=null && time.isBefore(shipment.getLastEventAt())) return;
        if ("CANCELLED".equals(shipment.getStatus()) && !"cancel".equals(status)) return;
        // Delivery and return are terminal carrier outcomes. Record contradictory events without reopening transit.
        if (Set.of("DELIVERED","RETURNED").contains(shipment.getStatus()) && !shipment.getProviderStatus().equals(status)) {
            if (ISSUES.contains(status) && shipment.getOrderId()!=null) {
                var affected=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
                if (affected.getEscrowStatus()==EscrowStatus.HELD) affected.setEscrowStatus(EscrowStatus.FROZEN);
            }
            return;
        }
        shipment.setOrderCode(code); shipment.setLastEventAt(time); shipment.setProviderStatus(status); shipment.setStatus("cancel".equals(status)?"CANCELLED":status.toUpperCase(Locale.ROOT));
        shipment.setUpdatedAt(Instant.now()); shipment.setLastError(null);
        if ("delivered".equals(status)) { shipment.setDeliveredAt(time); String pod=body.path("PodURL").asText(); if (pod.startsWith("https://")) shipment.setPodUrl(pod); }
        shipments.save(shipment);
        if (shipment.getOrderId()!=null) {
            var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
            if (order.getStatus()==OrderStatus.COMPLETED || order.getStatus()==OrderStatus.CANCELLED) return;
            boolean toBuyer=Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(shipment.getLeg());
            if (toBuyer && "cancel".equals(status) && "legacy_import".equals(type) && order.getShippingQuoteId()==null)
                order.setStatus(OrderStatus.PROCESSING);
            if (toBuyer && "delivered".equals(status)) { order.setStatus(OrderStatus.DELIVERED); order.setShippingDeliveredAt(time); }
            else if (toBuyer && Set.of("picked","storing","sorting","transporting","delivering").contains(status) && order.getShippingDeliveredAt()==null) order.setStatus(OrderStatus.SHIPPED);
            if ((ISSUES.contains(status) || "returned".equals(status)) && order.getEscrowStatus()==EscrowStatus.HELD) order.setEscrowStatus(EscrowStatus.FROZEN);
            orders.save(order);
        }
    }
    private Shipment lock(UUID id) {
        var shipment=shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (shipment.getOrderId()!=null) orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        else inspections.findByIdForUpdate(shipment.getInspectionOrderId()).orElseThrow();
        entityManager.refresh(shipment,LockModeType.PESSIMISTIC_WRITE);
        return shipment;
    }
    private UUID shipmentId(UUID parent,CreateShipmentRequest request) {
        return UUID.nameUUIDFromBytes((parent+":"+request.leg()+":"+request.requestId()).getBytes(StandardCharsets.UTF_8));
    }
    private void requireRead(UUID actor,boolean staff,Shipment shipment) {
        if (!staff && !actor.equals(shipment.getSellerId()) && !actor.equals(shipment.getBuyerId())) throw new ForbiddenException("Shipment belongs to another user");
    }
    private void requireOrderActor(UUID actor,boolean staff,Order order) {
        if (!staff && !actor.equals(order.getBuyer().getId()) && !actor.equals(order.getSeller().getId())) throw new ForbiddenException("Order belongs to another user");
    }
    @SuppressWarnings("unchecked") private Map<String,Object> readMap(String json) { return mapper.readValue(json,LinkedHashMap.class); }
    private void applyFeeAndEta(Shipment shipment,JsonNode data) {
        var fee=data.get("total_fee"); if (fee!=null && fee.isNumber() && fee.decimalValue().signum()>=0) shipment.setActualFee(fee.decimalValue());
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
                return java.time.OffsetDateTime.parse(text.contains(" ") ? text.replace(" ", "T") : text).toInstant();
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
