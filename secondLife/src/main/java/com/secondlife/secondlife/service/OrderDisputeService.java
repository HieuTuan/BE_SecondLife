package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.UserReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface OrderDisputeService {
    Instant open(Order order);
    void resolve(ReportResponse report, Resolve request, ResponsibleParty party);
    UserReport approvedReturn(Order order, UUID reportId);
    void bindReturn(Order order, UserReport report, Shipment shipment);
    UserReport refundDecision(Order order, Shipment shipment, ResponsibleParty submittedParty);
    void recordRefund(UserReport report, UUID actorId, BigDecimal amount);
}
