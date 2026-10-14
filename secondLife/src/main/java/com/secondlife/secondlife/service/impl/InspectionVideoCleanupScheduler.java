package com.secondlife.secondlife.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.media.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class InspectionVideoCleanupScheduler {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final Cloudinary cloudinary;
    private final Clock clock;

    @Autowired
    public InspectionVideoCleanupScheduler(InspectionOrderRepository inspectionOrderRepository,
                                           Cloudinary cloudinary,
                                           @Autowired(required = false) Clock clock) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.cloudinary = cloudinary;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public InspectionVideoCleanupScheduler(InspectionOrderRepository inspectionOrderRepository,
                                           Cloudinary cloudinary) {
        this(inspectionOrderRepository, cloudinary, Clock.systemUTC());
    }

    @Scheduled(fixedDelayString = "${app.media.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupDueVideos() {
        Instant now = clock.instant();
        List<InspectionOrder> dueOrders = inspectionOrderRepository.findDueForVideoCleanup(now);
        if (dueOrders.isEmpty()) {
            return;
        }

        log.info("Found {} inspection order(s) eligible for video proof cleanup", dueOrders.size());
        for (InspectionOrder order : dueOrders) {
            try {
                if (order.getVideoProofPublicId() != null && !order.getVideoProofPublicId().isBlank()) {
                    log.info("Destroying Cloudinary video proof publicId [{}] for inspection order [{}]",
                            order.getVideoProofPublicId(), order.getId());
                    cloudinary.uploader().destroy(order.getVideoProofPublicId(), ObjectUtils.asMap("resource_type", "video"));
                }
                if (order.getUnboxingVideoPublicId() != null && !order.getUnboxingVideoPublicId().isBlank()) {
                    log.info("Destroying Cloudinary unboxing video publicId [{}] for inspection order [{}]",
                            order.getUnboxingVideoPublicId(), order.getId());
                    cloudinary.uploader().destroy(order.getUnboxingVideoPublicId(), ObjectUtils.asMap("resource_type", "video"));
                }

                order.setVideoProofUrl(null);
                order.setUnboxingVideoUrl(null);
                order.setVideoCleanupStatus("CLEANED");
                order.setVideoCleanedAt(now);
                inspectionOrderRepository.save(order);
                log.info("Successfully cleaned up expired video proof for inspection order [{}]", order.getId());
            } catch (Exception ex) {
                log.error("Failed to cleanup video proof for inspection order [{}]: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }
}
