package com.secondlife.secondlife.common;

public final class GeoDistance {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double halfLat = Math.toRadians(lat2 - lat1) / 2;
        double halfLon = Math.toRadians(lon2 - lon1) / 2;
        double a = Math.sin(halfLat) * Math.sin(halfLat)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(halfLon) * Math.sin(halfLon);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
