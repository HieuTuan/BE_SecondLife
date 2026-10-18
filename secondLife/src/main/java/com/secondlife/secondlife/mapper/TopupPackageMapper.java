package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
public class TopupPackageMapper {
    private final Delegate delegate;

    public TopupPackageMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public TopupPackageResponse toResponse(TopupPackage source) {
        return delegate.toResponse(source);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        TopupPackageResponse toResponse(TopupPackage source);
    }
}
