package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.HighValueInspectionPolicyRequest;
import com.secondlife.secondlife.entity.HighValueInspectionPolicy;
import com.secondlife.secondlife.repository.HighValueInspectionPolicyRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HighValueInspectionPolicyServiceImplTest {
    @Test
    void updatePersistsTheNewThresholdAndAdminIdentityUnderARowLock() {
        var repository = mock(HighValueInspectionPolicyRepository.class);
        var policy = new HighValueInspectionPolicy();
        policy.setId((short) 1);
        policy.setThresholdPrice(new BigDecimal("10000000"));
        policy.setVersion(2);
        policy.setUpdatedAt(Instant.now().minusSeconds(60));
        when(repository.findForUpdate()).thenReturn(Optional.of(policy));
        when(repository.saveAndFlush(policy)).thenReturn(policy);
        UUID adminId = UUID.randomUUID();

        var response = new HighValueInspectionPolicyServiceImpl(repository)
                .update(adminId, new HighValueInspectionPolicyRequest(new BigDecimal("8000000")));

        assertEquals(new BigDecimal("8000000"), response.thresholdPrice());
        assertEquals(adminId, response.updatedBy());
        assertEquals("VND", response.currency());
        verify(repository).findForUpdate();
        verify(repository).saveAndFlush(policy);
    }
}
