package com.secondlife.secondlife.service;
import com.secondlife.secondlife.dto.request.VideoCompleteRequest;
import com.secondlife.secondlife.dto.request.VideoSignatureRequest;
import com.secondlife.secondlife.dto.response.VideoMetadataResponse;
import com.secondlife.secondlife.dto.response.VideoPlaybackResponse;
import com.secondlife.secondlife.dto.response.VideoSignatureResponse;
import java.util.UUID;
public interface VideoUploadService {
    VideoSignatureResponse signature(UUID ownerId, VideoSignatureRequest request);
    VideoMetadataResponse complete(UUID ownerId, VideoCompleteRequest request);
    VideoPlaybackResponse playback(UUID actorId,boolean reviewer,UUID uploadId);
}
