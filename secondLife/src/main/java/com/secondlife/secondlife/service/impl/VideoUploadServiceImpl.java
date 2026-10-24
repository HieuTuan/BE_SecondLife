package com.secondlife.secondlife.service.impl;
import com.secondlife.secondlife.config.VideoUploadProperties;
import com.secondlife.secondlife.dto.request.VideoCompleteRequest;
import com.secondlife.secondlife.dto.request.VideoSignatureRequest;
import com.secondlife.secondlife.dto.response.VideoMetadataResponse;
import com.secondlife.secondlife.dto.response.VideoPlaybackResponse;
import com.secondlife.secondlife.dto.response.VideoSignatureResponse;
import com.secondlife.secondlife.entity.VideoUploadSession;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.exception.VideoUploadException;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage.SignedUpload;
import com.secondlife.secondlife.integration.storage.CloudinaryVideoStorage.VerifiedVideo;
import com.secondlife.secondlife.mapper.VideoUploadMapper;
import com.secondlife.secondlife.repository.ReportEvidenceRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.VideoUploadSessionRepository;
import com.secondlife.secondlife.service.ReportEvidenceService;
import com.secondlife.secondlife.service.VideoUploadService;
import com.secondlife.secondlife.service.VideoVerificationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
@Service
public class VideoUploadServiceImpl implements VideoUploadService {
    private final VideoUploadSessionRepository sessions;
    private final UserRepository users;
    private final ReportEvidenceRepository evidence;
    private final ReportEvidenceService evidenceService;
    private final CloudinaryVideoStorage storage;
    private final VideoUploadProperties limits;
    private final VideoUploadMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final VideoVerificationService verification;
    public VideoUploadServiceImpl(VideoUploadSessionRepository sessions,UserRepository users,
            ReportEvidenceRepository evidence,ReportEvidenceService evidenceService,CloudinaryVideoStorage storage,
            VideoUploadProperties limits,VideoUploadMapper mapper,ObjectMapper json,@Qualifier("orderReleaseClock") Clock clock, VideoVerificationService verification) {
        this.sessions=sessions; this.users=users; this.evidence=evidence; this.evidenceService=evidenceService;
        this.storage=storage; this.limits=limits; this.mapper=mapper; this.json=json; this.clock=clock; this.verification=verification;
    }
    @Override @Transactional
    public VideoSignatureResponse signature(UUID ownerId,VideoSignatureRequest request) {
        users.findByIdForRoleUpdate(ownerId).orElseThrow(() -> new NotFoundException("User not found"));
        var existing=sessions.findByOwnerIdAndRequestId(ownerId,request.requestId());
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        if(existing.isPresent()) {
            var session=existing.get();
            if(!now.isBefore(session.getExpiresAt())) throw new ConflictException("Video upload session has expired; create a new requestId");
            return mapper.signature(session,json.readValue(session.getSignatureData(),SignedUpload.class),limits);
        }
        if(sessions.countByOwnerIdAndCreatedAtAfter(ownerId,now.minusSeconds(3600))>=limits.maxSessionsPerHour())
            throw new VideoUploadException("Video upload limit reached; retry later",429);
        UUID id=UUID.randomUUID();
        String publicId="report-evidence/videos/"+id;
        SignedUpload signed=storage.sign(publicId);
        var session=new VideoUploadSession();
        session.setId(id); session.setOwnerId(ownerId); session.setRequestId(request.requestId()); session.setPublicId(publicId);
        session.setSignatureData(json.writeValueAsString(signed)); session.setCreatedAt(now);
        session.setExpiresAt(now.plusSeconds(limits.sessionTtlSeconds()));
        sessions.saveAndFlush(session);
        return mapper.signature(session,signed,limits);
    }
    @Override @Transactional
    public VideoMetadataResponse complete(UUID ownerId,VideoCompleteRequest request) {
        var session=sessions.findByIdForUpdate(request.uploadId()).orElseThrow(() -> new NotFoundException("Video upload session not found"));
        if(!ownerId.equals(session.getOwnerId())) throw new ForbiddenException("Video upload belongs to another user");
        if(!session.getPublicId().equals(request.publicId())) throw new ConflictException("Video publicId does not match the upload session");
        if(session.getCompletedAt()!=null) {
            compareClientMetadata(request,session.getVideoUrl(),session.getBytes(),session.getFormat(),session.getDuration());
            return mapper.metadata(session);
        }
        if(!clock.instant().isBefore(session.getExpiresAt())) throw new ConflictException("Video upload session has expired");
        verification.acquire(ownerId,session.getId());
        VerifiedVideo video;
        try { video=storage.inspect(session.getPublicId()); }
        catch(IOException error) { throw new VideoUploadException("Video is not available for verification; finish uploading all chunks and retry",502); }
        validateVerifiedVideo(session,video);
        compareClientMetadata(request,video.videoUrl(),video.bytes(),video.format(),video.duration());
        String contentType=switch(video.format()) { case "mp4" -> "video/mp4"; case "mov" -> "video/quicktime"; case "webm" -> "video/webm"; default -> throw new BadRequestException("Unsupported video format"); };
        session.setVideoUrl(video.videoUrl()); session.setBytes(video.bytes()); session.setFormat(video.format());
        session.setDuration(video.duration()); session.setAssetId(video.assetId()); session.setProviderVersion(video.version());
        session.setCompletedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        evidence.saveUpload(session.getId(),ownerId,video.publicId(),contentType,video.bytes());
        sessions.saveAndFlush(session);
        return mapper.metadata(session);
    }
    @Override @Transactional(readOnly=true)
    public VideoPlaybackResponse playback(UUID actorId,boolean reviewer,UUID uploadId) {
        evidenceService.requireReadable(actorId,reviewer,uploadId);
        var session=sessions.findById(uploadId).orElseThrow(() -> new NotFoundException("Video not found"));
        if(session.getCompletedAt()==null) throw new ConflictException("Video upload has not completed");
        Instant expiresAt=clock.instant().plusSeconds(limits.playbackTtlSeconds());
        try { return new VideoPlaybackResponse(storage.playback(session.getPublicId(),session.getFormat(),expiresAt),expiresAt); }
        catch(IOException error) { throw new VideoUploadException("Video playback is temporarily unavailable",502); }
    }
    private void validateVerifiedVideo(VideoUploadSession session,VerifiedVideo video) {
        if(video==null || !session.getPublicId().equals(video.publicId()) || video.bytes()<=0 || video.bytes()>limits.maxBytes()
                || !Double.isFinite(video.duration()) || video.duration()<=0 || video.duration()>limits.maxDurationSeconds()
                || !Set.of("mp4","mov","webm").contains(video.format()) || video.assetId()==null || video.assetId().isBlank()
                || video.version()<=0 || video.videoUrl()==null || !video.videoUrl().startsWith("https://"))
            throw new BadRequestException("Verified video exceeds the evidence limits or has invalid metadata");
    }
    private void compareClientMetadata(VideoCompleteRequest request,String url,long bytes,String format,double duration) {
        if((request.videoUrl()!=null && !request.videoUrl().equals(url)) || (request.bytes()!=null && request.bytes()!=bytes)
                || (request.format()!=null && !request.format().equals(format))
                || (request.duration()!=null && (!Double.isFinite(request.duration()) || Math.abs(request.duration()-duration)>0.1)))
            throw new ConflictException("Client video metadata does not match the verified upload");
    }
}
