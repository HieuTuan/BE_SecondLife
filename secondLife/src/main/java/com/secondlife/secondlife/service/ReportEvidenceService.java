package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ReportEvidenceContent;
import com.secondlife.secondlife.dto.response.ReportEvidenceMetadataResponse;
import com.secondlife.secondlife.dto.response.ReportEvidenceUploadResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

public interface ReportEvidenceService {

    ReportEvidenceUploadResponse upload(UUID ownerId, MultipartFile file);

    void validateOwner(UUID ownerId, List<String> urls);

    void bind(UUID ownerId, UUID reportId, List<String> urls);

    void requireReadable(UUID actorId, boolean reviewer, UUID evidenceId);

    ReportEvidenceMetadataResponse metadata(UUID actorId, boolean reviewer, UUID evidenceId);

    ReportEvidenceContent read(UUID actorId, boolean reviewer, UUID evidenceId);

}
