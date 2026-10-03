package com.secondlife.secondlife.integration;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppliedV19ChecksumTest {
    @Test void preservesChecksumOfV19AlreadyAppliedOnExistingDatabase() throws Exception {
        CRC32 checksum = new CRC32();
        for (String line : Files.readAllLines(Path.of("src/main/resources/db/migration/V19__main_flow_listing_valuation.sql"), StandardCharsets.UTF_8))
            checksum.update(line.getBytes(StandardCharsets.UTF_8));
        assertEquals(1319288901, (int) checksum.getValue(), "Applied V19 must not change; additions belong in V20");
    }
}
