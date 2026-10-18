package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionSealLogResponse;
import com.secondlife.secondlife.entity.InspectionSealLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class InspectionSealLogMapper {
    private final Delegate delegate;

    public InspectionSealLogMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public InspectionSealLogResponse toResponse(InspectionSealLog log) {
        return delegate.toResponse(log);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "scannedBy", source = "scannedBy.id")
        @Mapping(target = "scannedByName", expression = "java(InspectionOrderMapper.inspectorName(log.getScannedBy()))")
        InspectionSealLogResponse toResponse(InspectionSealLog log);
    }
}
