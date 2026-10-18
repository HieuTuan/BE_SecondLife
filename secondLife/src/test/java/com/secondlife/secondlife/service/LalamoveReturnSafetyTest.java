package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.OrderMapper;
import com.secondlife.secondlife.mapper.ShipmentMapper;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LalamoveReturnSafetyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ShippingAddress sellerPickup = address("Seller", "123 Seller Street", "10.775600", "106.701900");
    private final ShippingAddress buyerDelivery = address("Buyer", "456 Buyer Street", "10.762622", "106.660172");
    private final ShippingParcel parcel = new ShippingParcel(12000, 120, 60, 40);

    @Test void storedCoordinateRoutePreservesAddressesAndNullAdministrativeMetadata() {
        var payload = ShippingPayloads.create(sellerPickup, buyerDelivery, parcel, "Furniture", new BigDecimal("1000000"));
        var snapshot = mapper.readTree(mapper.writeValueAsString(payload));

        var restoredSeller = ShippingPayloads.storedAddress(snapshot, "from");
        var restoredBuyer = ShippingPayloads.storedAddress(snapshot, "to");

        assertEquals(sellerPickup, restoredSeller);
        assertEquals(buyerDelivery, restoredBuyer);
        assertEquals(new BigDecimal("10.7756"), restoredSeller.latitude());
        assertEquals(new BigDecimal("106.660172"), restoredBuyer.longitude());
    }

    @Test void approvedCoordinateReturnReplaysOnceAndPreservesPaidVehicleSelection() {
        var shipments = mock(ShipmentRepository.class);
        var orders = mock(OrderRepository.class);
        var quotes = mock(ShippingQuoteRepository.class);
        var router = mock(ShippingProvider.class);
        var lalamove = mock(ShippingProvider.class);
        var wallets = mock(WalletService.class);
        var transactions = mock(PlatformTransactionManager.class);
        var rows = new HashMap<UUID, Shipment>();
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(router.forProvider("LALAMOVE")).thenReturn(lalamove);
        when(shipments.findById(any())).thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0))));
        when(shipments.findByOrderIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> new ArrayList<>(rows.values()));
        when(shipments.saveAndFlush(any())).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            rows.put(shipment.getId(), shipment);
            return shipment;
        });
        when(shipments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var order = order();
        order.setStatus(OrderStatus.DELIVERED);
        order.setShippingDeliveredAt(Instant.now().minusSeconds(300));
        order.setDeliveryAddress(mapper.writeValueAsString(buyerDelivery));
        var quote = new ShippingQuote();
        quote.setId(UUID.randomUUID());
        quote.setProvider("LALAMOVE");
        quote.setLeg("SELLER_TO_BUYER");
        quote.setFee(new BigDecimal("27000"));
        quote.setSellerPickupAddress(mapper.writeValueAsString(sellerPickup));
        var paidPayload = ShippingPayloads.create(sellerPickup, buyerDelivery, parcel, "Furniture", order.getFinalPrice());
        paidPayload.put("service_type", "VAN");
        quote.setPayload(mapper.writeValueAsString(paidPayload));
        order.setShippingQuoteId(quote.getId());
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(quotes.findById(quote.getId())).thenReturn(Optional.of(quote));
        when(lalamove.preview(anyMap())).thenReturn(mapper.valueToTree(Map.of(
                "total_fee", 28000, "provider_quote", Map.of("quotationId", "return-quote",
                "expiresAt", Instant.now().plusSeconds(300).toString()))));
        when(lalamove.create(anyMap())).thenReturn(mapper.valueToTree(Map.of(
                "order_code", "1234567890123456789", "status", "ASSIGNING_DRIVER", "total_fee", 28000)));
        var service = new com.secondlife.secondlife.service.impl.ShipmentServiceImpl(shipments, mock(ShipmentEventRepository.class), orders,
                mock(InspectionOrderRepository.class), quotes, mock(ShippingQuoteService.class), mock(com.secondlife.secondlife.service.ReverseGeocodingService.class), router,
                mapper, wallets, transactions, mock(SystemWalletService.class),mock(SellerReputationService.class),new com.secondlife.secondlife.service.impl.ListingFingerprintImpl(), MapperTestSupport.mapper(ShipmentMapper.class));
        var request = new CreateShipmentRequest(UUID.randomUUID(), "BUYER_TO_SELLER", null, null, "Approved dispute return");

        var first = service.createForOrder(UUID.randomUUID(), true, order.getId(), request);
        var replay = service.createForOrder(UUID.randomUUID(), true, order.getId(), request);

        assertEquals(first.shipmentId(), replay.shipmentId());
        assertEquals("BUYER_TO_SELLER", first.leg());
        assertEquals("LALAMOVE", first.provider());
        assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
        assertEquals(1, rows.size());
        var createPayload = ArgumentCaptor.forClass(Map.class);
        verify(lalamove, times(1)).create(createPayload.capture());
        verify(lalamove, times(1)).preview(anyMap());
        assertEquals("VAN", createPayload.getValue().get("service_type"));
        assertEquals("456 Buyer Street", createPayload.getValue().get("from_address"));
        assertEquals("123 Seller Street", createPayload.getValue().get("to_address"));
        var storedRoute = mapper.readTree(rows.get(first.shipmentId()).getPayload());
        assertEquals(buyerDelivery, ShippingPayloads.storedAddress(storedRoute, "from"));
        assertEquals(sellerPickup, ShippingPayloads.storedAddress(storedRoute, "to"));
        verifyNoInteractions(wallets);
    }

    @Test void cancelAfterPreflightFailureRefundsProductAndUnusedShipping() {
        assertCancellationRefund(null, new BigDecimal("1027000"));
    }

    @Test void cancelAfterActualCarrierBookingRetainsShippingFee() {
        assertCancellationRefund("1234567890123456789", new BigDecimal("1000000"));
    }

    private void assertCancellationRefund(String carrierOrderCode, BigDecimal expectedRefund) {
        var orders = mock(OrderRepository.class);
        var posts = mock(PostRepository.class);
        var shipments = mock(ShipmentRepository.class);
        var wallets = mock(WalletService.class);
        var order = order();
        var cancelled = new Shipment();
        cancelled.setId(UUID.randomUUID());
        cancelled.setProvider("LALAMOVE");
        cancelled.setOrderId(order.getId());
        cancelled.setStatus("CANCELLED");
        cancelled.setOrderCode(carrierOrderCode);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(posts.findByIdForUpdate(order.getPost().getId())).thenReturn(Optional.of(order.getPost()));
        when(shipments.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of(cancelled));
        var service = new OrderServiceImpl(orders, posts, mock(UserRepository.class), mock(NegotiationRepository.class),
                wallets, mock(ShippingQuoteService.class), shipments, mock(CommissionService.class), mock(SettlementService.class), mock(SystemWalletService.class), mock(SellerReputationService.class), MapperTestSupport.mapper(OrderMapper.class));

        var result = service.cancelOrder(order.getBuyer().getId(), order.getId());

        verify(wallets).processRefund(order.getBuyer().getId(), expectedRefund, order.getId());
        assertEquals(OrderStatus.CANCELLED, result.status());
        assertEquals(EscrowStatus.REFUNDED, result.escrowStatus());
        assertEquals("ACTIVE", order.getPost().getStatus());
    }

    private Order order() {
        var seller = new User(); seller.setId(UUID.randomUUID());
        var buyer = new User(); buyer.setId(UUID.randomUUID());
        var post = new Post(); post.setId(UUID.randomUUID()); post.setTitle("Furniture"); post.setStatus("RESERVED");
        var order = new Order(); order.setId(UUID.randomUUID()); order.setSeller(seller); order.setBuyer(buyer); order.setPost(post);
        order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD);
        order.setFinalPrice(new BigDecimal("1000000")); order.setShippingFee(new BigDecimal("27000"));
        return order;
    }

    private static ShippingAddress address(String name, String street, String latitude, String longitude) {
        return new ShippingAddress(name, "0912345678", street, new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
