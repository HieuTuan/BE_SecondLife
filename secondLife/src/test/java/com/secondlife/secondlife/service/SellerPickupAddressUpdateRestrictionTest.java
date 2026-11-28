package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
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
import com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerPickupAddressUpdateRestrictionTest {

    private PostRepository posts;
    private ShippingPickupAddressRepository addresses;
    private InspectionOrderRepository inspections;
    private ShipmentRepository shipments;
    private UserRepository users;
    private OrderRepository orders;
    private UserReportRepository reports;
    private ShippingQuoteServiceImpl service;

    private final UUID sellerId = UUID.randomUUID();
    private final ShippingAddress currentAddress = new ShippingAddress(
            "Shop A", "0901234567", "10 Nguyen Hue, Q1",
            new BigDecimal("10.7756"), new BigDecimal("106.7019"));
    private final ShippingAddress updatedAddress = new ShippingAddress(
            "Shop A", "0901234567", "20 Le Loi, Q1",
            new BigDecimal("10.7800"), new BigDecimal("106.7100"));

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        addresses = mock(ShippingPickupAddressRepository.class);
        inspections = mock(InspectionOrderRepository.class);
        shipments = mock(ShipmentRepository.class);
        users = mock(UserRepository.class);
        orders = mock(OrderRepository.class);
        reports = mock(UserReportRepository.class);
        var provider = mock(ShippingProvider.class);
        when(provider.code()).thenReturn("LALAMOVE");
        when(users.findByIdForRoleUpdate(sellerId)).thenReturn(Optional.of(new User()));

        service = new ShippingQuoteServiceImpl(
                posts,
                mock(NegotiationRepository.class),
                addresses,
                mock(ShippingQuoteRepository.class),
                inspections,
                shipments,
                provider,
                new ObjectMapper(),
                mock(ReverseGeocodingService.class),
                mock(ShippingVehicleSelectionMapper.class),
                users,
                mock(SellerOnboardingRepository.class),
                orders,
                reports);
    }

    @Test
    void blocksPickupChangeWhenSellerHasActiveOrderDispute() {
        when(reports.hasActiveSellerOrderDispute(sellerId)).thenReturn(true);

        var status = service.pickupUpdateStatus(sellerId);
        assertFalse(status.updatable());
        assertNotNull(status.reason());
        assertThrows(ConflictException.class, () -> service.savePickup(sellerId, updatedAddress));
    }

    @Test
    void blocksPickupChangeWhenSellerHasUnfinishedOrder() {
        when(orders.hasUnfinishedSellerOrder(sellerId)).thenReturn(true);

        assertFalse(service.pickupUpdateStatus(sellerId).updatable());
        assertThrows(ConflictException.class, () -> service.savePickup(sellerId, updatedAddress));
    }

    @Test
    void blocksPickupChangeWhenSellerHasActiveOrUnreturnedInspection() {
        when(inspections.hasActiveOrUnreturnedSellerInspection(sellerId)).thenReturn(true);

        assertFalse(service.pickupUpdateStatus(sellerId).updatable());
        assertThrows(ConflictException.class, () -> service.savePickup(sellerId, updatedAddress));
    }

    @Test
    void blocksPickupChangeWhenSellerHasInFlightShipment() {
        when(shipments.hasInFlightSellerShipment(sellerId)).thenReturn(true);

        assertFalse(service.pickupUpdateStatus(sellerId).updatable());
        assertThrows(ConflictException.class, () -> service.savePickup(sellerId, updatedAddress));
    }

    @Test
    void blocksPickupChangeWhenSellerHasReservedPost() {
        when(posts.countByUser_IdAndStatusIn(sellerId, List.of("RESERVED", "PAYMENT_PENDING"))).thenReturn(1L);

        assertFalse(service.pickupUpdateStatus(sellerId).updatable());
        assertThrows(ConflictException.class, () -> service.savePickup(sellerId, updatedAddress));
    }

    @Test
    void allowsIdempotentNoOpSaveWhenAddressIsUnchangedEvenIfOrderIsActive() {
        var existing = new ShippingPickupAddress();
        existing.setUserId(sellerId);
        existing.setAddressJson("{\"name\":\"Shop A\",\"phone\":\"0901234567\",\"address\":\"10 Nguyen Hue, Q1\",\"latitude\":10.775600,\"longitude\":106.701900}");
        when(addresses.findById(sellerId)).thenReturn(Optional.of(existing));
        when(orders.hasUnfinishedSellerOrder(sellerId)).thenReturn(true);

        var saved = service.savePickup(sellerId, currentAddress);
        assertEquals(currentAddress.address(), saved.address());
        verify(addresses).save(any(ShippingPickupAddress.class));
    }

    @Test
    void allowsPickupChangeWhenAllOrdersDisputesInspectionsAndReservationsAreSettled() {
        var status = service.pickupUpdateStatus(sellerId);
        assertTrue(status.updatable());
        assertNull(status.reason());

        var saved = service.savePickup(sellerId, updatedAddress);
        assertEquals(updatedAddress.address(), saved.address());
        verify(addresses).save(any(ShippingPickupAddress.class));
    }
}
