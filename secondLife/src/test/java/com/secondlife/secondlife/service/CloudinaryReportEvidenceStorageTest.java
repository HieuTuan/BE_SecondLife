package com.secondlife.secondlife.service;

import com.secondlife.secondlife.integration.storage.CloudinaryReportEvidenceStorage;
import com.cloudinary.*;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class CloudinaryReportEvidenceStorageTest {
    @Test void usesAuthenticatedStorageAndNeverReturnsAPublicOrSignedDeliveryUrl() throws Exception {
        Cloudinary cloud=spy(new Cloudinary(ObjectUtils.asMap("cloud_name","test-cloud","api_key","test-key","api_secret","test-secret")));
        Uploader uploader=mock(Uploader.class); doReturn(uploader).when(cloud).uploader();
        when(uploader.upload(any(byte[].class),anyMap())).thenAnswer(i -> {
            Map<?,?> options=i.getArgument(1);
            assertEquals("authenticated",options.get("type")); assertEquals("image",options.get("resource_type"));
            assertEquals(false,options.get("overwrite")); assertEquals(5000,options.get("timeout"));
            return Map.of("public_id",options.get("public_id"),"type","authenticated","format","png");
        });
        var storage=new CloudinaryReportEvidenceStorage(cloud,5000,1000000);
        var image=storage.upload(new MockMultipartFile("file","image.png","image/png",new byte[]{1,2,3}));
        assertTrue(image.storageId().startsWith("report-evidence/")); assertEquals("image/png",image.contentType());
        assertThrows(java.io.IOException.class,()->storage.read("../../../secret"));
    }
}
