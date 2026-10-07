package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptFileClient {
    private static final String PATH = "/file-service/v1/addFile";
    private final VnptHttpClient httpClient;
    private final long maxImageBytes;

    public VnptFileClient(VnptHttpClient httpClient,
            @Value("${app.ekyc.vnpt.max-image-bytes}") long maxImageBytes) {
        this.httpClient = httpClient;
        this.maxImageBytes = maxImageBytes;
    }

    public void validateImage(byte[] image) {
        imageType(image);
    }

    public String upload(byte[] image, String role) {
        MediaType imageType = imageType(image);
        String filename = role + (MediaType.IMAGE_PNG.equals(imageType) ? ".png" : ".jpg");
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(imageType);
        ByteArrayResource resource = new ByteArrayResource(image) {
            @Override public String getFilename() { return filename; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("title", filename);
        body.add("description", filename);
        try {
            // VNPT sheet says application/json while its body declares a file field.
            // Existing integration sends multipart; keep this format isolated until VNPT confirms it.
            JsonNode root = httpClient.postMultipart(PATH, body);
            String hash = root.path("object").path("hash").asText();
            if (hash.isBlank()) {
                throw new VnptUploadException(new VnptApiException(
                        "VNPT file hash missing", 502, PATH, "MISSING_HASH"));
            }
            return hash;
        } catch (VnptAuthenticationException ex) {
            throw ex;
        } catch (VnptUploadException ex) {
            throw ex;
        } catch (VnptApiException ex) {
            throw new VnptUploadException(ex);
        }
    }

    private MediaType imageType(byte[] bytes) {
        if (bytes == null || bytes.length < 4 || bytes.length > maxImageBytes) {
            throw new BadRequestException("eKYC image must be a nonempty JPEG or PNG up to " + maxImageBytes + " bytes");
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return MediaType.IMAGE_JPEG;
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return MediaType.IMAGE_PNG;
        }
        throw new BadRequestException("eKYC image must be a JPEG or PNG file");
    }
}
