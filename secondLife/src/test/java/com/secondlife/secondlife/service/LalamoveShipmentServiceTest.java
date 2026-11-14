package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.ShipmentMapper;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.ListingFingerprintImpl;
import com.secondlife.secondlife.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LalamoveShipmentServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ShipmentRepository shipments=mock(ShipmentRepository.class);
    private final ShipmentEventRepository events=mock(ShipmentEventRepository.class);
    private final OrderRepository orders=mock(OrderRepository.class);
    private final InspectionOrderRepository inspections=mock(InspectionOrderRepository.class);
    private final ShippingQuoteRepository quotes=mock(ShippingQuoteRepository.class);
    private final ShippingQuoteService quoteService=mock(ShippingQuoteService.class);
    private final ShippingProvider router=mock(ShippingProvider.class), lala=mock(ShippingProvider.class), ghn=mock(ShippingProvider.class);
    private final WalletService wallets=mock(WalletService.class);
    private final InspectionFeeSettlementService inspectionFeeSettlement = mock(InspectionFeeSettlementService.class);
    private final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    private final UUID seller=UUID.randomUUID(), buyer=UUID.randomUUID();
    private ShipmentService service;
    private Order order;
    private ShippingQuote quote;
    private final Map<UUID,Shipment> rows=new HashMap<>();
    @BeforeEach void setup() {
        when(router.code()).thenReturn("LALAMOVE");
        when(router.forProvider("LALAMOVE")).thenReturn(lala);
        when(router.forProvider("GHN")).thenReturn(ghn);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(shipments.findById(any())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        when(shipments.saveAndFlush(any())).thenAnswer(i -> { Shipment s=i.getArgument(0); rows.put(s.getId(),s); return s; });
        when(shipments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(shipments.findByOrderIdOrderByCreatedAtAsc(any())).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(shipments.findByProviderAndOrderCode(any(),any())).thenAnswer(i -> rows.values().stream().filter(s -> i.getArgument(0).equals(s.getProvider()) && i.getArgument(1).equals(s.getOrderCode())).findFirst());
        order=new Order(); order.setId(UUID.randomUUID()); order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD);
        User su=new User(); su.setId(seller); User bu=new User(); bu.setId(buyer); order.setSeller(su); order.setBuyer(bu);
        Post post=new Post(); post.setId(UUID.randomUUID()); post.setTitle("Product"); order.setPost(post); order.setFinalPrice(new BigDecimal("1000000"));
        quote=new ShippingQuote(); quote.setId(UUID.randomUUID()); quote.setProvider("LALAMOVE"); quote.setLeg("SELLER_TO_BUYER"); quote.setFee(new BigDecimal("27000"));
        var addr=new ShippingAddress("Seller", "0912345678", "123 Street", new BigDecimal("10.77"), new BigDecimal("106.69"));
        var payload=ShippingPayloads.create(addr,addr,new ShippingParcel(600,20,15,10),"Product",order.getFinalPrice());
        payload.put("provider_quote",Map.of("quotationId","Q1","expiresAt",Instant.now().plusSeconds(300).toString()));
        quote.setPayload(mapper.writeValueAsString(payload)); quote.setSellerPickupAddress(mapper.writeValueAsString(addr)); order.setDeliveryAddress(mapper.writeValueAsString(addr)); order.setShippingQuoteId(quote.getId());
        when(quotes.findById(quote.getId())).thenReturn(Optional.of(quote)); when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(lala.preview(anyMap())).thenAnswer(invocation -> mapper.valueToTree(Map.of("total_fee",27000,
            "provider_quote",Map.of("quotationId","Q-FRESH","serviceType","MOTORCYCLE",
                "scheduleAt",Instant.now().toString(),"expiresAt",Instant.now().plusSeconds(300).toString()))));
        when(lala.create(anyMap())).thenReturn(mapper.valueToTree(Map.of("order_code","1234567890123456789","status","ASSIGNING_DRIVER","total_fee",27000,"share_link","https://web.lalamove.com/track/example")));
        service = new ShipmentServiceImpl(shipments, events, orders, inspections, quotes, quoteService,
                mock(ReverseGeocodingService.class), router, mapper, wallets, transactions,
                mock(SystemWalletService.class), mock(SellerReputationService.class), null, null, new ListingFingerprintImpl(),
                MapperTestSupport.mapper(ShipmentMapper.class), mock(OrderDisputeService.class), mock(ReturnShippingFeeService.class), mock(InspectionCenterProfileService.class), mock(InspectionShippingQuoteService.class), inspectionFeeSettlement, true);
    }
    private CreateShipmentRequest request(UUID id) { return new CreateShipmentRequest(id,"SELLER_TO_BUYER",null,null,null); }
    private Shipment booked() { service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID())); return rows.values().iterator().next(); }
    private void event(Shipment s,String eventId,String status,Instant time) {
        service.receiveLalamove(mapper.valueToTree(Map.of("event_id",eventId,"event_type","ORDER_STATUS_CHANGED","order_code",s.getOrderCode(),"occurred_at",time.toString(),"status",status)));
    }
    @Test void onlyAssignedCenterCanBookPaidBuyerDeliveryAndReplayIsSafe() {
        UUID centerId = UUID.randomUUID();
        User center = new User();
        center.setId(centerId);
        InspectionOrder inspection = new InspectionOrder();
        inspection.setId(UUID.randomUUID());
        inspection.setInspectionCenter(center);
        inspection.setOrderType(InspectionOrderType.AT_CENTER);
        inspection.setStatus("DELIVERING_TO_BUYER");
        inspection.setOrder(order);
        quote.setLeg("CENTER_TO_BUYER");
        var route = (tools.jackson.databind.node.ObjectNode) mapper.readTree(quote.getPayload());
        route.put("from_name", "Hub Lab");
        route.put("from_phone", "0922222222");
        route.put("from_address", "Hub pickup");
        route.put("to_name", "Buyer");
        route.put("to_phone", "0933333333");
        route.put("to_address", "Buyer delivery");
        quote.setPayload(mapper.writeValueAsString(route));
        when(inspections.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        var request = new CreateShipmentRequest(UUID.randomUUID(), "CENTER_TO_BUYER", null, null, null);

        assertThrows(ForbiddenException.class, () -> service.createCenterDelivery(UUID.randomUUID(), inspection.getId(), request));
        assertThrows(ConflictException.class, () -> service.createCenterDelivery(centerId, inspection.getId(), request));
        verify(lala, never()).create(anyMap());

        inspection.setFinalPaymentPaidAt(Instant.now());
        var first = service.createCenterDelivery(centerId, inspection.getId(), request);
        var replay = service.createCenterDelivery(centerId, inspection.getId(), request);
        assertEquals(first.shipmentId(), replay.shipmentId());
        assertEquals("CENTER_TO_BUYER", first.leg());
        assertEquals(inspection.getId(), first.inspectionOrderId());
        assertEquals(first.shipmentId(), first.id());
        assertEquals("Hub Lab", first.senderName());
        assertEquals("0922222222", first.senderPhone());
        assertEquals("Hub pickup", first.senderAddress());
        assertEquals("Buyer", first.receiverName());
        assertEquals("0933333333", first.receiverPhone());
        assertEquals("Buyer delivery", first.receiverAddress());
        assertEquals(first.shareLink(), first.trackingUrl());
        assertEquals(first, replay);
        verify(lala, times(1)).create(anyMap());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assignedCenterCanReturnFailedGoodsWithoutClosingAppealWindow(boolean cashShipping) {
        User center = new User();
        center.setId(UUID.randomUUID());
        InspectionOrder inspection = new InspectionOrder();
        inspection.setId(UUID.randomUUID());
        inspection.setInspectionCenter(center);
        inspection.setSeller(order.getSeller());
        inspection.setBuyer(order.getBuyer());
        inspection.setPost(order.getPost());
        order.getPost().setPrice(order.getFinalPrice());
        inspection.setOrderType(InspectionOrderType.AT_CENTER);
        inspection.setStatus("FAILED");
        inspection.setCashShippingSimulation(cashShipping);
        inspection.setFeeSettledAt(Instant.now());
        inspection.setReturnShippingReserveFee(new BigDecimal("36000"));
        Instant appealDeadline = Instant.now().plusSeconds(3600);
        inspection.setAppealDeadline(appealDeadline);

        Shipment inbound = new Shipment();
        inbound.setLeg("SELLER_TO_CENTER");
        inbound.setStatus("DELIVERED");
        inbound.setPayload(quote.getPayload());
        when(inspections.findByIdForUpdate(inspection.getId())).thenReturn(Optional.of(inspection));
        var request = new CreateShipmentRequest(UUID.randomUUID(), "CENTER_TO_SELLER", null, null, null);
        when(inspections.findOwnerId(inspection.getId())).thenReturn(Optional.of(seller));

        assertThrows(ForbiddenException.class, () -> service.createForInspection(UUID.randomUUID(), true, inspection.getId(), request));

        assertThrows(ForbiddenException.class, () -> service.createCenterDelivery(UUID.randomUUID(), inspection.getId(), request));
        assertThrows(ConflictException.class, () -> service.createCenterDelivery(center.getId(), inspection.getId(), request));
        verify(lala, never()).create(anyMap());

        when(shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspection.getId())).thenReturn(List.of(inbound));
        when(inspections.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        var route = (tools.jackson.databind.node.ObjectNode) mapper.readTree(inbound.getPayload());
        route.put("to_address", "Inspection Hub");
        inbound.setPayload(mapper.writeValueAsString(route));

        assertThrows(ForbiddenException.class, () -> service.quoteCenterReturn(UUID.randomUUID(), inspection.getId()));
        var returnQuote = service.quoteCenterReturn(center.getId(), inspection.getId());
        assertEquals("Inspection Hub", returnQuote.from().address());
        assertEquals("123 Street", returnQuote.to().address());
        assertEquals(new BigDecimal("27000"), returnQuote.shippingFee());
        assertEquals(new BigDecimal("36000"), returnQuote.reserveFee());
        assertEquals("MOTORCYCLE", returnQuote.serviceType());
        assertTrue(rows.isEmpty());
        verify(lala, never()).create(anyMap());

        var priceIncreased = new CreateShipmentRequest(UUID.randomUUID(), "CENTER_TO_SELLER",
                null, null, null, null, new BigDecimal("26000"));
        assertThrows(ConflictException.class,
                () -> service.createCenterDelivery(center.getId(), inspection.getId(), priceIncreased));
        assertTrue(rows.isEmpty());
        verify(lala, never()).create(anyMap());

        var result = service.createCenterDelivery(center.getId(), inspection.getId(), request);
        assertEquals("Inspection Hub", result.senderAddress());
        assertEquals("123 Street", result.receiverAddress());
        assertEquals(result.shipmentId(), service.createCenterDelivery(center.getId(), inspection.getId(), request).shipmentId());
        verify(lala, times(1)).create(anyMap());

        var completion = mapper.createObjectNode().put("event_id", "return-completed")
                .put("event_type", "ORDER_STATUS_CHANGED").put("order_code", result.orderCode())
                .put("occurred_at", Instant.now().plusSeconds(1).toString()).put("status", "COMPLETED");
        BigDecimal finalFee = new BigDecimal("32000");
        if (cashShipping) {
            when(lala.detail(result.orderCode())).thenReturn(mapper.valueToTree(Map.of(
                    "order_code", result.orderCode(), "status", "COMPLETED", "total_fee", finalFee)));
            when(inspectionFeeSettlement.reimburseDeliveredReturn(
                    eq(inspection), any(Shipment.class), eq(finalFee))).thenReturn(true);
        } else {
            completion.put("fee", finalFee);
        }
        service.receiveLalamove(completion);

        assertEquals("FAILED", inspection.getStatus());
        assertEquals(appealDeadline, inspection.getAppealDeadline());
        assertEquals("DELIVERED", rows.get(result.shipmentId()).getStatus());
        if (cashShipping) {
            assertEquals(center.getId(), result.cashPayerId());
            verify(lala).create(argThat(payload -> Boolean.TRUE.equals(payload.get("inspection_cash_simulation"))
                    && payload.get("content").toString().startsWith("Trung tâm kiểm định")));
            Shipment delivered = rows.get(result.shipmentId());
            assertNotNull(delivered.getCashConfirmedAt());
            assertNull(delivered.getCashConfirmedBy());
            assertEquals(finalFee, delivered.getCashReimbursedAmount());
            assertEquals("LALAMOVE_COMPLETED:" + result.orderCode(), delivered.getCashReceiptReference());

            service.sync(center.getId(), false, result.shipmentId());

            verify(inspectionFeeSettlement, times(1)).reimburseDeliveredReturn(inspection, delivered, finalFee);
        } else {
            assertNull(result.cashPayerId());
            verify(inspectionFeeSettlement).settleReturnShipping(inspection, finalFee);
        }
    }

    @Test void carrierIdentityPersistsAndReplayDoesNotCreateTwice() {
        var request=request(UUID.randomUUID());
        var first=service.createForOrder(seller,false,order.getId(),request);
        var second=service.createForOrder(seller,false,order.getId(),request);
        assertEquals(first.shipmentId(),second.shipmentId()); assertEquals("LALAMOVE",first.provider()); assertEquals("ASSIGNING_DRIVER",first.providerStatus());
        verify(lala,times(1)).create(anyMap()); verifyNoInteractions(wallets);
    }
    @Test void uncertainCreationNeverRepeatsMutation() {
        when(lala.create(anyMap())).thenThrow(new ShippingProviderException("Timeout"));
        var request=request(UUID.randomUUID());
        assertEquals("CREATION_UNCERTAIN",service.createForOrder(seller,false,order.getId(),request).status());
        assertEquals("CREATION_UNCERTAIN",service.createForOrder(seller,false,order.getId(),request).status());
        verify(lala,times(1)).create(anyMap()); verifyNoInteractions(wallets);
    }
    @Test void expiredPaidQuoteRejectsHigherFeeWithoutChargingOrCreating() {
        var payload=mapper.readTree(quote.getPayload()); ((tools.jackson.databind.node.ObjectNode)payload.path("provider_quote")).put("expiresAt",Instant.now().minusSeconds(1).toString()); quote.setPayload(mapper.writeValueAsString(payload));
        when(lala.preview(anyMap())).thenReturn(mapper.valueToTree(Map.of("total_fee",30000,"provider_quote",Map.of("quotationId","Q2","expiresAt",Instant.now().plusSeconds(300).toString()))));
        assertThrows(ConflictException.class,() -> service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID())));
        verify(lala,never()).create(anyMap()); verifyNoInteractions(wallets); assertTrue(rows.isEmpty());
    }
    @Test void pastPickupScheduleWithUnexpiredQuoteIsRefreshedBeforeBookingAndReplayIsIdempotent() {
        var original=mapper.readTree(quote.getPayload());
        ((tools.jackson.databind.node.ObjectNode)original.path("provider_quote")).put("scheduleAt",Instant.now().minusSeconds(2040).toString());
        ((tools.jackson.databind.node.ObjectNode)original.path("provider_quote")).put("expiresAt",Instant.now().plusSeconds(960).toString());
        quote.setPayload(mapper.writeValueAsString(original));String paidSnapshot=quote.getPayload();
        var request=request(UUID.randomUUID());var result=service.createForOrder(seller,false,order.getId(),request);
        org.mockito.ArgumentCaptor<Map<String,Object>> captured=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(lala).create(captured.capture());
        assertEquals("Q-FRESH",mapper.valueToTree(captured.getValue().get("provider_quote")).path("quotationId").asText());
        assertEquals("Q-FRESH",mapper.readTree(rows.get(result.shipmentId()).getPayload()).path("provider_quote").path("quotationId").asText());
        assertEquals(paidSnapshot,quote.getPayload());assertEquals(new BigDecimal("27000"),result.quotedFee());
        assertEquals(result.shipmentId(),service.createForOrder(seller,false,order.getId(),request).shipmentId());
        verify(lala,times(1)).preview(anyMap());verify(lala,times(1)).create(anyMap());verifyNoInteractions(wallets);
    }
    @Test void higherFreshFeeIsRejectedEvenWhenThePaidQuotationHasNotExpired() {
        when(lala.preview(anyMap())).thenReturn(mapper.valueToTree(Map.of("total_fee",30000,
            "provider_quote",Map.of("quotationId","Q-EXPENSIVE","expiresAt",Instant.now().plusSeconds(300).toString()))));
        assertThrows(ConflictException.class,()->service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID())));
        verify(lala,never()).create(anyMap());verifyNoInteractions(wallets);assertTrue(rows.isEmpty());
        assertEquals(EscrowStatus.HELD,order.getEscrowStatus());assertEquals(new BigDecimal("27000"),quote.getFee());
    }
    @Test void pickedAndCompletedUpdateBusinessOrderWithoutSettlement() {
        var s=booked(); var time=Instant.now(); event(s,"a","ON_GOING",time); assertEquals(OrderStatus.PROCESSING,order.getStatus());
        event(s,"b","PICKED_UP",time.plusSeconds(1)); assertEquals(OrderStatus.SHIPPED,order.getStatus());
        event(s,"c","COMPLETED",time.plusSeconds(2)); assertEquals("DELIVERED",s.getStatus()); assertEquals(OrderStatus.DELIVERED,order.getStatus());
        assertEquals(EscrowStatus.HELD,order.getEscrowStatus()); verifyNoInteractions(wallets);
        event(s,"old","ASSIGNING_DRIVER",time.minusSeconds(1)); assertEquals("COMPLETED",s.getProviderStatus());
    }
    @Test void repeatedEventIdIsIdempotentAndCanceledCannotReopen() {
        var s=booked(); var time=Instant.now(); event(s,"a","CANCELED",time);
        event(s,"b","PICKED_UP",time.plusSeconds(1)); assertEquals("CANCELLED",s.getStatus()); assertEquals(OrderStatus.PROCESSING,order.getStatus());
        when(events.existsByEventKey(anyString())).thenReturn(true); event(s,"a","CANCELED",time);
        verify(events,times(2)).save(any()); verifyNoInteractions(wallets);
    }
    @Test void rejectedShipmentDoesNotPretendGoodsReturnedOrRefund() {
        var s=booked(); event(s,"a","PICKED_UP",Instant.now()); event(s,"b","REJECTED",Instant.now().plusSeconds(1));
        assertEquals("FAILED",s.getStatus()); assertEquals(EscrowStatus.FROZEN,order.getEscrowStatus());
        assertThrows(ConflictException.class,() -> service.refundReturned(seller,true,s.getId(),"not physically returned")); verifyNoInteractions(wallets);
    }
    @Test void completedTrackingDoesNotFetchUnavailableDriverLocation() {
        var s=booked(); event(s,"a","COMPLETED",Instant.now()); s.setDriverId("D1");
        var tracking=service.tracking(buyer,false,s.getId()); assertEquals("UNAVAILABLE",tracking.path("locationStatus").asText());
        verify(lala,never()).driver(anyString(),anyString());
        assertThrows(ForbiddenException.class,() -> service.tracking(UUID.randomUUID(),false,s.getId()));
    }
    @Test void sandboxPlaceholderDoesNotBecomeAMapLocationAndDriverContactsArePreserved() {
        var s=booked();event(s,"picked","PICKED_UP",Instant.now());s.setDriverId("80042");
        var driver=mapper.valueToTree(Map.of("driverId","80042","name","TestDriver","phone","+8411155111",
            "coordinates",Map.of("lat","0.0000000","lng","0.0000000","updatedAt","1970-01-01T00:00:00.00Z")));
        when(lala.driver(s.getOrderCode(),"80042")).thenReturn(driver);
        var result=service.tracking(buyer,false,s.getId());
        assertEquals("PICKED",result.path("status").asText());assertEquals("PICKED_UP",result.path("providerStatus").asText());
        assertEquals("UNAVAILABLE",result.path("locationStatus").asText());
        assertTrue(result.path("driver").path("coordinates").isNull());
        assertEquals("80042",result.path("driver").path("driverId").asText());
        assertEquals("TestDriver",result.path("driver").path("name").asText());
        assertEquals("+8411155111",result.path("driver").path("phone").asText());
        assertEquals("0.0000000",driver.path("coordinates").path("lat").asText());
    }
    @Test void malformedOutOfRangeZeroOrUndatedCoordinatesAreUnavailable() {
        var s=booked();event(s,"picked","PICKED_UP",Instant.now());s.setDriverId("D1");
        var current=Instant.now().minusSeconds(1).toString();
        for(var coordinates:List.of(
            Map.of("lat","0","lng","0","updatedAt",current),
            Map.of("lat","91","lng","106.7","updatedAt",current),
            Map.of("lat","10.77","lng","181","updatedAt",current),
            Map.of("lat","NaN","lng","106.7","updatedAt",current),
            Map.of("lat","10.77","lng","106.7"),
            Map.of("lat","10.77","lng","106.7","updatedAt","1970-01-01T00:00:00Z"),
            Map.of("lat","10.77","lng","106.7","updatedAt","not-a-time"),
            Map.of("lat","10.77","lng","106.7","updatedAt",Instant.now().plusSeconds(3600).toString()))) {
            when(lala.driver(s.getOrderCode(),"D1")).thenReturn(mapper.valueToTree(Map.of("driverId","D1","coordinates",coordinates)));
            var result=service.tracking(buyer,false,s.getId());
            assertEquals("UNAVAILABLE",result.path("locationStatus").asText());assertTrue(result.path("driver").path("coordinates").isNull());
        }
    }
    @Test void validCoordinatesAreAvailableIncludingASingleZeroCoordinate() {
        var s=booked();event(s,"picked","PICKED_UP",Instant.now());s.setDriverId("D1");
        for(var coordinates:List.of(
            Map.of("lat","10.77","lng","106.7","updatedAt",Instant.now().minusSeconds(1).toString()),
            Map.of("lat","0","lng","106.7","updatedAt",Instant.now().minusSeconds(1).toString()))) {
            when(lala.driver(s.getOrderCode(),"D1")).thenReturn(mapper.valueToTree(Map.of("driverId","D1","coordinates",coordinates)));
            var result=service.tracking(buyer,false,s.getId());
            assertEquals("AVAILABLE",result.path("locationStatus").asText());
            assertEquals(mapper.valueToTree(coordinates),result.path("driver").path("coordinates"));
        }
    }
    @Test void creationIntentCommitsBeforeCallingCarrier() {
        when(lala.create(anyMap())).thenAnswer(i -> { verify(transactions,atLeastOnce()).commit(any()); return mapper.valueToTree(Map.of("order_code","1234567890123456789","status","ASSIGNING_DRIVER","total_fee",27000)); });
        booked();
    }
    @Test void alreadyCompletedCreateResponseUpdatesBusinessOrderWithoutSettlement() {
        when(lala.create(anyMap())).thenReturn(mapper.valueToTree(Map.of("order_code","1234567890123456789","status","COMPLETED","total_fee",27000)));
        var shipment=booked(); assertEquals("DELIVERED",shipment.getStatus()); assertEquals(OrderStatus.DELIVERED,order.getStatus());
        assertNotNull(order.getShippingDeliveredAt()); assertEquals(EscrowStatus.HELD,order.getEscrowStatus()); verifyNoInteractions(wallets);
    }
    @Test void knownRejectedOrLocallyInvalidBookingIsSafeToCancelWithoutLosingShippingFee() {
        when(lala.create(anyMap())).thenThrow(new BadRequestException("Phone invalid"));
        var s=booked(); assertEquals("CANCELLED",s.getStatus()); assertNull(s.getOrderCode());
        rows.clear(); doThrow(new ShippingProviderException("Authentication rejected",401)).when(lala).create(anyMap());
        s=booked(); assertEquals("CANCELLED",s.getStatus()); assertNull(s.getOrderCode()); verifyNoInteractions(wallets);
    }
    @Test void invalidRecipientReturnsActionableErrorAndKeepsBuyerFundsHeld() {
        when(lala.create(anyMap())).thenThrow(new BadRequestException("Buyer delivery phone number must be a valid Vietnamese number"));
        var request=request(UUID.randomUUID());
        var first=service.createForOrder(seller,false,order.getId(),request);
        assertEquals("CANCELLED",first.status());assertNull(first.orderCode());
        assertTrue(first.lastError().contains("Buyer delivery phone number"));
        assertTrue(first.lastError().contains("fresh requestId"));
        var replay=service.createForOrder(seller,false,order.getId(),request);
        assertEquals(first.shipmentId(),replay.shipmentId());assertEquals(first.lastError(),replay.lastError());
        assertEquals(OrderStatus.PROCESSING,order.getStatus());assertEquals(EscrowStatus.HELD,order.getEscrowStatus());
        verify(lala,times(1)).create(anyMap());verifyNoInteractions(wallets);
    }
    @Test void unexpectedLocalErrorTextIsNotExposedInShipmentResponse() {
        when(lala.create(anyMap())).thenThrow(new BadRequestException("private-sensitive-marker"));
        var result=service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID()));
        assertEquals("CANCELLED",result.status());assertFalse(result.lastError().contains("private-sensitive-marker"));
    }
    @Test void rejectedBookingIncludesHttpStatusWithoutExposingRawExceptionMessage() {
        when(lala.create(anyMap())).thenThrow(new ShippingProviderException("private-sensitive-marker",422));
        var result=service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID()));
        assertEquals("CANCELLED",result.status());assertTrue(result.lastError().contains("HTTP 422"));
        assertFalse(result.lastError().contains("private-sensitive-marker"));verifyNoInteractions(wallets);
    }
    @Test void structuredCarrierRejectionPreservesDiagnosticCodeAndFieldInTheShipment() {
        when(lala.create(anyMap())).thenThrow(new LalamoveRequestException(422,"ERR_INVALID_FIELD","sender.phone"));
        var result=service.createForOrder(seller,false,order.getId(),request(UUID.randomUUID()));
        assertEquals("CANCELLED",result.status());assertNull(result.orderCode());
        assertTrue(result.lastError().contains("HTTP 422"));assertTrue(result.lastError().contains("ERR_INVALID_FIELD"));
        assertTrue(result.lastError().contains("sender.phone"));assertTrue(result.lastError().length()<=255);
        assertEquals(EscrowStatus.HELD,order.getEscrowStatus());verifyNoInteractions(wallets);
    }
    @Test void insufficientCarrierCreditIsAKnownRejectionAndDoesNotMakeAnotherBookingOnReplay() {
        when(lala.create(anyMap())).thenThrow(new ShippingProviderException("private-sensitive-marker",402));
        var request=request(UUID.randomUUID());var first=service.createForOrder(seller,false,order.getId(),request);
        assertEquals("CANCELLED",first.status());assertTrue(first.lastError().contains("HTTP 402"));
        assertEquals(first.shipmentId(),service.createForOrder(seller,false,order.getId(),request).shipmentId());
        assertEquals(OrderStatus.PROCESSING,order.getStatus());assertEquals(EscrowStatus.HELD,order.getEscrowStatus());
        verify(lala,times(1)).create(anyMap());verifyNoInteractions(wallets);
    }
    @Test void uncertainBookingCanOnlyBeRecoveredFromMatchingAuthenticatedMetadata() {
        when(lala.create(anyMap())).thenThrow(new ShippingProviderException("Timeout")); var shipment=booked();
        when(lala.detail("EXISTING")).thenReturn(mapper.valueToTree(Map.of("order_code","EXISTING","status","PICKED_UP","total_fee",27000,
                "raw",Map.of("metadata",Map.of("secondlifeClientOrderCode",shipment.getClientOrderCode())))));
        var recovered=service.reconcileCreation(seller,true,shipment.getId(),"EXISTING"); assertEquals("EXISTING",recovered.orderCode()); assertEquals(OrderStatus.SHIPPED,order.getStatus());
        verify(lala,times(1)).create(anyMap()); verifyNoInteractions(wallets);
    }
}
