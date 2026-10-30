package com.secondlife.secondlife.common.validation;

import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.exception.ImageValidationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

@Component
public class ImageUploadValidator {
    private static final Map<String, String> EXTENSIONS = Map.of("jpg", "jpeg", "jpeg", "jpeg", "png", "png", "webp", "webp");
    // Bound simultaneous bitmap decoding, not the number of uploaded files on disk.
    private final Semaphore decodeSlots = new Semaphore(2);
    private final int minShortSide;
    private final int maxDimension;
    private final long maxPixels;
    private final int normalizedMaxSide;

    public ImageUploadValidator(
            @Value("${app.listing.image-validation.min-short-side:600}") int minShortSide,
            @Value("${app.listing.image-similarity.max-dimension:16000}") int maxDimension,
            @Value("${app.listing.image-similarity.max-pixels:25000000}") long maxPixels,
            @Value("${app.listing.image-validation.normalized-max-side:2560}") int normalizedMaxSide) {
        if (minShortSide <= 0 || maxDimension < minShortSide || maxPixels <= 0
                || normalizedMaxSide < minShortSide || normalizedMaxSide > maxDimension) {
            throw new IllegalArgumentException("Invalid listing image validation dimensions");
        }
        this.minShortSide = minShortSide;
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
        this.normalizedMaxSide = normalizedMaxSide;
    }

    public record PreparedImage(MultipartFile file, String originalFingerprint, String contentFingerprint) { }

    public List<PreparedImage> prepareListingImages(List<MultipartFile> files, int minimum, int maximum, long maxBytes) {
        if (files == null || files.size() < minimum || files.size() > maximum) {
            throw error(HttpStatus.BAD_REQUEST, "images", "Cần tải từ " + minimum + " đến " + maximum + " ảnh sản phẩm.");
        }
        List<PreparedImage> prepared = new ArrayList<>();
        List<ErrorResponse.FieldErrorItem> errors = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        for (int index = 0; index < files.size(); index++) {
            String field = "images[" + index + "]";
            try {
                PreparedImage image = prepare(files.get(index), field, maxBytes, minShortSide);
                if (!fingerprints.add(image.contentFingerprint())) {
                    throw error(HttpStatus.BAD_REQUEST, field, "Ảnh trùng với một ảnh trước đó. Hãy chọn góc chụp khác.");
                }
                prepared.add(image);
            } catch (ImageValidationException ex) {
                if (ex.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) throw ex;
                errors.addAll(ex.getErrors());
                if (ex.getStatus() == HttpStatus.PAYLOAD_TOO_LARGE) status = ex.getStatus();
            }
        }
        if (!errors.isEmpty()) throw new ImageValidationException(status, errors);
        return List.copyOf(prepared);
    }

    /** Generic media shares safe decoding, without the listing-only minimum resolution/count. */
    public PreparedImage prepareMediaImage(MultipartFile file, String field, long maxBytes) {
        return prepare(file, field, maxBytes, 1);
    }

