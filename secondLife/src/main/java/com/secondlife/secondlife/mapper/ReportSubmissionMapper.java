package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ReportSubmissionResponse;
import com.secondlife.secondlife.entity.ReportSubmission;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class ReportSubmissionMapper {
    private final Delegate delegate;

    public ReportSubmissionMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ReportSubmissionResponse toResponse(ReportSubmission submission, @Context ObjectMapper json) {
        return delegate.toResponse(submission, json);
    }

    @Mapper(config = MapStructConfiguration.class, imports = List.class)
    public interface Delegate {
        @Mapping(target = "evidenceUrls", expression = "java(List.of(json.readValue(submission.getEvidenceUrls(), String[].class)))")
        ReportSubmissionResponse toResponse(ReportSubmission submission, @Context ObjectMapper json);
    }
}
