package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ReputationHistoryResponse;
import com.secondlife.secondlife.dto.response.SellerReputationData.Metrics;
import com.secondlife.secondlife.dto.response.SellerReputationData.State;
import com.secondlife.secondlife.repository.SellerReputationRepository.HistoryRow;
import com.secondlife.secondlife.repository.SellerReputationRepository.MetricsRow;
import com.secondlife.secondlife.repository.SellerReputationRepository.StateRow;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
public class SellerReputationMapper {
    private final Delegate delegate;

    public SellerReputationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public State state(StateRow row) {
        return delegate.state(row);
    }

    public ReputationHistoryResponse history(HistoryRow row) {
        return delegate.history(row);
    }

    public Metrics metrics(MetricsRow row) {
        return delegate.metrics(row);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        State state(StateRow row);
        ReputationHistoryResponse history(HistoryRow row);
        Metrics metrics(MetricsRow row);
    }
}
