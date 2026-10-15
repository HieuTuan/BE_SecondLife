package com.secondlife.secondlife.integration;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;
class AppliedV49ChecksumTest {
    @Test void preservesV49AlreadyAppliedByDevForDeviceTokens() throws Exception {
        Path source;
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            var versions=files.filter(p->p.getFileName().toString().startsWith("V49__")).toList();
            assertEquals(1,versions.size());source=versions.getFirst();
        }
        var crc=new CRC32();
        for(var line:Files.readAllLines(source,StandardCharsets.UTF_8)) crc.update(line.getBytes(StandardCharsets.UTF_8));
        assertEquals(-193514176,(int)crc.getValue(),"V49 belongs to the applied device-token migration; bank checkout must use a separate version");
    }
}
