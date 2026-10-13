package com.secondlife.secondlife.service.shipping;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service @RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final OrderRepository orders;
    private final InspectionOrderRepository inspections;
    private final ShippingQuoteRepository quotes;
    private final ShippingQuoteService quoteService;
    private final ReverseGeocodingService geocoding;
    private final ShippingProvider provider;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final WalletService wallets;
    private final PlatformTransactionManager transactions;

    private record Prepared(UUID id, boolean submit) {}

    private static final Set<String> LALAMOVE_STATUSES=Set.of("ASSIGNING_DRIVER","ON_GOING","PICKED_UP","COMPLETED","CANCELED","REJECTED","EXPIRED");

    public ShipmentResponse createForOrder(UUID actor,boolean staff,UUID orderId,CreateShipmentRequest request) {
        Prepared prepared=new TransactionTemplate(transactions).execute(tx -> prepareForOrder(actor,staff,orderId,request));
        return submit(prepared);
    }
    private Prepared prepareForOrder(UUID actor,boolean staff,UUID orderId,CreateShipmentRequest request) {
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
            if (original.hasNonNull("service_type")) payload.put("service_type",original.path("service_type").asText());
            quotedFee=null;
            order.setEscrowStatus(EscrowStatus.FROZEN); orders.save(order);
        } else {
            if (!request.leg().equals(quote.getLeg())) throw new ConflictException("Shipment leg must match the paid shipping quote: "+quote.getLeg());
            if (order.getEscrowStatus()!=EscrowStatus.HELD || order.getShippingDeliveredAt()!=null) throw new ConflictException("Order is not eligible for outbound shipping");
            if ("CENTER_TO_BUYER".equals(request.leg())) {
                if (!staff) throw new ForbiddenException("STAFF/ADMIN creates shipments from the inspection center");
                var inspection=inspections.findByPostId(order.getPost().getId()).orElseThrow(() -> new ConflictException("Inspection is required for the center leg"));
                if (!"PASSED".equals(inspection.getStatus())) throw new ConflictException("Inspection has not passed");
                if (request.fromAddress()!=null && !matchesAddress(request.fromAddress(),ShippingPayloads.storedAddress(mapper.readTree(quote.getPayload()),"from")))
                    throw new ConflictException("Inspection center address must match the paid shipping quote");
            }
        }
        return prepare(actor,orderId,null,order.getSeller().getId(),order.getBuyer().getId(),request,payload,quotedFee,quote.getProvider());
    }

    public ShipmentResponse createForInspection(UUID actor,boolean staff,UUID inspectionId,CreateShipmentRequest request) {
        Prepared prepared=new TransactionTemplate(transactions).execute(tx -> prepareForInspection(actor,staff,inspectionId,request));
        return submit(prepared);
    }
    private Prepared prepareForInspection(UUID actor,boolean staff,UUID inspectionId,CreateShipmentRequest request) {
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
                    || !matchesAddress(request.toAddress(),ShippingPayloads.storedAddress(mapper.readTree(original.getPayload()),"to")))
                throw new ConflictException("requestId was already used with different shipment details");
            payload=readMap(original.getPayload());
        } else payload=ShippingPayloads.create(quoteService.pickup(seller),geocoding.shipping(request.toAddress()),ShippingPayloads.parcel(post),post.getTitle(),post.getPrice());
        return prepare(actor,null,inspectionId,seller,null,request,payload,null,saved.map(Shipment::getProvider).orElse(activeCarrier()));
    }

    private Prepared prepare(UUID actor,UUID orderId,UUID inspectionId,UUID seller,UUID buyer,CreateShipmentRequest request,Map<String,Object> payload,BigDecimal fee,String carrierCode) {
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
            boolean outbound=!"BUYER_TO_SELLER".equals(request.leg());
            if (siblings.stream().anyMatch(s -> !"CANCELLED".equals(s.getStatus())
                    && !("RETURNED".equals(s.getStatus()) && Set.of("BUYER_TO_SELLER","SELLER_TO_CENTER").contains(s.getLeg()))
                    && (s.getLeg().equals(request.leg())
                    || (outbound && Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(s.getLeg())))))
                throw new ConflictException("A shipment is already active or uncertain; retry its original requestId");
            // A paid quotation can still be unexpired after its pickup schedule has passed.
            // Refresh only new booking attempts; existing requests return above without another provider mutation.
            if (fee==null || "LALAMOVE".equals(carrierCode)) {
                var preview=carrier(carrierCode).preview(payload);
                var total=preview.get("total_fee");
                if (total==null || !total.isNumber() || total.decimalValue().signum()<0 || total.decimalValue().compareTo(BigDecimal.valueOf(1000000000))>0)
                    throw new ShippingProviderException("Carrier returned an invalid shipping fee");
                if (fee!=null && total.decimalValue().compareTo(fee)>0)
                    throw new ConflictException("The current Lalamove fee exceeds the paid shipping fee; cancel the unshipped order and checkout with a new quote");
                if (fee==null) fee=total.decimalValue();
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
            shipment=shipments.saveAndFlush(shipment);
        }
        return new Prepared(id,true);
    }
    private ShipmentResponse submit(Prepared prepared) {
        return new TransactionTemplate(transactions).execute(tx -> {
        var shipment=lock(prepared.id());
        if (!prepared.submit() || shipment.getOrderCode()!=null) return ShipmentResponse.from(shipment);
        Map<String,Object> payload=readMap(shipment.getPayload());
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
                applyLalamoveStatus(shipment,status,delivered!=null?delivered:Instant.now());
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
        shipment.setUpdatedAt(Instant.now()); shipments.save(shipment); return ShipmentResponse.from(shipment);
        });
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
    @Transactional public ShipmentResponse cancel(UUID actor,boolean staff,UUID id) {
        var shipment=lock(id); if (!staff && !actor.equals(shipment.getSellerId())) throw new ForbiddenException("Only the seller can cancel this shipment");
        if (!staff && "BUYER_TO_SELLER".equals(shipment.getLeg())) throw new ForbiddenException("STAFF/ADMIN must cancel an approved return shipment");
        if ("CANCELLED".equals(shipment.getStatus())) return ShipmentResponse.from(shipment);
        boolean allowed="LALAMOVE".equals(shipment.getProvider()) && Set.of("ASSIGNING_DRIVER","ON_GOING").contains(shipment.getProviderStatus());
        if (!allowed || shipment.getOrderCode()==null) throw new ConflictException("Carrier pickup has started or creation is uncertain; cancellation requires reconciliation");
        carrier(shipment.getProvider()).cancel(shipment.getOrderCode()); shipment.setStatus("CANCELLED"); shipment.setProviderStatus("CANCELED"); shipment.setUpdatedAt(Instant.now());
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
        if(shipment.getOrderCode()==null) throw new ConflictException("Creation is uncertain; STAFF must reconcile the carrier order code");
        var data=carrier(shipment.getProvider()).detail(shipment.getOrderCode());
        if(!shipment.getOrderCode().equals(data.path("order_code").asText())) throw new ShippingProviderException("Lalamove detail does not match the shipment");
        reconcileLalamove(shipment,data);
        return ShipmentResponse.from(shipment);
    }

    @Transactional public void receiveLalamove(JsonNode body) {
        String code=body.path("order_code").asText();
        var found=shipments.findByProviderAndOrderCode("LALAMOVE",code);
        if (found.isEmpty()) throw new ShippingProviderException("Shipment is not linked yet; retry callback",503);
        var shipment=lock(found.get().getId());
        String key=ListingFingerprint.sha256("LALAMOVE|"+body.path("event_id").asText());
        if (events.existsByEventKey(key)) return;
        Instant time=parseProviderTime(body.get("occurred_at"));
        if (time==null) throw new BadRequestException("Lalamove event has no valid update time");
        String type=body.path("event_type").asText(),status=body.path("status").asText();
        if (!status.isBlank() && !LALAMOVE_STATUSES.contains(status)) throw new BadRequestException("Unknown Lalamove status");
        var event=new ShipmentEvent(); event.setShipmentId(shipment.getId()); event.setEventKey(key); event.setType(type);
        event.setStatus(status); event.setOccurredAt(time); event.setPayload(mapper.writeValueAsString(body)); events.save(event);
        if (shipment.getLastReconciledAt()!=null && !time.isAfter(shipment.getLastReconciledAt())) return;
        if (body.hasNonNull("replacement_order_code")) {
            String replacement=body.path("replacement_order_code").asText();
            var detail=carrier("LALAMOVE").detail(replacement);
            requireLalamoveReference(shipment,detail,replacement);
            shipment.setOrderCode(replacement); reconcileLalamove(shipment,detail); return;
        }
        var fee=body.get("fee");
        if (fee!=null && !fee.isNull() && (shipment.getLastFeeEventAt()==null || !time.isBefore(shipment.getLastFeeEventAt()))) {
            requireFee(fee); shipment.setActualFee(fee.decimalValue()); shipment.setLastFeeEventAt(time);
        }
        if (shipment.getLastEventAt()!=null && time.isBefore(shipment.getLastEventAt())) return;
        if (body.hasNonNull("driver_id")) shipment.setDriverId(body.path("driver_id").asText());
        if (body.hasNonNull("share_link") && body.path("share_link").asText().startsWith("https://")) shipment.setShareLink(body.path("share_link").asText());
        if (body.hasNonNull("pod_url") && body.path("pod_url").asText().startsWith("https://")) shipment.setPodUrl(body.path("pod_url").asText());
        if ("FAILED".equals(body.path("pod_status").asText())) freeze(shipment);
        if (!status.isBlank()) applyLalamoveStatus(shipment,status,time);
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
        return ShipmentResponse.from(shipment);
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
                    var visible=(tools.jackson.databind.node.ObjectNode)driver.deepCopy();
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
        applyLalamoveStatus(shipment,status,delivered!=null?delivered:observedAt);
        shipment.setLastReconciledAt(observedAt); shipment.setUpdatedAt(observedAt); shipment.setLastError(null); shipments.save(shipment);
        var event=new ShipmentEvent(); event.setShipmentId(shipment.getId()); event.setEventKey(ListingFingerprint.sha256("LALAMOVE|sync|"+shipment.getId()+"|"+observedAt));
        event.setType("RECONCILIATION"); event.setStatus(status); event.setOccurredAt(observedAt); event.setPayload(mapper.writeValueAsString(data)); events.save(event);
    }
    private void requireLalamoveReference(Shipment shipment,JsonNode data,String code) {
        if (!code.equals(data.path("order_code").asText()) || !shipment.getClientOrderCode().equals(data.path("raw").path("metadata").path("secondlifeClientOrderCode").asText()))
            throw new ConflictException("Carrier order does not contain this shipment's verified client reference");
    }
    private void applyLalamoveStatus(Shipment shipment,String status,Instant time) {
        if (Set.of("DELIVERED","CANCELLED").contains(shipment.getStatus()) && !Objects.equals(shipment.getProviderStatus(),status)) {
            if (Set.of("REJECTED","EXPIRED").contains(status)) freeze(shipment);
            return;
        }
        shipment.setProviderStatus(status); shipment.setStatus(canonicalLalamove(status)); shipment.setLastEventAt(time); shipment.setLastError(null);
        if ("COMPLETED".equals(status) && shipment.getDeliveredAt()==null) shipment.setDeliveredAt(time);
        if (Set.of("REJECTED","EXPIRED").contains(status)) freeze(shipment);
        if (shipment.getOrderId()==null) return;
        var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
        if (Set.of(OrderStatus.COMPLETED,OrderStatus.CANCELLED).contains(order.getStatus())) return;
        boolean toBuyer=Set.of("SELLER_TO_BUYER","CENTER_TO_BUYER").contains(shipment.getLeg());
        if (toBuyer && "PICKED_UP".equals(status) && order.getShippingDeliveredAt()==null) order.setStatus(OrderStatus.SHIPPED);
        if (toBuyer && "COMPLETED".equals(status)) { order.setStatus(OrderStatus.DELIVERED); if (order.getShippingDeliveredAt()==null) order.setShippingDeliveredAt(shipment.getDeliveredAt()); }
        orders.save(order);
    }
    private void freeze(Shipment shipment) {
        if (shipment.getOrderId()!=null) {
            var order=orders.findByIdForUpdate(shipment.getOrderId()).orElseThrow();
            if (order.getEscrowStatus()==EscrowStatus.HELD) { order.setEscrowStatus(EscrowStatus.FROZEN); orders.save(order); }
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
                return java.time.OffsetDateTime.parse(text.contains(" ") ? text.replace(" ", "T") : text).toInstant();
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
