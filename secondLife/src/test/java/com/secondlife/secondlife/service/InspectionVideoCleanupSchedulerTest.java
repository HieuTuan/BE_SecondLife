package com.secondlife.secondlife.service;

import com.cloudinary.Cloudinary;
import com.secondlife.secondlife.integration.storage.CloudinaryMediaStorage;
import com.cloudinary.Uploader;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.scheduler.InspectionVideoCleanupScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionVideoCleanupSchedulerTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private Clock clock;
    private InspectionVideoCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        scheduler = new InspectionVideoCleanupScheduler(inspectionOrderRepository, new CloudinaryMediaStorage(cloudinary), clock);
    }

    @Test
    @DisplayName("cleanupDueVideos: Tự động tiêu hủy video trên Cloudinary sau 48h và cập nhật trạng thái CLEANED")
    void testCleanupDueVideos() throws IOException {
        InspectionOrder order = new InspectionOrder();
        order.setId(UUID.randomUUID());
        order.setVideoProofUrl("https://res.cloudinary.com/demo/video/upload/proof.mp4");
        order.setVideoProofPublicId("secondlife/inspections/videos/proof_123");
        order.setUnboxingVideoUrl("https://res.cloudinary.com/demo/video/upload/unbox.mp4");
        order.setUnboxingVideoPublicId("secondlife/inspections/videos/unbox_456");
        order.setVideoCleanupStatus("ACTIVE");
        order.setVideoRetentionUntil(Instant.parse("2026-10-14T09:00:00Z"));

        when(inspectionOrderRepository.findDueForVideoCleanup(clock.instant()))
                .thenReturn(List.of(order));
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("secondlife/inspections/videos/proof_123"), any(Map.class)))
                .thenReturn(Map.of("result", "ok"));
        when(uploader.destroy(eq("secondlife/inspections/videos/unbox_456"), any(Map.class)))
                .thenReturn(Map.of("result", "ok"));

        scheduler.cleanupDueVideos();

        verify(uploader, times(1)).destroy(eq("secondlife/inspections/videos/proof_123"), any(Map.class));
        verify(uploader, times(1)).destroy(eq("secondlife/inspections/videos/unbox_456"), any(Map.class));
        verify(inspectionOrderRepository, times(1)).save(order);

        assertNull(order.getVideoProofUrl());
        assertNull(order.getUnboxingVideoUrl());
        assertEquals("CLEANED", order.getVideoCleanupStatus());
        assertNotNull(order.getVideoCleanedAt());
    }

    @Test
    @DisplayName("cleanupDueVideos: Không có đơn nào đến hạn thì không gọi Cloudinary")
    void testCleanupDueVideos_NoDueOrders() {
        when(inspectionOrderRepository.findDueForVideoCleanup(clock.instant()))
                .thenReturn(List.of());

        scheduler.cleanupDueVideos();

        verifyNoInteractions(cloudinary);
        verify(inspectionOrderRepository, never()).save(any());
    }
}
