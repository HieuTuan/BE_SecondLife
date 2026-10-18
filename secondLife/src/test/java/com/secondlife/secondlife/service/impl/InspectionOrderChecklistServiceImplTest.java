package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.service.InspectionChecklistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionOrderChecklistServiceImplTest {

    @Mock
    private InspectionOrderRepository orderRepository;
    @Mock
    private InspectionChecklistService checklistService;
    @InjectMocks
    private InspectionOrderChecklistServiceImpl service;

    @Test
    void resolvesChecklistUsingTheOrdersProductCategory() {
        UUID orderId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Post post = new Post();
        post.setCategoryId(categoryId);
        InspectionOrder order = new InspectionOrder();
        order.setPost(post);
        InspectionChecklistResponse checklist = InspectionChecklistResponse.builder().categoryId(categoryId).build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(checklistService.getByCategoryId(categoryId)).thenReturn(checklist);

        assertSame(checklist, service.getForOrder(orderId));
    }

    @Test
    void preservesDefaultChecklistLookupForOrdersWithoutAPost() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(new InspectionOrder()));
        InspectionChecklistResponse checklist = InspectionChecklistResponse.builder().title("Default").build();
        when(checklistService.getByCategoryId(null)).thenReturn(checklist);

        assertSame(checklist, service.getForOrder(orderId));
        verify(checklistService).getByCategoryId(null);
    }

    @Test
    void preservesNotFoundErrorBeforeLookingUpAChecklist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.getForOrder(orderId));

        assertEquals("Đơn kiểm định không tồn tại", error.getMessage());
        verifyNoInteractions(checklistService);
    }
}
