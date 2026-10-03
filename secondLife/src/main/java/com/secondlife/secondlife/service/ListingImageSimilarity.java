package com.secondlife.secondlife.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Approximate photo evidence for staff review; exact byte identity is handled separately. */
public final class ListingImageSimilarity {
    private static final int MAX_DIMENSION = 16_000;
    private static final long MAX_PIXELS = 25_000_000;
    private static final int SAMPLE_SIZE = 32;
    private static final double[][] COSINES = cosines();

    private ListingImageSimilarity() { }

    /** Returns null for undecodable or low-information images, including legacy fixture bytes. */
    public static String fingerprint(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        if (bytes.length > 20 * 1024 * 1024) throw new IllegalArgumentException("Image file is too large");
        BufferedImage image = decode(bytes);
        if (image == null) return null;
        Signature original = signature(image, 0, 0, image.getWidth(), image.getHeight());
        if (original == null) return null;
        List<Signature> signatures = new ArrayList<>();
        signatures.add(original);
        // Original versus center/corner crops catches common recropping, not arbitrary crops.
        for (int percent : new int[]{90, 80, 70}) {
            int width = Math.max(1, image.getWidth() * percent / 100);
            int height = Math.max(1, image.getHeight() * percent / 100);
            int dx = image.getWidth() - width, dy = image.getHeight() - height;
            for (int[] offset : new int[][]{{dx / 2, dy / 2}, {0, 0}, {dx, 0}, {0, dy}, {dx, dy}}) {
                Signature crop = signature(image, offset[0], offset[1], width, height);
                if (crop != null) signatures.add(crop);
            }
        }
        return "v1:" + String.join(",", signatures.stream().map(Signature::serialize).toList());
    }

    public static boolean similar(String first, String second) {
        List<Signature> left = parse(first), right = parse(second);
        if (left.isEmpty() || right.isEmpty()) return false;
        // Require one entire photo to match: two unrelated images may share a plain corner.
        for (Signature candidate : right) if (matches(left.getFirst(), candidate)) return true;
        for (Signature candidate : left) if (matches(right.getFirst(), candidate)) return true;
        return false;
    }

    private static BufferedImage decode(byte[] bytes) {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image dimensions exceed the supported limit");
                }
                ImageReadParam params = reader.getDefaultReadParam();
                int subsampling = Math.max(1, (Math.max(width, height) + 1023) / 1024);
                params.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return reader.read(0, params);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return null;
        }
    }

    private static Signature signature(BufferedImage source, int x, int y, int width, int height) {
        BufferedImage sample = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sample.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE, x, y, x + width, y + height, null);
        } finally {
            graphics.dispose();
        }
        double[][] gray = new double[SAMPLE_SIZE][SAMPLE_SIZE];
        double sum = 0, squared = 0;
        int red = 0, green = 0, blue = 0;
        for (int row = 0; row < SAMPLE_SIZE; row++) {
            for (int col = 0; col < SAMPLE_SIZE; col++) {
                int rgb = sample.getRGB(col, row);
                int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
                red += r; green += g; blue += b;
                double value = 0.299 * r + 0.587 * g + 0.114 * b;
                gray[row][col] = value;
                sum += value;
                squared += value * value;
            }
        }
        int count = SAMPLE_SIZE * SAMPLE_SIZE;
        if (squared / count - Math.pow(sum / count, 2) < 100) return null;
        double[] coefficients = new double[63];
        int index = 0;
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                if (u == 0 && v == 0) continue;
                double coefficient = 0;
                for (int row = 0; row < SAMPLE_SIZE; row++) {
                    for (int col = 0; col < SAMPLE_SIZE; col++) {
                        coefficient += gray[row][col] * COSINES[u][col] * COSINES[v][row];
                    }
                }
                coefficients[index++] = coefficient;
            }
        }
        double[] sorted = coefficients.clone();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        long hash = 0, edges = 0;
        for (int bit = 0; bit < coefficients.length; bit++) {
            if (coefficients[bit] > median) hash |= 1L << bit;
        }
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (gray[row * 4 + 2][col * 31 / 8] > gray[row * 4 + 2][(col + 1) * 31 / 8]) {
                    edges |= 1L << (row * 8 + col);
                }
            }
        }
        return new Signature(hash, edges, red / count, green / count, blue / count);
    }

    private static boolean matches(Signature first, Signature second) {
        return Long.bitCount(first.hash ^ second.hash) <= 8
                && Long.bitCount(first.edges ^ second.edges) <= 12
                && Math.abs(first.red - second.red) + Math.abs(first.green - second.green)
                + Math.abs(first.blue - second.blue) <= 90;
    }

    private static List<Signature> parse(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("v1:") || descriptor.length() > 626) return List.of();
        try {
            String[] parts = descriptor.substring(3).split(",", -1);
            if (parts.length > 16) return List.of();
            List<Signature> signatures = new ArrayList<>();
            for (String part : parts) {
                if (part.length() != 38) return List.of();
                // Validate the complete string; unsigned longs alone accept a leading plus sign.
                HexFormat.of().parseHex(part);
                signatures.add(new Signature(Long.parseUnsignedLong(part.substring(0, 16), 16),
                        Long.parseUnsignedLong(part.substring(16, 32), 16),
                        Integer.parseInt(part.substring(32, 34), 16), Integer.parseInt(part.substring(34, 36), 16),
                        Integer.parseInt(part.substring(36, 38), 16)));
            }
            return signatures;
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private static double[][] cosines() {
        double[][] result = new double[8][SAMPLE_SIZE];
        for (int frequency = 0; frequency < 8; frequency++) {
            for (int position = 0; position < SAMPLE_SIZE; position++) {
                result[frequency][position] = Math.cos((2 * position + 1) * frequency * Math.PI / (2 * SAMPLE_SIZE));
            }
        }
        return result;
    }

    private record Signature(long hash, long edges, int red, int green, int blue) {
        String serialize() {
            return String.format(Locale.ROOT, "%016x%016x%02x%02x%02x", hash, edges, red, green, blue);
        }
    }
}
