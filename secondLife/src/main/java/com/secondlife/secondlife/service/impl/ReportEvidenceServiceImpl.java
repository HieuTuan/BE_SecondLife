package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.ReportEvidenceService;
import com.secondlife.secondlife.dto.response.ReportEvidenceContent;
import com.secondlife.secondlife.dto.response.ReportEvidenceUploadResponse;
import com.secondlife.secondlife.exception.*;
import org.springframework.beans.factory.annotation.Value;
import com.secondlife.secondlife.repository.ReportEvidenceRepository;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service("reportEvidenceService")
public class ReportEvidenceServiceImpl implements ReportEvidenceService {

    private static final String PREFIX="/api/v1/reports/evidence/";
    private final ReportEvidenceRepository evidence;
    private final ReportEvidenceStorage storage;
    private final long maxBytes;
    public ReportEvidenceServiceImpl(ReportEvidenceRepository evidence,ReportEvidenceStorage storage,@Value("${app.media.max-file-size-bytes}") long maxBytes) {
        this.evidence=evidence; this.storage=storage; this.maxBytes=maxBytes;
    }
    @Transactional
    public ReportEvidenceUploadResponse upload(UUID ownerId,MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>maxBytes) throw new BadRequestException("Evidence image is empty or exceeds the upload size limit");
        if(!Set.of("image/jpeg","image/png","image/webp").contains(String.valueOf(file.getContentType())))
            throw new BadRequestException("Evidence supports JPEG, PNG and WEBP images");
        try {
            var image=storage.upload(file); UUID id=UUID.randomUUID();
            evidence.saveUpload(id,ownerId,image.storageId(),image.contentType(),image.size());
            return new ReportEvidenceUploadResponse(id,PREFIX+id,image.contentType(),image.size());
        } catch(IOException ex) { throw new BadRequestException("Private evidence upload failed; try again"); }
    }
    public void validateOwner(UUID ownerId,List<String> urls) {
        for(String url:urls) {
            UUID id=id(url);
            UUID owner=evidence.findOwnerId(id).orElseThrow(() -> new BadRequestException("Report evidence upload not found"));
            if(!ownerId.equals(owner)) throw new ForbiddenException("Report evidence belongs to another user");
        }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bind(UUID ownerId,UUID reportId,List<String> urls) {
        for(String url:urls) {
            int updated=evidence.attachToReport(id(url),ownerId,reportId);
            if(updated!=1) throw new ConflictException("Evidence was already attached to another report; upload it again for the new report");
        }
    }
    @Transactional(readOnly=true)
    public ReportEvidenceContent read(UUID actorId,boolean reviewer,UUID evidenceId) {
        var upload=evidence.findUploadById(evidenceId).orElseThrow(() -> new NotFoundException("Report evidence not found"));
        UUID owner=upload.ownerId(); UUID report=upload.reportId();
        if(!actorId.equals(owner)) {
            if(!reviewer || report==null) throw new ForbiddenException("You cannot read this report evidence");
            // Review authority is checked by the controller; never expose evidence of an unsubmitted report.
        }
        try { return new ReportEvidenceContent(upload.contentType(),storage.read(upload.storageId())); }
        catch(IOException ex) { throw new BadRequestException("Report evidence is temporarily unavailable"); }
    }
    private UUID id(String url) {
        if(url==null || !url.matches("/api/v1/reports/evidence/[0-9a-fA-F-]{36}"))
            throw new BadRequestException("Upload private report evidence first via POST /api/v1/reports/evidence; use its evidenceUrl");
        try { return UUID.fromString(url.substring(PREFIX.length())); }
        catch(IllegalArgumentException ex) { throw new BadRequestException("Invalid report evidence reference"); }
    }
}
