package com.secondlife.secondlife.integration.storage;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
public interface ReportEvidenceStorage {
    record StoredImage(String storageId,String contentType,long size) { }
    StoredImage upload(MultipartFile file) throws IOException;
    byte[] read(String storageId) throws IOException;
}
