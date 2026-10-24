package com.secondlife.secondlife.mapper;
import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.config.VideoUploadProperties;
import com.secondlife.secondlife.dto.response.ReportEvidenceMetadataResponse;
import com.secondlife.secondlife.dto.response.VideoMetadataResponse;
import com.secondlife.secondlife.dto.response.VideoSignatureResponse;
import com.secondlife.secondlife.entity.ReportEvidenceUpload;
import com.secondlife.secondlife.entity.VideoUploadSession;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage.SignedUpload;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class VideoUploadMapper {
    private final Delegate delegate;
    public VideoUploadMapper(Delegate delegate) { this.delegate=delegate; }
    public VideoSignatureResponse signature(VideoUploadSession session,SignedUpload signed,VideoUploadProperties limits) {
        return delegate.signature(session,signed,limits);
    }
    public VideoMetadataResponse metadata(VideoUploadSession session) { return delegate.metadata(session); }
    public ReportEvidenceMetadataResponse evidence(ReportEvidenceUpload upload) { return delegate.evidence(upload); }
    @Mapper(config=MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target="uploadId",source="session.id")
        @Mapping(target="publicId",source="session.publicId")
        @Mapping(target="expiresAt",source="session.expiresAt")
        @Mapping(target="maxBytes",source="limits.maxBytes")
        @Mapping(target="chunkSizeBytes",source="limits.chunkSizeBytes")
        VideoSignatureResponse signature(VideoUploadSession session,SignedUpload signed,VideoUploadProperties limits);
        @Mapping(target="uploadId",source="id")
        @Mapping(target="evidenceId",source="id")
        @Mapping(target="evidenceUrl",expression="java(evidenceUrl(session.getId()))")
        VideoMetadataResponse metadata(VideoUploadSession session);
        @Mapping(target="evidenceId",source="id")
        @Mapping(target="size",source="sizeBytes")
        @Mapping(target="mediaType",expression="java(mediaType(upload))")
        @Mapping(target="contentUrl",expression="java(contentUrl(upload))")
        ReportEvidenceMetadataResponse evidence(ReportEvidenceUpload upload);
        default String evidenceUrl(UUID id) { return "/api/v1/reports/evidence/"+id; }
        default String mediaType(ReportEvidenceUpload upload) { return upload.getContentType().startsWith("video/") ? "VIDEO" : "IMAGE"; }
        default String contentUrl(ReportEvidenceUpload upload) {
            return upload.getContentType().startsWith("video/") ? "/api/video/"+upload.getId()+"/playback" : evidenceUrl(upload.getId());
        }

    }
}
