package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.OrderFeedResponse;

import java.util.UUID;

public interface OrderFeedService {
    OrderFeedResponse read(UUID actor, String perspective, String filter, String query, int page, int size, UUID detail);
}
