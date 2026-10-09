package com.secondlife.secondlife.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateDraftRequest {
    private String title;
    private String description;
    private String itemCondition;
    private BigDecimal price;
    private Integer shippingWeight;
    private Integer shippingLength;
    private Integer shippingWidth;
    private Integer shippingHeight;
}
