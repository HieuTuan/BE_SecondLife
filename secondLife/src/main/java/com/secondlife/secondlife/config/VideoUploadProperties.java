package com.secondlife.secondlife.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.video-upload")
public record VideoUploadProperties(
        @DefaultValue("") String uploadPreset,
        @DefaultValue("262144000") @Positive long maxBytes,
        @DefaultValue("600") @Positive long maxDurationSeconds,
        @DefaultValue("3600") @Positive @Max(3600) long sessionTtlSeconds,
        @DefaultValue("300") @Positive @Max(3600) long playbackTtlSeconds,
        @DefaultValue("10") @Positive int maxSessionsPerHour,
        @DefaultValue("10000") @Positive int timeoutMs,
        @DefaultValue("6291456") @Min(5242881) int chunkSizeBytes) { }