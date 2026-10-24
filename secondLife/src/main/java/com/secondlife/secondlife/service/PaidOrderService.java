package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Order;

public interface PaidOrderService {
    void complete(Order order, boolean useWallet);

}
