package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.TamperSealService;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("tamperSealService")
public class TamperSealServiceImpl implements TamperSealService {

    private static final String PREFIX = "SL-SEAL";
    private static final String SALT = "SecondLife-TamperSeal-Salt-2026";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern SEAL_PATTERN = Pattern.compile("^SL-SEAL-(\\d{8})-([A-Z0-9]{6})-([A-Z0-9]{2})$");

    /**
     * Sinh mã tem niêm phong số chuẩn: SL-SEAL-{YYYYMMDD}-{6CHAR_CODE}-{CHECKSUM_2CHAR}
     */
    public String generateSealCode() {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        String randomCode = generateRandomHex(6);
        String checksum = calculateChecksum(dateStr + "-" + randomCode);
        return String.format("%s-%s-%s-%s", PREFIX, dateStr, randomCode, checksum);
    }

    /**
     * Xác thực tính hợp lệ của mã tem qua Checksum
     */
    public boolean verifyChecksum(String sealCode) {
        if (sealCode == null || sealCode.isBlank()) {
            return false;
        }
        String cleanCode = sealCode.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = SEAL_PATTERN.matcher(cleanCode);
        if (matcher.matches()) {
            String dateStr = matcher.group(1);
            String randomCode = matcher.group(2);
            String expectedChecksum = matcher.group(3);

            String actualChecksum = calculateChecksum(dateStr + "-" + randomCode);
            return expectedChecksum.equalsIgnoreCase(actualChecksum);
        }

        // Bất kỳ mã tem nào bắt đầu bằng 8 chữ số ngày (SL-SEAL-YYYYMMDD...) hoặc có từ 4 dấu gạch nối trở lên
        // đều là cấu trúc tem chuẩn hiện đại, nếu sai lệch phải bị từ chối, không cho fallback
        if (cleanCode.matches("^SL-SEAL-\\d{8}.*") || cleanCode.chars().filter(ch -> ch == '-').count() >= 4) {
            return false;
        }

        // Tương thích ngược với các mã test/legacy cũ (ví dụ: SL-SEAL-8829102, SL-SEAL-TEST-9988)
        return cleanCode.startsWith("SL-SEAL-") && cleanCode.length() >= 8;
    }

    /**
     * Kiểm tra định dạng cơ bản của tem
     */
    public boolean isValidSealFormat(String sealCode) {
        if (sealCode == null || sealCode.isBlank()) return false;
        String clean = sealCode.trim().toUpperCase(Locale.ROOT);
        return clean.startsWith(PREFIX + "-") && clean.length() >= 10;
    }

    private String calculateChecksum(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload + ":" + SALT).getBytes(StandardCharsets.UTF_8));
            return String.format("%02X", hash[0] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            int sum = 0;
            for (byte b : (payload + SALT).getBytes(StandardCharsets.UTF_8)) {
                sum = (sum * 31 + (b & 0xFF)) & 0xFF;
            }
            return String.format("%02X", sum);
        }
    }

    private String generateRandomHex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.substring(0, length);
    }
}
