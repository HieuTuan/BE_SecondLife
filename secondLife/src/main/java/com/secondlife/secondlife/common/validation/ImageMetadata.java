package com.secondlife.secondlife.common.validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Bounded container metadata inspection, before ImageIO allocates a bitmap. */
final class ImageMetadata {
    record Info(String format, int orientation, boolean animated) { }

    private ImageMetadata() { }

    static Info inspect(byte[] data) {
        if (data.length >= 3 && unsigned(data[0]) == 255 && unsigned(data[1]) == 216 && unsigned(data[2]) == 255) {
            return jpeg(data);
        }
        if (data.length >= 8 && ByteBuffer.wrap(data).getLong() == 0x89504e470d0a1a0aL) return png(data);
        if (data.length >= 12 && tag(data, 0).equals("RIFF") && tag(data, 8).equals("WEBP")) return webp(data);
        throw new IllegalArgumentException("Unsupported image signature");
    }

    private static Info jpeg(byte[] data) {
        int position = 2;
        int orientation = 1;
        while (position + 4 <= data.length) {
            if (unsigned(data[position++]) != 255) break;
            while (position < data.length && unsigned(data[position]) == 255) position++;
            if (position >= data.length) break;
            int marker = unsigned(data[position++]);
            if (marker == 218 || marker == 217) break;
            if (marker >= 208 && marker <= 215) continue;
            int length = (unsigned(data[position]) << 8) | unsigned(data[position + 1]);
            if (length < 2 || (long) position + length > data.length) throw new IllegalArgumentException("Truncated JPEG");
            if (marker == 225 && length >= 8 && tag(data, position + 2).equals("Exif")) {
                orientation = tiffOrientation(data, position + 8, length - 8);
            }
            position += length;
        }
        return new Info("jpeg", orientation, false);
    }

    private static Info png(byte[] data) {
        int position = 8;
        int orientation = 1;
        boolean animated = false;
        while (position + 12 <= data.length) {
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(data, position, 4).getInt());
            long next = (long) position + 12 + length;
            if (next > data.length) throw new IllegalArgumentException("Truncated PNG");
            int size = (int) length;
            String type = tag(data, position + 4);
            CRC32 crc = new CRC32();
            crc.update(data, position + 4, size + 4);
            long declaredCrc = Integer.toUnsignedLong(ByteBuffer.wrap(data, position + 8 + size, 4).getInt());
            if (crc.getValue() != declaredCrc) throw new IllegalArgumentException("Corrupt PNG chunk");
            if (type.equals("acTL")) animated = true;
            if (type.equals("eXIf")) orientation = tiffOrientation(data, position + 8, size);
            if (type.equals("IEND")) return new Info("png", orientation, animated);
            position = (int) next;
        }
        throw new IllegalArgumentException("Missing PNG end marker");
    }

    private static Info webp(byte[] data) {
        long end = Integer.toUnsignedLong(ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8;
        if (end > data.length || end < 12) throw new IllegalArgumentException("Truncated WebP");
        int position = 12;
        int orientation = 1;
        boolean animated = false;
        while ((long) position + 8 <= end) {
            String type = tag(data, position);
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(data, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            long next = (long) position + 8 + length + (length & 1);
            if (next > end) throw new IllegalArgumentException("Truncated WebP chunk");
            if (type.equals("ANIM") || type.equals("ANMF")
                    || (type.equals("VP8X") && length > 0 && (data[position + 8] & 2) != 0)) animated = true;
            if (type.equals("EXIF")) {
                int start = position + 8;
                int skip = length >= 6 && tag(data, start).equals("Exif") ? 6 : 0;
                orientation = tiffOrientation(data, start + skip, (int) length - skip);
            }
            position = (int) next;
        }
        if (position != end) throw new IllegalArgumentException("Invalid WebP chunk size");
        return new Info("webp", orientation, animated);
    }

    private static int tiffOrientation(byte[] bytes, int offset, int length) {
        if (length < 8 || offset < 0 || (long) offset + length > bytes.length) return 1;
        ByteBuffer input = ByteBuffer.wrap(bytes, offset, length).slice();
        short order = input.getShort(0);
        if (order != 0x4949 && order != 0x4d4d) return 1;
        input.order(order == 0x4949 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (input.getShort(2) != 42) return 1;
        long directory = Integer.toUnsignedLong(input.getInt(4));
        if (directory + 2 > length) return 1;
        int count = Short.toUnsignedInt(input.getShort((int) directory));
        for (int index = 0; index < count; index++) {
            long entry = directory + 2 + 12L * index;
            if (entry + 12 > length) return 1;
            int start = (int) entry;
            if (Short.toUnsignedInt(input.getShort(start)) == 274
                    && input.getShort(start + 2) == 3 && input.getInt(start + 4) == 1) {
                int value = Short.toUnsignedInt(input.getShort(start + 8));
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int unsigned(byte value) { return value & 255; }
}
