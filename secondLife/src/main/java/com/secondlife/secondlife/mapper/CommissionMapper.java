package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CommissionAuditResponse;
import com.secondlife.secondlife.dto.response.CommissionRuleResponse;
import com.secondlife.secondlife.dto.response.OrderCommissionResponse;
import com.secondlife.secondlife.entity.CommissionRule;
import com.secondlife.secondlife.entity.CommissionRuleAudit;
import com.secondlife.secondlife.entity.OrderCommissionSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CommissionMapper {
    private final Delegate delegate;

    public CommissionMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CommissionAuditResponse toAuditResponse(CommissionRuleAudit source) {
        return delegate.toAuditResponse(source);
    }

    public CommissionRuleResponse toRuleResponse(CommissionRule source) {
        return delegate.toRuleResponse(source);
    }

    public OrderCommissionResponse toOrderResponse(OrderCommissionSnapshot snapshot, BigDecimal rawCommission, BigDecimal platformCommission, BigDecimal sellerPayout) {
        return delegate.toOrderResponse(snapshot, rawCommission, platformCommission, sellerPayout);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        CommissionAuditResponse toAuditResponse(CommissionRuleAudit source);

        CommissionRuleResponse toRuleResponse(CommissionRule source);

        @Mapping(target = "snapshotId", source = "snapshot.id")
        @Mapping(target = "rawCommission", source = "rawCommission")
        @Mapping(target = "platformCommission", source = "platformCommission")
        @Mapping(target = "sellerPayout", source = "sellerPayout")
        OrderCommissionResponse toOrderResponse(OrderCommissionSnapshot snapshot, BigDecimal rawCommission,
                BigDecimal platformCommission, BigDecimal sellerPayout);
    }
}
