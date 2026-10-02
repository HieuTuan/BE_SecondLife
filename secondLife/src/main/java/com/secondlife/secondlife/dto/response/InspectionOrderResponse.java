package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.InspectionOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class InspectionOrderResponse {
    private UUID id;
    private UUID postId;
    private String postTitle;
    private String postImageUrl;
    private BigDecimal postPrice;
    private UUID inspectorId;
    private String inspectorName;
    private String status;
    private String note;
    private BigDecimal inspectionFee;
    private BigDecimal shippingFee;
    private Instant createdAt;
    private Instant updatedAt;

    public static InspectionOrderResponse from(InspectionOrder order) {
        InspectionOrderResponse res = new InspectionOrderResponse();
        res.setId(order.getId());
        if (order.getPost() != null) {
            res.setPostId(order.getPost().getId());
            res.setPostTitle(order.getPost().getTitle());
            res.setPostImageUrl(order.getPost().getImageUrl());
            res.setPostPrice(order.getPost().getPrice());
        }
        if (order.getInspector() != null) {
            res.setInspectorId(order.getInspector().getId());
            res.setInspectorName(order.getInspector().getEmail());
        }
        res.setStatus(order.getStatus());
        res.setNote(order.getNote());
        res.setInspectionFee(order.getInspectionFee());
        res.setShippingFee(order.getShippingFee());
        res.setCreatedAt(order.getCreatedAt());
        res.setUpdatedAt(order.getUpdatedAt());
        return res;
    }
}
