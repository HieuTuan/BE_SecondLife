package com.secondlife.secondlife.service;
import com.secondlife.secondlife.dto.reputation.*;
import com.secondlife.secondlife.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service
public class ReportEvidenceService {
    public record UploadResponse(UUID evidenceId,String evidenceUrl,String contentType,long size) { }
    public record Content(String contentType,byte[] bytes) { }
    private static final String PREFIX="/api/v1/reports/evidence/";
    private final JdbcTemplate jdbc;
    private final ReportEvidenceStorage storage;
    private final long maxBytes;
    public ReportEvidenceService(JdbcTemplate jdbc,ReportEvidenceStorage storage,@Value("${app.media.max-file-size-bytes}") long maxBytes) {
        this.jdbc=jdbc; this.storage=storage; this.maxBytes=maxBytes;
    }
    @Transactional
    public UploadResponse upload(UUID ownerId,MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>maxBytes) throw new BadRequestException("Evidence image is empty or exceeds the upload size limit");
        if(!Set.of("image/jpeg","image/png","image/webp").contains(String.valueOf(file.getContentType())))
            throw new BadRequestException("Evidence supports JPEG, PNG and WEBP images");
        try {
            var image=storage.upload(file); UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO report_evidence_uploads(id,owner_id,storage_id,content_type,size_bytes) VALUES (?,?,?,?,?)",
                    id,ownerId,image.storageId(),image.contentType(),image.size());
            return new UploadResponse(id,PREFIX+id,image.contentType(),image.size());
        } catch(IOException ex) { throw new BadRequestException("Private evidence upload failed; try again"); }
    }
    public void validateOwner(UUID ownerId,List<String> urls) {
        for(String url:urls) {
            UUID id=id(url);
            var owners=jdbc.query("SELECT owner_id FROM report_evidence_uploads WHERE id=?",(rs,n)->rs.getObject(1,UUID.class),id);
            if(owners.isEmpty()) throw new BadRequestException("Report evidence upload not found");
            if(!ownerId.equals(owners.getFirst())) throw new ForbiddenException("Report evidence belongs to another user");
        }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void bind(UUID ownerId,UUID reportId,List<String> urls) {
        for(String url:urls) {
            int updated=jdbc.update("UPDATE report_evidence_uploads SET report_id=? WHERE id=? AND owner_id=? AND report_id IS NULL",reportId,id(url),ownerId);
            if(updated!=1) throw new ConflictException("Evidence was already attached to another report; upload it again for the new report");
        }
    }
    @Transactional(readOnly=true)
    public Content read(UUID actorId,boolean reviewer,UUID evidenceId) {
        var uploads=jdbc.queryForList("SELECT * FROM report_evidence_uploads WHERE id=?",evidenceId);
        if(uploads.isEmpty()) throw new NotFoundException("Report evidence not found");
        var row=uploads.getFirst(); UUID owner=(UUID)row.get("owner_id"); UUID report=(UUID)row.get("report_id");
        if(!actorId.equals(owner)) {
            if(!reviewer || report==null) throw new ForbiddenException("You cannot read this report evidence");
            // Review authority is checked by the controller; never expose evidence of an unsubmitted report.
        }
        try { return new Content((String)row.get("content_type"),storage.read((String)row.get("storage_id"))); }
        catch(IOException ex) { throw new BadRequestException("Report evidence is temporarily unavailable"); }
    }
    private UUID id(String url) {
        if(url==null || !url.matches("/api/v1/reports/evidence/[0-9a-fA-F-]{36}"))
            throw new BadRequestException("Upload private report evidence first via POST /api/v1/reports/evidence; use its evidenceUrl");
        try { return UUID.fromString(url.substring(PREFIX.length())); }
        catch(IllegalArgumentException ex) { throw new BadRequestException("Invalid report evidence reference"); }
    }
}
