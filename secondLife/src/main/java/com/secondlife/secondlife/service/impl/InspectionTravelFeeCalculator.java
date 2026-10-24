package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.entity.InspectionTravelTier;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class InspectionTravelFeeCalculator {

    private final InspectionTravelTierRepository tierRepository;

    public InspectionTravelFeeCalculator(InspectionTravelTierRepository tierRepository) {
        this.tierRepository = tierRepository;
    }

    @Value("${app.inspection.center.latitude:10.7300}")
    private double defaultCenterLatitude;

    @Value("${app.inspection.center.longitude:106.7050}")
    private double defaultCenterLongitude;

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_CURVATURE_FACTOR = 1.25;

    public static class TravelFeeResult {
        private final BigDecimal distanceKm;
        private final BigDecimal oneWayFee;
        private final BigDecimal roundTripFee;
        private final List<InspectionTravelTierDto> appliedTiers;

        public TravelFeeResult(BigDecimal distanceKm, BigDecimal oneWayFee, BigDecimal roundTripFee, List<InspectionTravelTierDto> appliedTiers) {
            this.distanceKm = distanceKm;
            this.oneWayFee = oneWayFee;
            this.roundTripFee = roundTripFee;
            this.appliedTiers = appliedTiers;
        }

        public BigDecimal getDistanceKm() { return distanceKm; }
        public BigDecimal getOneWayFee() { return oneWayFee; }
        public BigDecimal getRoundTripFee() { return roundTripFee; }
        public List<InspectionTravelTierDto> getAppliedTiers() { return appliedTiers; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private BigDecimal distanceKm;
            private BigDecimal oneWayFee;
            private BigDecimal roundTripFee;
            private List<InspectionTravelTierDto> appliedTiers;

            public Builder distanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; return this; }
            public Builder oneWayFee(BigDecimal oneWayFee) { this.oneWayFee = oneWayFee; return this; }
            public Builder roundTripFee(BigDecimal roundTripFee) { this.roundTripFee = roundTripFee; return this; }
            public Builder appliedTiers(List<InspectionTravelTierDto> appliedTiers) { this.appliedTiers = appliedTiers; return this; }

            public TravelFeeResult build() {
                return new TravelFeeResult(distanceKm, oneWayFee, roundTripFee, appliedTiers);
            }
        }
    }

    /**
     * Tính toán khoảng cách (km) và cước di chuyển 6 bậc thang (2 chiều) giữa Người bán và Trung tâm kiểm định.
     */
    public TravelFeeResult calculate(BigDecimal sellerLat, BigDecimal sellerLon, BigDecimal centerLat, BigDecimal centerLon) {
        double lat1 = sellerLat != null ? sellerLat.doubleValue() : 10.7769;
        double lon1 = sellerLon != null ? sellerLon.doubleValue() : 106.7009;
        double lat2 = centerLat != null ? centerLat.doubleValue() : defaultCenterLatitude;
        double lon2 = centerLon != null ? centerLon.doubleValue() : defaultCenterLongitude;

        double directDistance = haversine(lat1, lon1, lat2, lon2);
        double roadDistance = Math.max(1.0, directDistance * ROAD_CURVATURE_FACTOR);
        BigDecimal distanceKm = BigDecimal.valueOf(roadDistance).setScale(2, RoundingMode.HALF_UP);

        return calculateWithDistance(distanceKm);
    }

    public TravelFeeResult calculateWithDistance(BigDecimal distanceKm) {
        List<InspectionTravelTier> tiers = tierRepository.findAllActive();
        if (tiers == null || tiers.isEmpty()) {
            tiers = defaultTiers();
        }

        BigDecimal oneWayFee = BigDecimal.ZERO;
        List<InspectionTravelTierDto> appliedTiers = new ArrayList<>();
        double remainingDistance = distanceKm.doubleValue();

        for (InspectionTravelTier tier : tiers) {
            double from = tier.getFromKm().doubleValue();
            Double to = tier.getToKm() != null ? tier.getToKm().doubleValue() : null;

            if (remainingDistance > from) {
                double tierCapacity = to != null ? (to - from) : Double.MAX_VALUE;
                double chargedInTier = Math.min(remainingDistance - from, tierCapacity);

                BigDecimal chargedDistance = BigDecimal.valueOf(chargedInTier).setScale(2, RoundingMode.HALF_UP);
                BigDecimal feeInTier = chargedDistance.multiply(tier.getPricePerKm()).setScale(0, RoundingMode.HALF_UP);

                oneWayFee = oneWayFee.add(feeInTier);

                appliedTiers.add(InspectionTravelTierDto.builder()
                        .tierNumber(tier.getTierNumber())
                        .name(tier.getName())
                        .fromKm(tier.getFromKm())
                        .toKm(tier.getToKm())
                        .pricePerKm(tier.getPricePerKm())
                        .distanceChargedInTier(chargedDistance)
                        .feeInTier(feeInTier)
                        .build());
            }
        }

        BigDecimal roundTripFee = oneWayFee.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP);

        return TravelFeeResult.builder()
                .distanceKm(distanceKm)
                .oneWayFee(oneWayFee)
                .roundTripFee(roundTripFee)
                .appliedTiers(appliedTiers)
                .build();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private List<InspectionTravelTier> defaultTiers() {
        List<InspectionTravelTier> list = new ArrayList<>();
        list.add(createTier(1, "0-5 km đầu", 0.0, 5.0, 2000));
        list.add(createTier(2, "Trên 5-10 km", 5.0, 10.0, 2500));
        list.add(createTier(3, "Trên 10-15 km", 10.0, 15.0, 3000));
        list.add(createTier(4, "Trên 15-20 km", 15.0, 20.0, 3500));
        list.add(createTier(5, "Trên 20-30 km", 20.0, 30.0, 4000));
        list.add(createTier(6, "Trên 30 km", 30.0, null, 4500));
        return list;
    }

    private InspectionTravelTier createTier(int num, String name, double from, Double to, long price) {
        InspectionTravelTier tier = new InspectionTravelTier();
        tier.setTierNumber(num);
        tier.setName(name);
        tier.setFromKm(BigDecimal.valueOf(from));
        tier.setToKm(to != null ? BigDecimal.valueOf(to) : null);
        tier.setPricePerKm(BigDecimal.valueOf(price));
        tier.setStatus("ACTIVE");
        return tier;
    }
}