    private PreparedImage prepare(MultipartFile file, String field, long maxBytes, int minimumSide) {
        if (maxBytes <= 0 || maxBytes >= Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid image byte limit");
        if (file == null || file.isEmpty()) throw error(HttpStatus.BAD_REQUEST, field, "File ảnh không được để trống.");
        if (file.getSize() > maxBytes) throw oversized(field, file.getSize(), maxBytes);
        String extension = extension(file.getOriginalFilename(), field);
        if (!decodeSlots.tryAcquire()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, field, "Máy chủ đang xử lý nhiều ảnh. Vui lòng thử lại sau.");
        }
        try (InputStream stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes((int) maxBytes + 1);
            if (bytes.length > maxBytes) throw oversized(field, bytes.length, maxBytes);
            if (bytes.length == 0) throw error(HttpStatus.BAD_REQUEST, field, "File ảnh không được để trống.");
            ImageMetadata.Info info;
            try {
                info = ImageMetadata.inspect(bytes);
            } catch (IllegalArgumentException ex) {
                throw error(HttpStatus.BAD_REQUEST, field, "Không đọc được nội dung ảnh. Chỉ nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
            }
            if (info.animated()) throw error(HttpStatus.BAD_REQUEST, field, "Không hỗ trợ ảnh động. Hãy chọn ảnh tĩnh JPEG, PNG hoặc WebP.");
            if (!info.format().equals(EXTENSIONS.get(extension))) {
                throw error(HttpStatus.BAD_REQUEST, field, "Đuôi file không khớp định dạng ảnh thực tế.");
            }
            String mime = file.getContentType();
            if (mime != null && !mime.isBlank() && !mime.equalsIgnoreCase("application/octet-stream")) {
                String declared = mime.toLowerCase(Locale.ROOT).trim();
                if (declared.equals("image/jpg")) declared = "image/jpeg";
                if (!declared.equals("image/" + info.format())) {
                    throw error(HttpStatus.BAD_REQUEST, field, "Content-Type không khớp định dạng ảnh thực tế.");
                }
            }
            return decodeAndPrepare(bytes, info, file.getName(), field, maxBytes, minimumSide);
        } catch (ImageValidationException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw error(HttpStatus.BAD_REQUEST, field, "Ảnh bị hỏng hoặc không thể giải mã. Vui lòng chọn lại ảnh.");
        } finally {
            decodeSlots.release();
        }
    }

    private PreparedImage decodeAndPrepare(byte[] bytes, ImageMetadata.Info info, String partName,
                                           String field, long maxBytes, int minimumSide) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw error(HttpStatus.BAD_REQUEST, field, "Không có bộ giải mã cho định dạng ảnh này.");
            ImageReader reader = readers.next();
            BufferedImage decoded = null;
            BufferedImage normalized = null;
            try {
                // EXIF orientation was inspected above; do not inflate unused metadata.
                reader.setInput(input, false, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                        || (long) width * height > maxPixels) {
                    throw error(HttpStatus.BAD_REQUEST, field, "Ảnh có kích thước " + width + " × " + height
                            + " px. Tối đa " + maxDimension + " px/cạnh và " + maxPixels + " pixel.");
                }
                if (Math.min(width, height) < minimumSide) {
                    throw error(HttpStatus.BAD_REQUEST, field, "Ảnh có kích thước " + width + " × " + height
                            + " px. Cạnh ngắn phải đạt ít nhất " + minimumSide + " px.");
                }
                if (reader.getNumImages(true) != 1) throw error(HttpStatus.BAD_REQUEST, field, "Chỉ hỗ trợ ảnh tĩnh một khung hình.");
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                ImageReadParam params = reader.getDefaultReadParam();
                // Downsample while preserving the listing minimum for unusually wide/tall photos.
                int sampling = Math.max(1, Math.min(Math.max(width, height) / normalizedMaxSide,
                        Math.min(width, height) / minimumSide));
                params.setSourceSubsampling(sampling, sampling, 0, 0);
                decoded = reader.read(0, params);
                if (decoded == null || warned[0]) throw new IOException("Incomplete image decode");
                normalized = normalize(decoded, info.orientation(), minimumSide);
                boolean alpha = normalized.getColorModel().hasAlpha();
                String format = alpha ? "png" : "jpg";
                byte[] safeBytes = encode(normalized, format);
                if (safeBytes.length > maxBytes) throw oversized(field, safeBytes.length, maxBytes);
                MultipartFile safeFile = new SafeImage(partName, UUID.randomUUID() + "." + format,
                        alpha ? "image/png" : "image/jpeg", safeBytes);
                return new PreparedImage(safeFile, sha256(bytes), sha256(safeBytes));
            } finally {
                reader.dispose();
                if (decoded != null) decoded.flush();
                if (normalized != null) normalized.flush();
            }
        }
    }

    private BufferedImage normalize(BufferedImage source, int orientation, int minimumSide) {
        int width = source.getWidth(), height = source.getHeight();
        boolean swapped = orientation >= 5;
        int orientedWidth = swapped ? height : width, orientedHeight = swapped ? width : height;
        double scale = Math.min(1, Math.max((double) normalizedMaxSide / Math.max(orientedWidth, orientedHeight),
                (double) minimumSide / Math.min(orientedWidth, orientedHeight)));
        int targetWidth = Math.max(1, (int) Math.round(orientedWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(orientedHeight * scale));
        BufferedImage result = new BufferedImage(targetWidth, targetHeight,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.scale((double) targetWidth / orientedWidth, (double) targetHeight / orientedHeight);
            AffineTransform transform = switch (orientation) {
                case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
                case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
                case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
                case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
                case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
                case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
                case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
                default -> new AffineTransform();
            };
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("Missing image writer");
        var writer = writers.next();
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpg")) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.9f);
            }
            // No source metadata is passed: EXIF, GPS, comments and appended bytes are removed.
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static String extension(String name, String field) {
        if (name == null || name.isBlank() || name.length() > 255 || name.chars().anyMatch(c -> c < 32)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw error(HttpStatus.BAD_REQUEST, field, "Tên file ảnh không hợp lệ.");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!EXTENSIONS.containsKey(extension)) {
            throw error(HttpStatus.BAD_REQUEST, field, "Định dạng file không hợp lệ. Chỉ nhận JPEG, PNG, WebP tĩnh; HEIC cần chuyển sang JPEG trước khi gửi.");
        }
        return extension;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static ImageValidationException oversized(String field, long size, long maximum) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, field, "Dung lượng ảnh " + size + " byte vượt giới hạn " + maximum + " byte.");
    }

    private static ImageValidationException error(HttpStatus status, String field, String message) {
        return new ImageValidationException(status, List.of(new ErrorResponse.FieldErrorItem(field, message)));
    }

    private record SafeImage(String name, String originalFilename, String contentType, byte[] content) implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), content); }
    }
}
