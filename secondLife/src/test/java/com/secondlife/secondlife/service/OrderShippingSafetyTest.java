package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.mapper.OrderMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import com.secondlife.secondlife.mapper.MapperTestSupport;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderShippingSafetyTest {
    @Test void buyerConfirmationRecordsDeliveryAndSettlesHeldEscrow() {
        OrderRepository orders = mock(OrderRepository.class);
        var wallets = mock(WalletService.class);
        var settlements = mock(SettlementService.class);
        var buyer = new User(); buyer.setId(UUID.randomUUID());
        var seller = new User(); seller.setId(UUID.randomUUID());
        var order = new Order(); order.setId(UUID.randomUUID()); order.setBuyer(buyer); order.setSeller(seller);
        order.setFinalPrice(BigDecimal.valueOf(100000)); order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD);
        var post = new Post(); post.setId(UUID.randomUUID()); post.setTitle("Product"); order.setPost(post);
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        var service = new OrderServiceImpl(orders, mock(PostRepository.class), mock(UserRepository.class), mock(NegotiationRepository.class), wallets,
                mock(com.secondlife.secondlife.service.ShippingQuoteService.class),mock(ShipmentRepository.class),
                mock(CommissionService.class), settlements, mock(SystemWalletService.class), mock(SellerReputationService.class), MapperTestSupport.mapper(OrderMapper.class));

        service.confirmDelivery(buyer.getId(), order.getId());

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertNotNull(order.getShippingDeliveredAt());
        var calls = inOrder(orders, settlements);
        calls.verify(orders).saveAndFlush(order);
        calls.verify(settlements).settle(order);
    }

    @Test void getSellerOrdersWithMalformedSortFallsBackGracefully() {
        OrderRepository orders = mock(OrderRepository.class);
        var sellerId = UUID.randomUUID();
        when(orders.findBySellerId(eq(sellerId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        var service = new OrderServiceImpl(orders, mock(PostRepository.class), mock(UserRepository.class), mock(NegotiationRepository.class),
                mock(WalletService.class), mock(com.secondlife.secondlife.service.ShippingQuoteService.class), mock(ShipmentRepository.class),
                mock(CommissionService.class), mock(SettlementService.class), mock(SystemWalletService.class), mock(SellerReputationService.class), MapperTestSupport.mapper(OrderMapper.class));

        // When client sends sort=[""]
        org.springframework.data.domain.Pageable malformed = org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("[\"\"]"));
        var result = service.getSellerOrders(sellerId, malformed);

        assertNotNull(result);
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orders).findBySellerId(eq(sellerId), captor.capture());
        assertEquals("createdAt: DESC", captor.getValue().getSort().toString());
    }

    @Test void cancelOrderRejectsPaidAtCenterInspectionOrderToPreventOrphanedHubItem() {
        OrderRepository orders = mock(OrderRepository.class);
        PostRepository posts = mock(PostRepository.class);
        InspectionOrderRepository inspections = mock(InspectionOrderRepository.class);
        var buyer = new User(); buyer.setId(UUID.randomUUID());
        var seller = new User(); seller.setId(UUID.randomUUID());
        var post = new Post(); post.setId(UUID.randomUUID()); post.setTitle("Máy lọc không khí");
        var order = new Order();
        order.setId(UUID.randomUUID());
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setPost(post);
        order.setPaymentMethod(OrderPaymentMethod.WALLET);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setEscrowStatus(EscrowStatus.HELD);
        order.setFinalPrice(BigDecimal.valueOf(2000000));

        var linkedInspection = new InspectionOrder();
        linkedInspection.setId(UUID.randomUUID());
        linkedInspection.setOrderType(InspectionOrderType.AT_CENTER);
        linkedInspection.setFinalPaymentPaidAt(java.time.Instant.now());

        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(posts.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspections.findByOrderId(order.getId())).thenReturn(Optional.of(linkedInspection));

        var service = new OrderServiceImpl(orders, posts, mock(UserRepository.class), mock(NegotiationRepository.class),
                mock(WalletService.class), mock(com.secondlife.secondlife.service.ShippingQuoteService.class), mock(ShipmentRepository.class),
                mock(CommissionService.class), mock(SettlementService.class), mock(SystemWalletService.class), mock(SellerReputationService.class), MapperTestSupport.mapper(OrderMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "inspectionOrderRepository", inspections);

        assertThrows(com.secondlife.secondlife.exception.ConflictException.class,
                () -> service.cancelOrder(buyer.getId(), order.getId()));
        verify(posts, never()).save(any());
    }
}
