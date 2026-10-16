package com.secondlife.secondlife.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TamperSealServiceTest {

    private TamperSealService sealService;

    @BeforeEach
    void setUp() {
        sealService = new TamperSealService();
    }

    @Test
    @DisplayName("generateSealCode: Sinh mã tem niêm phong chuẩn có checksum")
    void testGenerateSealCode() {
        String sealCode = sealService.generateSealCode();

        assertNotNull(sealCode);
        assertTrue(sealCode.startsWith("SL-SEAL-"));
        // Cấu trúc SL-SEAL-YYYYMMDD-XXXXXX-CC (tổng 26 ký tự)
        assertEquals(26, sealCode.length());
        assertTrue(sealService.isValidSealFormat(sealCode));
        assertTrue(sealService.verifyChecksum(sealCode));
    }

    @Test
    @DisplayName("verifyChecksum: Phát hiện tem bị sửa đổi checksum hoặc mã ngẫu nhiên")
    void testVerifyChecksumTampered() {
        String validCode = sealService.generateSealCode();
        assertTrue(sealService.verifyChecksum(validCode));

        // Can thiệp vào ký tự checksum cuối cùng
        String tamperedChecksum = validCode.substring(0, validCode.length() - 2) + "00";
        if (tamperedChecksum.equals(validCode)) {
            tamperedChecksum = validCode.substring(0, validCode.length() - 2) + "FF";
        }
        assertFalse(sealService.verifyChecksum(tamperedChecksum));

        // Can thiệp vào phần thân mã ngẫu nhiên (làm sai checksum)
        String tamperedBody = validCode.substring(0, 17) + "99" + validCode.substring(19);
        assertFalse(sealService.verifyChecksum(tamperedBody));
    }

    @Test
    @DisplayName("verifyChecksum: Tương thích ngược với mã tem cũ hoặc test prefix")
    void testLegacyFormatCompatibility() {
        assertTrue(sealService.verifyChecksum("SL-SEAL-TEST-9988"));
        assertTrue(sealService.verifyChecksum("SL-SEAL-8829102"));
        assertFalse(sealService.verifyChecksum("INVALID-CODE"));
        assertFalse(sealService.verifyChecksum(null));
        assertFalse(sealService.verifyChecksum(""));
    }
}
