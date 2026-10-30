package com.secondlife.secondlife.common.validation;

import com.secondlife.secondlife.exception.ImageValidationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUploadValidatorTest {
    private static final long LIMIT = 10 * 1024 * 1024;
    private final ImageUploadValidator validator = new ImageUploadValidator(600, 16000, 25000000, 2560);
    private static final Path FIXTURES = Path.of("src/test/resources/image-validation");

    @ParameterizedTest
    @CsvSource({"valid-front.jpg,image/jpeg", "valid-back.png,image/png", "valid-label.webp,image/webp"})
    void genuineImageFormatsAreDecodedAndReencoded(String name, String mime) throws Exception {
        var prepared = validator.prepareMediaImage(file(name, mime), "file", LIMIT);
        var decoded = ImageIO.read(prepared.file().getInputStream());
        assertEquals(1200, decoded.getWidth());
        assertEquals(900, decoded.getHeight());
        assertTrue(prepared.file().getOriginalFilename().matches("[0-9a-f-]{36}\\.(jpg|png)"));
        assertNotEquals(name, prepared.file().getOriginalFilename());
    }

    @ParameterizedTest
    @CsvSource({"fake-jpeg.jpg,image/jpeg", "truncated.jpg,image/jpeg", "truncated.png,image/png",
            "truncated-no-eoi.jpg,image/jpeg", "truncated-entropy-tail.jpg,image/jpeg",
            "png-renamed-as-jpeg.jpg,image/jpeg", "animated.png,image/png", "animated.webp,image/webp",
            "animated.gif,image/gif", "unsupported.svg,image/svg+xml"})
    void invalidOrAnimatedFilesHaveAnIndexed400Error(String name, String mime) throws Exception {
        var input = file(name, mime);
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareMediaImage(input, "images[1]", LIMIT));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("images[1]", failure.getErrors().getFirst().field());
    }

    @ParameterizedTest
    @ValueSource(strings = {"too-small-599.png", "too-wide-16001x600.png", "too-many-pixels-8000x4000.png"})
    void listingDimensionsAreCheckedBeforeImagePreparation(String name) throws Exception {
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareListingImages(List.of(file(name, "image/png"), file("valid-front.jpg", "image/jpeg"),
                        file("valid-label.webp", "image/webp")), 3, 6, LIMIT));
        assertEquals("images[0]", failure.getErrors().getFirst().field());
        assertTrue(failure.getMessage().contains("kích thước"));
    }

    @Test
    void minimum600AndMaximumSixPhotosAreInclusive() throws Exception {
        var prepared = validator.prepareListingImages(List.of(file("boundary-600.png", "image/png"),
                file("valid-front.jpg", "image/jpeg"), file("valid-back.png", "image/png"),
                file("valid-label.webp", "image/webp"), file("valid-left.jpg", "image/jpeg"),
                file("valid-right.jpg", "image/jpeg")), 3, 6, LIMIT);
        assertEquals(6, prepared.size());
    }

    @Test
    void byteLimitIsInclusiveAndAnExtraByteReturns413() throws Exception {
        var source = file("valid-front.jpg", "image/jpeg").getBytes();
        var exact = new MockMultipartFile("images", "exact.jpg", "image/jpeg", Arrays.copyOf(source, (int) LIMIT));
        assertTrue(validator.prepareMediaImage(exact, "file", LIMIT).file().getSize() <= LIMIT);
        var large = new MockMultipartFile("images", "large.jpg", "image/jpeg", new byte[(int) LIMIT + 1]);
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareMediaImage(large, "images[2]", LIMIT));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
        assertEquals("images[2]", failure.getErrors().getFirst().field());
    }

    @Test
    void dimensionsAndFileDamageAreReportedForBothFiles() throws Exception {
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareListingImages(List.of(file("too-small-599.png", "image/png"),
                        file("fake-jpeg.jpg", "image/jpeg"), file("valid-front.jpg", "image/jpeg")), 3, 6, LIMIT));
        assertEquals(List.of("images[0]", "images[1]"), failure.getErrors().stream().map(e -> e.field()).toList());
    }

    @Test
    void repeatedInputIsRejectedEvenUnderAnotherFilename() throws Exception {
        var original = file("valid-front.jpg", "image/jpeg");
        var duplicate = new MockMultipartFile("images", "another-name.jpg", "image/jpeg", original.getBytes());
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareListingImages(List.of(original, duplicate, file("valid-back.png", "image/png")), 3, 6, LIMIT));
        assertEquals("images[1]", failure.getErrors().getFirst().field());
        assertTrue(failure.getMessage().contains("trùng"));
    }

    @Test
    void phoneOrientationIsAppliedAndExifRemoved() throws Exception {
        var prepared = validator.prepareMediaImage(file("phone-orientation-6.jpg", "image/jpeg"), "file", LIMIT);
        byte[] safe = prepared.file().getBytes();
        var image = ImageIO.read(new ByteArrayInputStream(safe));
        assertEquals(800, image.getWidth());
        assertEquals(1200, image.getHeight());
        String raw = new String(safe, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Exif"));
        assertFalse(raw.contains("Metadata must not appear"));
    }

    @ParameterizedTest
    @CsvSource({"1,0,1,2,3", "2,1,0,3,2", "3,3,2,1,0", "4,2,3,0,1",
            "5,0,2,1,3", "6,2,0,3,1", "7,3,1,2,0", "8,1,3,0,2"})
    void allExifOrientationsPlaceTheCorrectPixels(int orientation, int topLeft, int topRight, int bottomLeft, int bottomRight) throws Exception {
        var prepared = validator.prepareMediaImage(file("exif-orientation-" + orientation + ".jpg", "image/jpeg"), "file", LIMIT);
        var image = ImageIO.read(prepared.file().getInputStream());
        assertEquals(orientation >= 5 ? 600 : 800, image.getWidth());
        assertEquals(orientation >= 5 ? 800 : 600, image.getHeight());
        Color[] colors = {Color.RED, new Color(0, 128, 0), Color.BLUE, Color.YELLOW};
        int[] expected = {topLeft, topRight, bottomLeft, bottomRight};
        int[][] positions = {{30, 30}, {image.getWidth() - 30, 30}, {30, image.getHeight() - 30},
                {image.getWidth() - 30, image.getHeight() - 30}};
        for (int index = 0; index < positions.length; index++) {
            Color actual = new Color(image.getRGB(positions[index][0], positions[index][1]));
            Color target = colors[expected[index]];
            assertTrue(Math.abs(actual.getRed() - target.getRed()) < 20);
            assertTrue(Math.abs(actual.getGreen() - target.getGreen()) < 20);
            assertTrue(Math.abs(actual.getBlue() - target.getBlue()) < 20);
        }
    }

    @ParameterizedTest
    @CsvSource({"phone-orientation-6.png,image/png", "phone-orientation-6.webp,image/webp"})
    void pngAndWebpExifAreAlsoApplied(String filename, String mime) throws Exception {
        var prepared = validator.prepareMediaImage(file(filename, mime), "file", LIMIT);
        var image = ImageIO.read(prepared.file().getInputStream());
        assertEquals(800, image.getWidth());
        assertEquals(1200, image.getHeight());
    }

    @Test
    void gpsExifIsRemovedAndTransparencyIsPreserved() throws Exception {
        var gpsInput = file("phone-with-gps.jpg", "image/jpeg");
        assertTrue(new String(gpsInput.getBytes(), StandardCharsets.ISO_8859_1).contains("Exif"));
        var clean = validator.prepareMediaImage(gpsInput, "file", LIMIT).file();
        assertFalse(new String(clean.getBytes(), StandardCharsets.ISO_8859_1).contains("Exif"));
        var transparent = validator.prepareMediaImage(file("valid-transparent.png", "image/png"), "file", LIMIT).file();
        assertEquals("image/png", transparent.getContentType());
        var image = ImageIO.read(transparent.getInputStream());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(255, image.getRGB(400, 300) >>> 24);
    }

    @Test
    void changingExifAloneDoesNotBypassDuplicateDetection() throws Exception {
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareListingImages(List.of(file("valid-front.jpg", "image/jpeg"),
                        file("duplicate-front-exif.jpg", "image/jpeg"), file("valid-back.png", "image/png")), 3, 6, LIMIT));
        assertEquals("images[1]", failure.getErrors().getFirst().field());
        assertTrue(failure.getMessage().contains("trùng"));
    }

    @Test
    void unusedCompressedTextIsSkippedInsteadOfBeingInflated() throws Exception {
        byte[] source = file("valid-back.png", "image/png").getBytes();
        byte[] type = "zTXt".getBytes(StandardCharsets.US_ASCII);
        byte[] optionalText = "Comment\0\0not-a-deflate-stream".getBytes(StandardCharsets.US_ASCII);
        var output = new ByteArrayOutputStream();
        var data = new DataOutputStream(output);
        data.write(source, 0, 33);
        data.writeInt(optionalText.length);
        data.write(type);
        data.write(optionalText);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(optionalText);
        data.writeInt((int) crc.getValue());
        data.write(source, 33, source.length - 33);
        var input = new MockMultipartFile("file", "metadata.png", "image/png", output.toByteArray());
        var prepared = validator.prepareMediaImage(input, "file", LIMIT);
        var image = ImageIO.read(prepared.file().getInputStream());
        assertEquals(1200, image.getWidth());
        assertEquals(900, image.getHeight());
        assertFalse(new String(prepared.file().getBytes(), StandardCharsets.ISO_8859_1).contains("not-a-deflate-stream"));
    }

    @Test
    void byteLimitIsEnforcedEvenIfMultipartReportsAnIncorrectSize() {
        var misleading = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[5]) {
            @Override public long getSize() { return 4; }
        };
        var failure = assertThrows(ImageValidationException.class,
                () -> validator.prepareMediaImage(misleading, "file", 4));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
    }

    @Test
    void largeValidPhotoIsReducedWithoutUpscaling() throws Exception {
        var prepared = validator.prepareMediaImage(file("valid-large-4000x3000.jpg", "image/jpeg"), "file", LIMIT);
        var image = ImageIO.read(prepared.file().getInputStream());
        assertEquals(2560, image.getWidth());
        assertEquals(1920, image.getHeight());
    }

    @Test
    void genericMediaDoesNotRequireTheListingMinimum() throws Exception {
        var prepared = validator.prepareMediaImage(file("too-small-599.png", "image/png"), "file", LIMIT);
        assertEquals(599, ImageIO.read(prepared.file().getInputStream()).getHeight());
    }

    @Test
    void neutralPhotoIsNotMistakenForAnInvalidFile() throws Exception {
        assertTrue(validator.prepareMediaImage(file("valid-blank-white.jpg", "image/jpeg"), "file", LIMIT).file().getSize() > 0);
    }

    @Test
    void actualBytesAllowMobileOctetStreamButRejectSpoofedSpecificMime() throws Exception {
        byte[] data = file("valid-front.jpg", "image/jpeg").getBytes();
        assertTrue(validator.prepareMediaImage(new MockMultipartFile("file", "camera.JPG", "application/octet-stream", data), "file", LIMIT).file().getSize() > 0);
        assertTrue(validator.prepareMediaImage(new MockMultipartFile("file", "camera.jpg", null, data), "file", LIMIT).file().getSize() > 0);
        assertThrows(ImageValidationException.class,
                () -> validator.prepareMediaImage(new MockMultipartFile("file", "camera.jpg", "image/png", data), "file", LIMIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../photo.jpg", "C:\\photo.jpg", "photo.jpg.exe", "camera.heic"})
    void unsafeOrUnsupportedNamesAreRejected(String name) throws Exception {
        var input = new MockMultipartFile("file", name, "image/jpeg", file("valid-front.jpg", "image/jpeg").getBytes());
        assertThrows(ImageValidationException.class, () -> validator.prepareMediaImage(input, "file", LIMIT));
    }

    @Test
    void fewerThanThreeAndMoreThanSixHaveAnImagesFieldError() throws Exception {
        MultipartFile image = file("valid-front.jpg", "image/jpeg");
        for (int count : List.of(0, 1, 2, 7)) {
            var failure = assertThrows(ImageValidationException.class,
                    () -> validator.prepareListingImages(Collections.nCopies(count, image), 3, 6, LIMIT));
            assertEquals("images", failure.getErrors().getFirst().field());
        }
    }

    static MockMultipartFile file(String name, String mime) throws Exception {
        return new MockMultipartFile("images", name, mime, Files.readAllBytes(FIXTURES.resolve(name)));
    }
}
