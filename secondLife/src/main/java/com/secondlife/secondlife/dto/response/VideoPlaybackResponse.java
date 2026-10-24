package com.secondlife.secondlife.dto.response;
import java.time.Instant;
public record VideoPlaybackResponse(String url, Instant expiresAt) { }
