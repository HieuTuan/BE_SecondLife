package com.secondlife.secondlife.service.shipping;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VietnameseAdministrativeAreasTest {
    @Test void optimizedLookupPreservesReferenceResultsIncludingWardEdgesAndOutsidePoints() throws Exception {
        var resource=new org.springframework.core.io.ClassPathResource("geocoding/index-reference-points.csv.gz");
        int count=0;
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.util.zip.GZIPInputStream(resource.getInputStream()),java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                var fields=line.split(",");
                var actual=VietnameseAdministrativeAreas.locate(Double.parseDouble(fields[0]),Double.parseDouble(fields[1]));
                assertEquals(fields[2],actual==null?"-":actual.code(),"Coordinate: "+line);
                count++;
            }
        }
        assertEquals(10052,count);
    }
    @Test void ringDistinguishesInteriorEdgeAndExterior() {
        double[] square = {0,0, 2,0, 2,2, 0,2, 0,0};
        assertEquals(1,VietnameseAdministrativeAreas.position(square,1,1));
        assertEquals(2,VietnameseAdministrativeAreas.position(square,0,1));
        assertEquals(2,VietnameseAdministrativeAreas.position(square,0,0));
        assertEquals(0,VietnameseAdministrativeAreas.position(square,3,1));
    }
    @Test void rayPruningPreservesHorizontalVerticalAndSlopedBoundaryCases() {
        double[] triangle={0,0, 2,2, 2,0, 0,0};
        assertEquals(2,VietnameseAdministrativeAreas.position(triangle,1,1));
        assertEquals(2,VietnameseAdministrativeAreas.position(triangle,Math.nextUp(1.0),1));
        assertEquals(2,VietnameseAdministrativeAreas.position(triangle,1,0));
        assertEquals(1,VietnameseAdministrativeAreas.position(triangle,1,0.5));
        assertEquals(0,VietnameseAdministrativeAreas.position(triangle,1,1.01));
        assertEquals(0,VietnameseAdministrativeAreas.position(triangle,2.01,1));
    }
    @Test void mergedProvinceAndIslandAreResolvedWithoutNearestWardGuessing() {
        assertEquals("Phường Dĩ An",VietnameseAdministrativeAreas.locate(10.906,106.769).wardName());
        assertNotNull(VietnameseAdministrativeAreas.locate(8.683,106.601));
        assertNull(VietnameseAdministrativeAreas.locate(10.045,105.7469));
        assertNull(VietnameseAdministrativeAreas.locate(0,0));
        assertNull(VietnameseAdministrativeAreas.locate(Double.NaN,106));
    }
}
