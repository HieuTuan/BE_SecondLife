package com.secondlife.secondlife.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ListingImageSimilarityTest {
    private static final ListingImageSimilarity similarity = new ListingImageSimilarity(
            16_000, 25_000_000L, 20 * 1024 * 1024L, 100, 8, 12, 90);

    @Test
    void supportedWebpUploadsHaveAnImageIoDecoder() {
        var readers = ImageIO.getImageReadersByFormatName("webp");
        assertTrue(readers.hasNext(), "The WebP decoder must be present at runtime");
        readers.next().dispose();
    }

    @Test
    void matchesJpegReencodingAndResizing() throws Exception {
        BufferedImage original = scene(17);
        BufferedImage resized = new BufferedImage(180, 135, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(original, 0, 0, 180, 135, null);
        graphics.dispose();
        String first = fingerprint(original, "png");
        assertNotNull(first);
        assertTrue(similarity.similar(first, fingerprint(resized, "jpeg")));
        assertEquals(first, fingerprint(original, "png"));
    }

    @Test
    void matchesCenteredAndCornerCrops() throws Exception {
        BufferedImage original = scene(17);
        String full = fingerprint(original, "png");
        assertTrue(similarity.similar(full,
                fingerprint(original.getSubimage(40, 30, 320, 240), "jpeg")));
        assertTrue(similarity.similar(full,
                fingerprint(original.getSubimage(0, 0, 280, 210), "png")));
    }

    @Test
    void doesNotMatchUnrelatedPhotos() throws Exception {
        assertFalse(similarity.similar(fingerprint(scene(17), "png"),
                fingerprint(scene(92), "jpeg")));
    }

    @Test
    void flatImagesDoNotProduceVisualMatches() throws Exception {
        BufferedImage flat = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flat.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 400, 300);
        graphics.dispose();
        assertNull(fingerprint(flat, "png"));
        assertFalse(similarity.similar(null, null));
    }

    @Test
    void unsupportedBytesAndInvalidDescriptorsAreIgnored() {
        assertNull(similarity.fingerprint(new byte[]{1, 2, 3}));
        assertNull(similarity.fingerprint(null));
        assertFalse(similarity.similar("invalid", "invalid"));
        assertFalse(similarity.similar("v1:not-hex", "v1:not-hex"));
    }

    @Test
    void rejectsHugeDeclaredDimensionsBeforeDecodingPixels() throws Exception {
        // A valid PNG signature and IHDR suffice for the reader to inspect dimensions.
        byte[] png = java.util.HexFormat.of().parseHex(
                "89504e470d0a1a0a0000000d494844520000753000007530080200000000000000");
        assertThrows(IllegalArgumentException.class, () -> similarity.fingerprint(png));
    }

    @Test
    void usesConfiguredSimilarityTolerances() {
        String first = "v1:00000000000000000000000000000000101010";
        String second = "v1:00000000000000010000000000000001111010";
        var strict = new ListingImageSimilarity(16_000, 25_000_000L, 20 * 1024 * 1024L, 100, 0, 0, 0);
        var tolerant = new ListingImageSimilarity(16_000, 25_000_000L, 20 * 1024 * 1024L, 100, 1, 1, 1);

        assertFalse(strict.similar(first, second));
        assertTrue(tolerant.similar(first, second));
    }

    @Test
    void enforcesConfiguredImageLimits() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(scene(17), "png", bytes));
        byte[] png = bytes.toByteArray();
        var dimensionLimit = new ListingImageSimilarity(300, 25_000_000L, 20 * 1024 * 1024L, 100, 8, 12, 90);
        var pixelLimit = new ListingImageSimilarity(16_000, 100_000L, 20 * 1024 * 1024L, 100, 8, 12, 90);
        var byteLimit = new ListingImageSimilarity(16_000, 25_000_000L, png.length - 1L, 100, 8, 12, 90);

        assertThrows(IllegalArgumentException.class, () -> dimensionLimit.fingerprint(png));
        assertThrows(IllegalArgumentException.class, () -> pixelLimit.fingerprint(png));
        assertThrows(IllegalArgumentException.class, () -> byteLimit.fingerprint(png));
    }

    private static String fingerprint(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return similarity.fingerprint(bytes.toByteArray());
    }

    private static BufferedImage scene(long seed) {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(185, 175, 160));
        graphics.fillRect(0, 0, 400, 300);
        Random random = new Random(seed);
        for (int i = 0; i < 45; i++) {
            graphics.setColor(new Color(random.nextInt(220), random.nextInt(220), random.nextInt(220)));
            int x = random.nextInt(350), y = random.nextInt(260);
            int width = 20 + random.nextInt(100), height = 20 + random.nextInt(90);
            if (i % 2 == 0) graphics.fillOval(x, y, width, height);
            else graphics.fillRect(x, y, width, height);
        }
        graphics.dispose();
        return image;
    }
}
