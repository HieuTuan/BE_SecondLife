package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.ShippingCatalogProperties.City;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Exact ward boundaries, loaded once with an immutable bounding-volume index. */
public final class VietnameseAdministrativeAreas {
    private VietnameseAdministrativeAreas() { }

    public record Division(String code, String wardName, City city) {
        public String cityName() {
            return switch (city) {
                case HANOI -> "Thành phố Hà Nội";
                case DANANG -> "Thành phố Đà Nẵng";
                case HO_CHI_MINH -> "Thành phố Hồ Chí Minh";
            };
        }
    }

    private record Ring(double[] points, double[] bounds) {
        int locate(double x, double y) {
            return within(bounds, y, x) ? position(points, x, y) : 0;
        }
    }

    private record Polygon(List<Ring> rings) {
        boolean covers(double x, double y) {
            int outer = rings.getFirst().locate(x, y);
            if (outer == 0) return false;
            if (outer == 2) return true;
            for (int i = 1; i < rings.size(); i++) {
                int hole = rings.get(i).locate(x, y);
                if (hole == 2) return true;
                if (hole == 1) return false;
            }
            return true;
        }
    }

    private record Area(Division division, double[] bounds, List<Polygon> polygons, int ordinal) {
        boolean covers(double latitude, double longitude) {
            if (!within(bounds, latitude, longitude)) return false;
            for (Polygon polygon : polygons) if (polygon.covers(longitude, latitude)) return true;
            return false;
        }
    }

    private record Node(double[] bounds, int minimumOrdinal, Node left, Node right, List<Area> leaf) { }

    private static final class Index {
        private static final Node ROOT = build(load());
    }

    public static Division locate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return null;
        Area area = find(Index.ROOT, latitude, longitude, null);
        return area == null ? null : area.division();
    }

    private static Area find(Node node, double latitude, double longitude, Area best) {
        if ((best != null && node.minimumOrdinal() >= best.ordinal())
                || !within(node.bounds(), latitude, longitude)) return best;
        if (node.leaf() != null) {
            for (Area area : node.leaf()) {
                if ((best == null || area.ordinal() < best.ordinal()) && area.covers(latitude, longitude)) best = area;
            }
            return best;
        }
        Node first = node.left().minimumOrdinal() < node.right().minimumOrdinal() ? node.left() : node.right();
        Node second = first == node.left() ? node.right() : node.left();
        // Keep original source order for points shared by multiple ward boundaries.
        return find(second, latitude, longitude, find(first, latitude, longitude, best));
    }

    private static Node build(List<Area> areas) {
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int minimum = Integer.MAX_VALUE;
        for (Area area : areas) {
            for (int i = 0; i < 2; i++) {
                bounds[i] = Math.min(bounds[i], area.bounds()[i]);
                bounds[i + 2] = Math.max(bounds[i + 2], area.bounds()[i + 2]);
            }
            minimum = Math.min(minimum, area.ordinal());
        }
        if (areas.size() <= 8) return new Node(bounds, minimum, null, null, List.copyOf(areas));
        int axis = bounds[2] - bounds[0] >= bounds[3] - bounds[1] ? 0 : 1;
        var sorted = new ArrayList<>(areas);
        sorted.sort(Comparator.comparingDouble(a -> a.bounds()[axis] + a.bounds()[axis + 2]));
        int middle = sorted.size() / 2;
        return new Node(bounds, minimum, build(sorted.subList(0, middle)), build(sorted.subList(middle, sorted.size())), null);
    }

    private static boolean within(double[] bounds, double latitude, double longitude) {
        return longitude >= bounds[0] && latitude >= bounds[1] && longitude <= bounds[2] && latitude <= bounds[3];
    }

    private static List<Area> load() {
        var resource = new ClassPathResource("geocoding/vn-three-cities-wards.bin.gz");
        var areas = new ArrayList<Area>();
        var codes = new HashSet<String>();
        try (var stream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource.getInputStream())))) {
            if (!"SLADM002".equals(new String(stream.readNBytes(8), StandardCharsets.US_ASCII)))
                throw new IllegalStateException("Unsupported administrative boundary format");
            int count = count(stream, 10000);
            for (int ordinal = 0; ordinal < count; ordinal++) {
                City city = City.valueOf(text(stream));
                String code = text(stream), name = text(stream);
                if (code.isBlank() || name.isBlank() || !codes.add(code))
                    throw new IllegalStateException("Invalid administrative division");
                double[] bounds = new double[4];
                for (int i = 0; i < 4; i++) bounds[i] = stream.readDouble();
                var polygons = new ArrayList<Polygon>();
                int polygonCount = count(stream, 100000);
                for (int i = 0; i < polygonCount; i++) polygons.add(polygon(stream));
                areas.add(new Area(new Division(code, name, city), bounds, List.copyOf(polygons), ordinal));
            }
            if (stream.read() != -1) throw new IllegalStateException("Unexpected administrative boundary data");
            return List.copyOf(areas);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load Vietnamese administrative boundaries", error);
        }
    }

    private static String text(DataInputStream stream) throws java.io.IOException {
        int length = count(stream, 4096);
        byte[] bytes = stream.readNBytes(length);
        if (bytes.length != length) throw new java.io.EOFException();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int count(DataInputStream stream, int maximum) throws java.io.IOException {
        int count = stream.readInt();
        if (count < 1 || count > maximum) throw new IllegalStateException("Invalid administrative data length");
        return count;
    }

    private static double coordinate(DataInputStream stream) throws java.io.IOException {
        int value = stream.readInt();
        // Preserve exceptional higher-precision coordinates bit-for-bit instead of rounding them.
        return value == Integer.MIN_VALUE ? stream.readDouble() : value / 1000000.0;
    }

    private static Polygon polygon(DataInputStream stream) throws java.io.IOException {
        var rings = new ArrayList<Ring>();
        int ringCount = count(stream, 100000);
        for (int r = 0; r < ringCount; r++) {
            int pointCount = count(stream, 1000000);
            if (pointCount < 4) throw new IllegalStateException("Invalid administrative ring");
            double[] points = new double[pointCount * 2];
            double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int i = 0; i < pointCount; i++) {
                double x = coordinate(stream), y = coordinate(stream);
                if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 180 || Math.abs(y) > 90)
                    throw new IllegalStateException("Invalid administrative coordinate");
                points[2 * i] = x; points[2 * i + 1] = y;
                bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
            }
            if (points[0] != points[points.length - 2] || points[1] != points[points.length - 1])
                throw new IllegalStateException("Unclosed administrative ring");
            rings.add(new Ring(points, bounds));
        }
        return new Polygon(List.copyOf(rings));
    }

    // 0 outside, 1 inside, 2 on an edge. Holes and multipolygon islands are respected.
    static int position(double[] ring, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 2; i < ring.length; j = i, i += 2) {
            double ay = ring[j + 1], by = ring[i + 1];
            // An edge outside the horizontal ray's y interval cannot cross it or contain the point.
            if ((ay < y && by < y) || (ay > y && by > y)) continue;
            double ax = ring[j], bx = ring[i];
            if (ax < x && bx < x) continue;
            if (x >= Math.min(ax, bx) && x <= Math.max(ax, bx)) {
                double cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax);
                if (Math.abs(cross) <= 1e-12) return 2;
            }
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside;
        }
        return inside ? 1 : 0;
    }
}
