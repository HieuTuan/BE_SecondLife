package com.secondlife.secondlife.integration.storage;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryReportEvidenceStorage implements ReportEvidenceStorage {
    private final Cloudinary cloudinary;
    private final RestClient client;
    private final long maxBytes;
    private final int timeoutMs;
    public CloudinaryReportEvidenceStorage(Cloudinary cloudinary,@Value("${app.reputation.storage-timeout-ms}") int timeoutMs,
            @Value("${app.media.max-file-size-bytes}") long maxBytes) {
        if(timeoutMs<=0 || maxBytes<=0 || maxBytes>=Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid report evidence storage limits");
        this.cloudinary=cloudinary; this.timeoutMs=timeoutMs; this.maxBytes=maxBytes;
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(timeoutMs); factory.setReadTimeout(timeoutMs);
        this.client=RestClient.builder().requestFactory(factory).build();
    }
    public StoredImage upload(MultipartFile file) throws IOException {
        String id="report-evidence/"+UUID.randomUUID();
        Map<?,?> result=cloudinary.uploader().upload(file.getBytes(),ObjectUtils.asMap("resource_type","image","type","authenticated",
                "public_id",id,"overwrite",false,"timeout",timeoutMs));
        if(!id.equals(result.get("public_id")) || !"authenticated".equals(result.get("type"))) throw new IOException("Invalid private storage response");
        String mime=switch(String.valueOf(result.get("format"))) { case "jpg","jpeg" -> "image/jpeg"; case "png" -> "image/png"; case "webp" -> "image/webp"; default -> throw new IOException("Unsupported evidence image format"); };
        return new StoredImage(id,mime,file.getSize());
    }
    public byte[] read(String storageId) throws IOException {
        // Signed provider URL stays inside BE; clients receive bytes only after report authorization.
        if(!storageId.matches("report-evidence/[0-9a-f-]{36}")) throw new IOException("Invalid evidence storage reference");
        String signedUrl=cloudinary.url().secure(true).type("authenticated").signed(true).generate(storageId);
        try {
            return client.get().uri(signedUrl).exchange((request,response)-> {
                if(!response.getStatusCode().is2xxSuccessful()) throw new IOException("Evidence storage response failed");
                byte[] bytes=response.getBody().readNBytes((int)maxBytes+1);
                if(bytes.length==0 || bytes.length>maxBytes) throw new IOException("Invalid evidence storage size");
                return bytes;
            });
        } catch(RuntimeException ex) { throw new IOException("Evidence storage read failed"); }
    }
}
