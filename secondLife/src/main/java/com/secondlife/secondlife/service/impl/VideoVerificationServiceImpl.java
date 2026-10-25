package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.VideoVerificationWindow;
import com.secondlife.secondlife.exception.VideoUploadException;
import com.secondlife.secondlife.repository.VideoVerificationWindowRepository;
import com.secondlife.secondlife.service.VideoVerificationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class VideoVerificationServiceImpl implements VideoVerificationService {
    private final VideoVerificationWindowRepository windows;
    private final Clock clock;
    private final int globalPerHour;
    private final int ownerPerHour;
    private final long cooldownSeconds;

    public VideoVerificationServiceImpl(VideoVerificationWindowRepository windows,
            @Qualifier("orderReleaseClock") Clock clock,
            @Value("${app.video-upload.verification-global-per-hour:100}") int globalPerHour,
            @Value("${app.video-upload.verification-owner-per-hour:20}") int ownerPerHour,
            @Value("${app.video-upload.verification-cooldown-seconds:30}") long cooldownSeconds) {
        if (globalPerHour <= 0 || ownerPerHour <= 0 || cooldownSeconds <= 0) {
            throw new IllegalArgumentException("Video verification limits must be positive");
        }
        this.windows = windows;
        this.clock = clock;
        this.globalPerHour = globalPerHour;
        this.ownerPerHour = ownerPerHour;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquire(UUID ownerId, UUID uploadId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Every instance locks in this order; no upload-session FK or row is touched here.
        var global = lockWindow("global", now);
        var owner = lockWindow("owner:" + ownerId, now);
        var upload = lockWindow("upload:" + uploadId, now);
        requireAvailable(global, now, 3600, globalPerHour, "Global video verification limit reached; retry later");
        requireAvailable(owner, now, 3600, ownerPerHour, "Your video verification limit was reached; retry later");
        requireAvailable(upload, now, cooldownSeconds, 1, "Video verification was attempted recently; retry after the cooldown");
        global.setCalls(global.getCalls() + 1);
        owner.setCalls(owner.getCalls() + 1);
        upload.setCalls(upload.getCalls() + 1);
    }

    private VideoVerificationWindow lockWindow(String id, Instant now) {
        windows.initialize(id, now);
        return windows.findByIdForUpdate(id).orElseThrow(() -> new IllegalStateException("Video verification window not initialized"));
    }

    private void requireAvailable(VideoVerificationWindow window, Instant now, long periodSeconds, int maximum, String message) {
        if (!now.isBefore(window.getStartedAt().plusSeconds(periodSeconds))) {
            window.setStartedAt(now);
            window.setCalls(0);
        }
        if (window.getCalls() >= maximum) throw new VideoUploadException(message, 429);
    }
}
