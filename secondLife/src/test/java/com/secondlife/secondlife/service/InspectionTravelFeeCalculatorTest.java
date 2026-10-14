package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionTravelTier;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionTravelFeeCalculatorTest {

    @Mock
    private InspectionTravelTierRepository tierRepository;

    @InjectMocks
    private InspectionTravelFeeCalculator calculator;

    private List<InspectionTravelTier> defaultTiers;

    @BeforeEach
    void setUp() {
        InspectionTravelTier t1 = new InspectionTravelTier();
        t1.setTierNumber(1);
        t1.setName("0-5 km");
        t1.setFromKm(BigDecimal.valueOf(0.0));
        t1.setToKm(BigDecimal.valueOf(5.0));
        t1.setPricePerKm(BigDecimal.valueOf(2000.00));
        t1.setStatus("ACTIVE");

        InspectionTravelTier t2 = new InspectionTravelTier();
        t2.setTierNumber(2);
        t2.setName("5-10 km");
        t2.setFromKm(BigDecimal.valueOf(5.0));
        t2.setToKm(BigDecimal.valueOf(10.0));
        t2.setPricePerKm(BigDecimal.valueOf(2500.00));
        t2.setStatus("ACTIVE");

        InspectionTravelTier t3 = new InspectionTravelTier();
        t3.setTierNumber(3);
        t3.setName("10-15 km");
        t3.setFromKm(BigDecimal.valueOf(10.0));
        t3.setToKm(BigDecimal.valueOf(15.0));
        t3.setPricePerKm(BigDecimal.valueOf(3000.00));
        t3.setStatus("ACTIVE");

        InspectionTravelTier t4 = new InspectionTravelTier();
        t4.setTierNumber(4);
        t4.setName("15-20 km");
        t4.setFromKm(BigDecimal.valueOf(15.0));
        t4.setToKm(BigDecimal.valueOf(20.0));
        t4.setPricePerKm(BigDecimal.valueOf(3500.00));
        t4.setStatus("ACTIVE");

        InspectionTravelTier t5 = new InspectionTravelTier();
        t5.setTierNumber(5);
        t5.setName("20-30 km");
        t5.setFromKm(BigDecimal.valueOf(20.0));
        t5.setToKm(BigDecimal.valueOf(30.0));
        t5.setPricePerKm(BigDecimal.valueOf(4000.00));
        t5.setStatus("ACTIVE");

        InspectionTravelTier t6 = new InspectionTravelTier();
        t6.setTierNumber(6);
        t6.setName(">30 km");
        t6.setFromKm(BigDecimal.valueOf(30.0));
        t6.setToKm(null); // Open ended
        t6.setPricePerKm(BigDecimal.valueOf(4500.00));
        t6.setStatus("ACTIVE");

        defaultTiers = List.of(t1, t2, t3, t4, t5, t6);
    }

    @Test
    @DisplayName("Khoảng cách 3.5 km: chỉ tính bậc 1, 1 chiều 7.000đ, 2 chiều 14.000đ")
    void testShortDistanceTier1() {
        when(tierRepository.findAllActive()).thenReturn(defaultTiers);

        var result = calculator.calculateWithDistance(BigDecimal.valueOf(3.5));

        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(7000).compareTo(result.getOneWayFee()));
        assertEquals(0, BigDecimal.valueOf(14000).compareTo(result.getRoundTripFee()));
        assertEquals(1, result.getAppliedTiers().size());
        assertEquals(1, result.getAppliedTiers().get(0).getTierNumber());
    }

    @Test
    @DisplayName("Khoảng cách 35 km chuẩn theo ví dụ: 1 chiều 117.500đ, 2 chiều 235.000đ")
    void testLongDistance35Km() {
        when(tierRepository.findAllActive()).thenReturn(defaultTiers);

        var result = calculator.calculateWithDistance(BigDecimal.valueOf(35.0));

        assertNotNull(result);
        // (5*2000) + (5*2500) + (5*3000) + (5*3500) + (10*4000) + (5*4500) = 117.500
        assertEquals(0, BigDecimal.valueOf(117500).compareTo(result.getOneWayFee()));
        // 117.500 * 2 = 235.000
        assertEquals(0, BigDecimal.valueOf(235000).compareTo(result.getRoundTripFee()));
        assertEquals(6, result.getAppliedTiers().size());
    }

    @Test
    @DisplayName("Khoảng cách 0 km: Phí bằng 0")
    void testZeroDistance() {
        when(tierRepository.findAllActive()).thenReturn(defaultTiers);

        var result = calculator.calculateWithDistance(BigDecimal.ZERO);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getOneWayFee()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getRoundTripFee()));
    }
}
