package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.response.CreditPricingRuleResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import com.secondlife.secondlife.mapper.TopupPackageMapper;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.service.impl.AdminTopupPackageServiceImpl;
import com.secondlife.secondlife.service.impl.TopupPackagePricingPolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTopupPackageServiceTest {
    private TopupPackageRepository repository;
    private AdminTopupPackageService service;
    private final UUID id = UUID.randomUUID();
    private final TopupPackageRequest request = new TopupPackageRequest("Updated Combo", null,
            3, 12, 4, new BigDecimal("65000.50"), true);

    @BeforeEach
    void setup() {
        repository = mock(TopupPackageRepository.class);
        var pricing = mock(CreditPricingService.class);
        when(pricing.getAdminPrices()).thenReturn(List.of(
                new CreditPricingRuleResponse(CreditType.LISTING, new BigDecimal("20000"), "VND", true),
                new CreditPricingRuleResponse(CreditType.AI_CHAT, new BigDecimal("2000"), "VND", true),
                new CreditPricingRuleResponse(CreditType.VALUATION, new BigDecimal("2000"), "VND", true)));
        service = new AdminTopupPackageServiceImpl(repository, MapperTestSupport.mapper(TopupPackageMapper.class),
                pricing, new TopupPackagePricingPolicy());
    }

    @Test
    void createsPackageWithGeneratedIdAndAllEntitlements() {
        when(repository.saveAndFlush(any(TopupPackage.class))).thenAnswer(call -> {
            TopupPackage entity = call.getArgument(0);
            entity.setId(id);
            return entity;
        });
        var response = service.createPackage(request);
        assertAll(() -> assertEquals(id, response.id()),
                () -> assertEquals(request.name(), response.name()),
                () -> assertEquals(request.price(), response.price()),
                () -> assertEquals(3, response.postCredits()),
                () -> assertEquals(12, response.chatCredits()),
                () -> assertEquals(4, response.valuationCredits()),
                () -> assertEquals(new BigDecimal("29.35"), response.discountPercentage()));
    }

    @Test
    void fullUpdateKeepsIdentityAndCanClearOptionalDescription() {
        var entity = new TopupPackage();
        entity.setId(id);
        entity.setDescription("Old description");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        var response = service.updatePackage(id, request);
        assertEquals(id, response.id());
        assertEquals(request.name(), response.name());
        assertEquals(request.price(), response.price());
        assertNull(response.description());
        assertEquals(new BigDecimal("29.35"), response.discountPercentage());
        assertEquals(3, response.postCredits());
        assertEquals(12, response.chatCredits());
        assertEquals(4, response.valuationCredits());
    }

    @Test
    void missingPackageIs404ForReadUpdateAndDeleteWithoutWriting() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertEquals("Không tìm thấy gói nạp", assertThrows(NotFoundException.class,
                () -> service.getPackage(id)).getMessage());
        assertThrows(NotFoundException.class, () -> service.updatePackage(id, request));
        assertThrows(NotFoundException.class, () -> service.deletePackage(id));
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void deletesOnlyTheRequestedPackage() {
        var entity = new TopupPackage();
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        service.deletePackage(id);
        verify(repository).saveAndFlush(entity);
        assertEquals(false, entity.isActive());
        verify(repository, never()).delete(any());
    }

    @Test
    void mapsAdminListAndDetailWithoutExposingEntity() {
        var entity = new TopupPackage();
        entity.setId(id);
        entity.setName("Basic Combo");
        when(repository.findAll(Sort.by("name", "id"))).thenReturn(List.of(entity));
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        assertEquals(List.of(service.getPackage(id)), service.getPackages());
    }
}
