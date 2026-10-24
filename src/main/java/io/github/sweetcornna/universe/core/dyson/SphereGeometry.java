package io.github.sweetcornna.universe.core.dyson;

import java.util.ArrayList;
import java.util.List;

/**
 * 单位球上的几何（ADR-0030 §四）：大圆弧、球面多边形、经纬度吸附。球面多边形放到以它的中心为切点的心射投影里处理，
 * 大圆弧在心射投影里是直线，所以简单性、包含、三角剖分都按平面算。y 是极轴，经度按 atan2(z, x)。
 */
public final class SphereGeometry {
    private static final double EPS = 1e-7;

    private SphereGeometry() {
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] normalize(double[] p) {
        double length = Math.sqrt(dot(p, p));
        return length < 1e-12 ? new double[] {0, 1, 0} : new double[] {p[0] / length, p[1] / length, p[2] / length};
    }

    /** 两个单位向量之间的角（弧度）。 */
    public static double angle(double[] a, double[] b) {
        return Math.acos(Math.max(-1, Math.min(1, dot(a, b))));
    }

    /** 纬度、经度（度）换成单位向量。 */
    public static double[] fromLatLon(double latitude, double longitude) {
        double lat = Math.toRadians(latitude);
        double lon = Math.toRadians(longitude);
        return new double[] {Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
    }

    public static double latitude(double[] p) {
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, p[1]))));
    }

    /** 经度（度），0 到 360。 */
    public static double longitude(double[] p) {
        double lon = Math.toDegrees(Math.atan2(p[2], p[0]));
        return lon < 0 ? lon + 360 : lon;
    }

    /** 吸附到每 step 度的经纬网格上；两极只有一个点。step 不大于 0 时不吸附。 */
    public static double[] snap(double[] p, double step) {
        if (step <= 0) {
            return normalize(p);
        }
        double lat = Math.max(-90, Math.min(90, Math.round(latitude(p) / step) * step));
        double lon = Math.abs(lat) >= 90 - 1e-9 ? 0 : (Math.round(longitude(p) / step) * step) % 360;
        return fromLatLon(lat, lon);
    }

    /** x 严格在弧 ab 的两个端点之间（x 在 ab 的大圆上）。 */
    private static boolean between(double[] x, double[] a, double[] b, double[] normal) {
        return dot(cross(a, x), normal) > EPS && dot(cross(x, b), normal) > EPS;
    }

    /** 弧 ab 和弧 cd 交叉（只在端点碰上的不算）。弧都短于半圈。 */
    public static boolean arcsCross(double[] a, double[] b, double[] c, double[] d) {
        double[] n1 = normalize(cross(a, b));
        double[] n2 = normalize(cross(c, d));
        double[] p = cross(n1, n2);
        double length = Math.sqrt(dot(p, p));
        if (length < 1e-9) {
            // 同一个大圆：一条弧的端点落在另一条弧里面就算重叠
            return onArc(c, a, b) || onArc(d, a, b) || onArc(a, c, d) || onArc(b, c, d);
        }
        p = new double[] {p[0] / length, p[1] / length, p[2] / length};
        double[] ab = {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
        double[] cd = {c[0] + d[0], c[1] + d[1], c[2] + d[2]};
        double[] x = dot(p, ab) >= 0 ? p : new double[] {-p[0], -p[1], -p[2]};
        return dot(x, cd) > 0 && between(x, a, b, n1) && between(x, c, d, n2);
    }

    /** p 在弧 ab 上、不是端点。 */
    private static boolean onArc(double[] p, double[] a, double[] b) {
        return arcDistance(p, a, b) < EPS && angle(p, a) > EPS && angle(p, b) > EPS;
    }

    /** p 到弧 ab 的角距离（弧度）。 */
    public static double arcDistance(double[] p, double[] a, double[] b) {
        double[] n = normalize(cross(a, b));
        double off = dot(p, n);
        double[] q = normalize(new double[] {p[0] - n[0] * off, p[1] - n[1] * off, p[2] - n[2] * off});
        if (dot(cross(a, q), n) >= 0 && dot(cross(q, b), n) >= 0 && dot(q, new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}) > 0) {
            return Math.asin(Math.min(1, Math.abs(off)));
        }
        return Math.min(angle(p, a), angle(p, b));
    }

    public static double[] centroid(double[][] points) {
        double[] c = new double[3];
        for (double[] p : points) {
            c[0] += p[0];
            c[1] += p[1];
            c[2] += p[2];
        }
        return normalize(c);
    }

    /** 以 center 为切点的心射投影；点不在 center 那一侧的半球前面（离切点 87° 以外）时是 null。 */
    public static double[] gnomonic(double[] center, double[] point) {
        double d = dot(point, center);
        if (d <= 0.05) {
            return null;
        }
        double[] up = Math.abs(center[1]) < 0.99 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] east = normalize(cross(up, center));
        double[] north = cross(center, east);
        return new double[] {dot(point, east) / d, dot(point, north) / d};
    }

    /** 整个多边形的心射投影；有一个点投不上就是 null。从球外看逆时针的多边形投影后也是逆时针。 */
    public static double[][] gnomonic(double[] center, double[][] points) {
        double[][] out = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            out[i] = gnomonic(center, points[i]);
            if (out[i] == null) {
                return null;
            }
        }
        return out;
    }

    /** 平面多边形有向面积的两倍，逆时针为正。 */
    public static double area2(double[][] polygon) {
        double total = 0;
        for (int i = 0; i < polygon.length; i++) {
            double[] p = polygon[i];
            double[] q = polygon[(i + 1) % polygon.length];
            total += p[0] * q[1] - q[0] * p[1];
        }
        return total;
    }

    private static double orient2(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean opposite(double u, double v) {
        return u > 1e-12 && v < -1e-12 || u < -1e-12 && v > 1e-12;
    }

    /** 逆时针的平面多边形是凸的。 */
    public static boolean convex(double[][] polygon) {
        for (int i = 0; i < polygon.length; i++) {
            if (orient2(polygon[i], polygon[(i + 1) % polygon.length], polygon[(i + 2) % polygon.length]) < -1e-12) {
                return false;
            }
        }
        return true;
    }

    /** 平面多边形不自交。 */
    public static boolean simple(double[][] polygon) {
        int n = polygon.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) {
                    continue;
                }
                double[] a = polygon[i];
                double[] b = polygon[(i + 1) % n];
                double[] c = polygon[j];
                double[] d = polygon[(j + 1) % n];
                if (opposite(orient2(c, d, a), orient2(c, d, b)) && opposite(orient2(a, b, c), orient2(a, b, d))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 点在平面多边形里面（射线法）。 */
    public static boolean contains(double[][] polygon, double[] p) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double[] a = polygon[i];
            double[] b = polygon[j];
            if ((a[1] > p[1]) != (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 逆时针的简单平面多边形剖成三角形（割耳法）：顶点下标，每三个一组，共 n−2 个。 */
    public static int[] triangulate(double[][] polygon) {
        int n = polygon.length;
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            remaining.add(i);
        }
        int[] out = new int[Math.max(0, (n - 2) * 3)];
        int k = 0;
        boolean clipped = true;
        while (remaining.size() > 3 && clipped) {
            clipped = false;
            int m = remaining.size();
            for (int i = 0; i < m && !clipped; i++) {
                int a = remaining.get((i + m - 1) % m);
                int b = remaining.get(i);
                int c = remaining.get((i + 1) % m);
                if (orient2(polygon[a], polygon[b], polygon[c]) <= 1e-12) {
                    continue;
                }
                boolean empty = true;
                for (int other : remaining) {
                    if (other != a && other != b && other != c && orient2(polygon[a], polygon[b], polygon[other]) >= 0
                            && orient2(polygon[b], polygon[c], polygon[other]) >= 0 && orient2(polygon[c], polygon[a], polygon[other]) >= 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    out[k++] = a;
                    out[k++] = b;
                    out[k++] = c;
                    remaining.remove(i);
                    clipped = true;
                }
            }
        }
        // 剩下的（正常是一个三角形，退化时可能更多）按扇形补上
        for (int i = 1; i + 1 < remaining.size(); i++) {
            out[k++] = remaining.get(0);
            out[k++] = remaining.get(i);
            out[k++] = remaining.get(i + 1);
        }
        return out;
    }

    /** 球面多边形的有向面积（球面度），从球外看逆时针为正。多边形要在一个半球以内。 */
    public static double signedArea(double[][] points) {
        double total = 0;
        double[] a = points[0];
        for (int k = 1; k + 1 < points.length; k++) {
            double[] b = points[k];
            double[] c = points[k + 1];
            total += 2 * Math.atan2(dot(a, cross(b, c)), 1 + dot(a, b) + dot(b, c) + dot(c, a));
        }
        return total;
    }

    /** 点 p 在球面多边形里面；center 是多边形的中心。 */
    public static boolean contains(double[][] polygon, double[] center, double[] p) {
        double[][] flat = gnomonic(center, polygon);
        double[] q = gnomonic(center, p);
        return flat != null && q != null && contains(flat, q);
    }

    /** 射线 origin + t·direction（t ≥ 0）和以原点为心、半径 radius 的球最近的交点；没有交点时是 null。 */
    public static double[] rayHit(double[] origin, double[] direction, double radius) {
        double[] d = normalize(direction);
        double b = dot(origin, d);
        double c = dot(origin, origin) - radius * radius;
        double disc = b * b - c;
        if (disc < 0) {
            return null;
        }
        double root = Math.sqrt(disc);
        double t = -b - root >= 0 ? -b - root : -b + root;
        if (t < 0) {
            return null;
        }
        return new double[] {origin[0] + d[0] * t, origin[1] + d[1] * t, origin[2] + d[2] * t};
    }

    /** {@link DysonSphere#orient} 的逆：恒星系坐标换回层自己的坐标。 */
    public static double[] unorient(double[] world, double inclination, double ascendingNode, double spin) {
        double n = Math.toRadians(ascendingNode);
        double x = world[0] * Math.cos(n) + world[2] * Math.sin(n);
        double z1 = -world[0] * Math.sin(n) + world[2] * Math.cos(n);
        double i = Math.toRadians(inclination);
        double y = world[1] * Math.cos(i) + z1 * Math.sin(i);
        double z = -world[1] * Math.sin(i) + z1 * Math.cos(i);
        double s = Math.toRadians(spin);
        return new double[] {x * Math.cos(s) + z * Math.sin(s), y, -x * Math.sin(s) + z * Math.cos(s)};
    }
}
