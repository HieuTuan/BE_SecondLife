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
        assertTrue(ListingImageSimilarity.similar(first, fingerprint(resized, "jpeg")));
        assertEquals(first, fingerprint(original, "png"));
    }

    @Test
    void matchesCenteredAndCornerCrops() throws Exception {
        BufferedImage original = scene(17);
        String full = fingerprint(original, "png");
        assertTrue(ListingImageSimilarity.similar(full,
                fingerprint(original.getSubimage(40, 30, 320, 240), "jpeg")));
        assertTrue(ListingImageSimilarity.similar(full,
                fingerprint(original.getSubimage(0, 0, 280, 210), "png")));
    }

    @Test
    void doesNotMatchUnrelatedPhotos() throws Exception {
        assertFalse(ListingImageSimilarity.similar(fingerprint(scene(17), "png"),
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
        assertFalse(ListingImageSimilarity.similar(null, null));
    }

    @Test
    void unsupportedBytesAndInvalidDescriptorsAreIgnored() {
        assertNull(ListingImageSimilarity.fingerprint(new byte[]{1, 2, 3}));
        assertNull(ListingImageSimilarity.fingerprint(null));
        assertFalse(ListingImageSimilarity.similar("invalid", "invalid"));
        assertFalse(ListingImageSimilarity.similar("v1:not-hex", "v1:not-hex"));
    }

    @Test
    void rejectsHugeDeclaredDimensionsBeforeDecodingPixels() throws Exception {
        // A valid PNG signature and IHDR suffice for the reader to inspect dimensions.
        byte[] png = java.util.HexFormat.of().parseHex(
                "89504e470d0a1a0a0000000d494844520000753000007530080200000000000000");
        assertThrows(IllegalArgumentException.class, () -> ListingImageSimilarity.fingerprint(png));
    }

    private static String fingerprint(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return ListingImageSimilarity.fingerprint(bytes.toByteArray());
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
