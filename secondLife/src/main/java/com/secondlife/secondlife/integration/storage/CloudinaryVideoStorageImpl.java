package com.secondlife.secondlife.integration.storage;

import com.cloudinary.Cloudinary;
import com.secondlife.secondlife.config.VideoUploadProperties;
import com.secondlife.secondlife.exception.VideoUploadException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@EnableConfigurationProperties(VideoUploadProperties.class)
public class CloudinaryVideoStorageImpl implements CloudinaryVideoStorage {
    private static final Pattern PUBLIC_ID = Pattern.compile(
            "report-evidence/videos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<String> FORMATS = Set.of("mp4", "mov", "webm");

    private final Cloudinary cloudinary;
    private final VideoUploadProperties properties;

    public CloudinaryVideoStorageImpl(Cloudinary cloudinary, VideoUploadProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public SignedUpload sign(String publicId) {
        if (!validPublicId(publicId)) {
            throw new IllegalArgumentException("Invalid video storage reference");
        }
        if (!StringUtils.hasText(properties.uploadPreset()) || !StringUtils.hasText(cloudinary.config.cloudName)
                || !StringUtils.hasText(cloudinary.config.apiKey) || !StringUtils.hasText(cloudinary.config.apiSecret)) {
            throw new VideoUploadException("Video upload is not configured", 503);
        }
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> parameters = Map.of(
                "timestamp", Long.toString(timestamp),
                "upload_preset", properties.uploadPreset(),
                "public_id", publicId,
                "type", "authenticated",
                "overwrite", "false",
                "allowed_formats", "mp4,mov,webm");
        try {
            // resource_type belongs in the endpoint, outside Cloudinary's signed parameter string.
            String signature = cloudinary.apiSignRequest(new HashMap<>(parameters), cloudinary.config.apiSecret);
            String uploadUrl = cloudinary.cloudinaryApiUrl("upload", Map.of("resource_type", "video"));
            return new SignedUpload(signature, timestamp, cloudinary.config.apiKey, cloudinary.config.cloudName,
                    uploadUrl, parameters);
        } catch (RuntimeException exception) {
            throw new VideoUploadException("Unable to prepare video upload", 502);
        }
    }

    @Override
    public VerifiedVideo inspect(String publicId) throws IOException {
        if (!validPublicId(publicId)) {
            throw new IOException("Invalid video storage reference");
        }
        // http5 2.0.0 accepts seconds for all three timeout options.
        int timeoutSeconds = (int) Math.max(1, (properties.timeoutMs() + 999L) / 1000L);
        try {
            Map<?, ?> resource = cloudinary.api().resource(publicId, Map.of(
                    "resource_type", "video", "type", "authenticated", "media_metadata", true,
                    "timeout", timeoutSeconds, "connect_timeout", timeoutSeconds,
                    "connection_request_timeout", timeoutSeconds));
            if (!publicId.equals(resource.get("public_id")) || !"video".equals(resource.get("resource_type"))
                    || !"authenticated".equals(resource.get("type"))) {
                throw new IOException("Unexpected video storage metadata");
            }
            String format = text(resource, "format");
            long bytes = positiveInteger(resource, "bytes");
            double duration = positiveNumber(resource, "duration");
            if (!FORMATS.contains(format) || bytes > properties.maxBytes()
                    || duration > properties.maxDurationSeconds()) {
                throw new IOException("Uploaded video exceeds supported limits");
            }
            // Cloudinary uses resource_type=video for audio too; require real video dimensions.
            positiveNumber(resource, "width");
            positiveNumber(resource, "height");
            if (resource.containsKey("video")) {
                if (!(resource.get("video") instanceof Map<?, ?> video) || video.isEmpty()
                        || (video.containsKey("codec") && (!StringUtils.hasText(String.valueOf(video.get("codec")))
                        || video.get("codec") == null || "none".equals(video.get("codec"))))) {
                    throw new IOException("Uploaded asset has no video stream");
                }
            }
            String videoUrl = text(resource, "secure_url");
            URI uri = URI.create(videoUrl);
            if (!"https".equals(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IOException("Invalid video storage URL");
            }
            return new VerifiedVideo(publicId, videoUrl, bytes, format, duration,
                    text(resource, "asset_id"), positiveInteger(resource, "version"));
        } catch (Exception exception) {
            // Provider exceptions can contain credentials or signed request details.
            throw new IOException("Unable to verify uploaded video");
        }
    }

    @Override
    public String playback(String publicId, String format, Instant expiresAt) throws IOException {
        if (!validPublicId(publicId) || format == null || !FORMATS.contains(format)
                || expiresAt == null || expiresAt.getEpochSecond() <= Instant.now().getEpochSecond()) {
            throw new IOException("Invalid video playback reference or expiry");
        }
        try {
            return cloudinary.privateDownload(publicId, format, Map.of(
                    "resource_type", "video", "type", "authenticated", "attachment", false,
                    "expires_at", expiresAt.getEpochSecond()));
        } catch (Exception exception) {
            throw new IOException("Unable to prepare video playback");
        }
    }

    private static boolean validPublicId(String publicId) {
        return publicId != null && PUBLIC_ID.matcher(publicId).matches();
    }

    private static String text(Map<?, ?> resource, String key) throws IOException {
        if (!(resource.get(key) instanceof String value) || !StringUtils.hasText(value)) {
            throw new IOException("Missing video storage metadata");
        }
        return value;
    }

    private static double positiveNumber(Map<?, ?> resource, String key) throws IOException {
        if (!(resource.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue())
                || value.doubleValue() <= 0) {
            throw new IOException("Invalid video storage metadata");
        }
        return value.doubleValue();
    }

    private static long positiveInteger(Map<?, ?> resource, String key) throws IOException {
        double value = positiveNumber(resource, key);
        long integer = ((Number) resource.get(key)).longValue();
        if (integer <= 0 || value != integer) {
            throw new IOException("Invalid video storage metadata");
        }
        return integer;
    }
}