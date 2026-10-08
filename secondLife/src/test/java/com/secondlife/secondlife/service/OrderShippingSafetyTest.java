package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderShippingSafetyTest {
    @Test void buyerCannotReleaseMoneyBeforeCarrierConfirmsDelivery() {
        OrderRepository orders = mock(OrderRepository.class);
        var wallets = mock(WalletService.class);
        var buyer = new User(); buyer.setId(UUID.randomUUID());
        var seller = new User(); seller.setId(UUID.randomUUID());
        var order = new Order(); order.setId(UUID.randomUUID()); order.setBuyer(buyer); order.setSeller(seller);
        order.setFinalPrice(BigDecimal.valueOf(100000)); order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD);
        var post = new Post(); post.setId(UUID.randomUUID()); post.setTitle("Product"); order.setPost(post);
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(i -> i.getArgument(0));
        var service = new OrderServiceImpl(orders, mock(PostRepository.class), mock(UserRepository.class), mock(NegotiationRepository.class), wallets,
                mock(com.secondlife.secondlife.service.shipping.ShippingQuoteService.class),mock(ShipmentRepository.class),
                mock(CommissionService.class), mock(SettlementService.class));
        assertThrows(ConflictException.class, () -> service.confirmDelivery(buyer.getId(), order.getId()));
        verifyNoInteractions(wallets);
    }

    @Test void getSellerOrdersWithMalformedSortFallsBackGracefully() {
        OrderRepository orders = mock(OrderRepository.class);
        var sellerId = UUID.randomUUID();
        when(orders.findBySellerId(eq(sellerId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        var service = new OrderServiceImpl(orders, mock(PostRepository.class), mock(UserRepository.class), mock(NegotiationRepository.class),
                mock(WalletService.class), mock(com.secondlife.secondlife.service.shipping.ShippingQuoteService.class), mock(ShipmentRepository.class),
                mock(CommissionService.class), mock(SettlementService.class));

        // When client sends sort=[""]
        org.springframework.data.domain.Pageable malformed = org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("[\"\"]"));
        var result = service.getSellerOrders(sellerId, malformed);

        assertNotNull(result);
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orders).findBySellerId(eq(sellerId), captor.capture());
        assertEquals("createdAt: DESC", captor.getValue().getSort().toString());
    }
}
