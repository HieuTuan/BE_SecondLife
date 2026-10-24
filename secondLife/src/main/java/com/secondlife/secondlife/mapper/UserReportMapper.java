package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.ReportRequests.TargetType;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.entity.UserReport;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class UserReportMapper {
    private final Delegate delegate;

    public UserReportMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ReportResponse toResponse(UserReport report, @Context ObjectMapper json) {
        return delegate.toResponse(report, json);
    }

    @Mapper(config = MapStructConfiguration.class, imports = {List.class, TargetType.class})
    public interface Delegate {
        @Mapping(target = "evidenceUrls", expression = "java(List.of(json.readValue(report.getEvidenceUrls(),String[].class)))")
        @Mapping(target = "targetType", expression = "java(report.getOrderId()==null?TargetType.LISTING:TargetType.ORDER)")
        ReportResponse toResponse(UserReport report, @Context ObjectMapper json);
    }
}
