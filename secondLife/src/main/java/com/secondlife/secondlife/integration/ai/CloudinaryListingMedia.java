package com.secondlife.secondlife.integration.ai;

import com.secondlife.secondlife.exception.AiProviderException;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.Locale;

final class CloudinaryListingMedia {
    private CloudinaryListingMedia() {
    }

    static Media trustedImage(String imageUrl, String cloudName, String operation) {
        if (cloudName == null || !cloudName.matches("[A-Za-z0-9_-]+"))
            throw new AiProviderException(operation + " image storage is not configured");
        URI uri = URI.create(imageUrl);
        String path = uri.getRawPath();
        String prefix = "/" + cloudName + "/image/upload/";
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || path == null || !path.startsWith(prefix) || path.length() <= prefix.length()
                || path.contains("%") || !uri.normalize().getRawPath().equals(path))
            throw new AiProviderException(operation + " images must use the configured Cloudinary storage");
        if ("http".equalsIgnoreCase(uri.getScheme())) uri = URI.create("https" + imageUrl.substring(imageUrl.indexOf(':')));
        String lowerPath = path.toLowerCase(Locale.ROOT);
        String mime;
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) mime = "image/jpeg";
        else if (lowerPath.endsWith(".png")) mime = "image/png";
        else if (lowerPath.endsWith(".webp")) mime = "image/webp";
        else throw new AiProviderException(operation + " supports JPEG, PNG and WEBP images");
        return new Media(MimeTypeUtils.parseMimeType(mime), uri);
    }
}
