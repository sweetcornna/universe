package io.github.sweetcornna.universe.core.dyson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 戴森壳一层的蓝图（ADR-0030 §四）：网格和颜色写成一行文字，{@code udyson1:} 后面跟 Base64 的压缩数据和校验码。
 * 半径、倾角、升交点不在里面，导入的层用自己的。节点按 1e-5 度存经纬度。
 *
 * <p>同一套二进制格式也用在网络上（{@link #pack}、{@link #unpack}），那里节点存精确的坐标，服务端才认得出没动的节点。
 */
public final class DysonBlueprint {
    public static final String PREFIX = "udyson";
    public static final int VERSION = 1;
    /** 解压以后最多多少字节，防止一小段文字解压出很大的东西。 */
    private static final int MAX_BYTES = 256 * 1024;
    /** 蓝图串最长多少个字符：输入框和蓝图库按它限长，比最大的一层导出来还宽裕。 */
    public static final int MAX_TEXT = 128 * 1024;
    private static final double QUANTUM = 1e5;

    /** 读蓝图的错误，界面上显示 {@link #key()}。 */
    public enum Error {
        EMPTY,
        NOT_BLUEPRINT,
        WRONG_VERSION,
        CORRUPT,
        INVALID;

        public String key() {
            return "gui.universe.dyson.blueprint.error." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 读的结果：成功时 design 不是 null；失败时有 error，INVALID 时还有具体的 problem，WRONG_VERSION 时 version
     * 是串里写的版本。
     */
    public record Parsed(DysonDesign design, Error error, DysonDesign.Problem problem, int version) {
        static Parsed fail(Error error) {
            return new Parsed(null, error, null, 0);
        }
    }

    private DysonBlueprint() {
    }

    /** 导出成文字。 */
    public static String export(DysonDesign design) {
        byte[] body = deflate(write(design, false));
        CRC32 crc = new CRC32();
        crc.update(body);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return PREFIX + VERSION + ":" + encoder.encodeToString(body) + ":" + Long.toHexString(crc.getValue());
    }

    /** 读文字。 */
    public static Parsed parse(String text) {
        String s = text == null ? "" : text.strip();
        if (s.isEmpty()) {
            return Parsed.fail(Error.EMPTY);
        }
        if (!s.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Parsed.fail(Error.NOT_BLUEPRINT);
        }
        int colon = s.indexOf(':');
        int version;
        try {
            version = Integer.parseInt(s.substring(PREFIX.length(), colon < 0 ? s.length() : colon));
        } catch (NumberFormatException e) {
            return Parsed.fail(Error.NOT_BLUEPRINT);
        }
        if (version != VERSION) {
            return new Parsed(null, Error.WRONG_VERSION, null, version);
        }
        int second = colon < 0 ? -1 : s.indexOf(':', colon + 1);
        if (second < 0) {
            return Parsed.fail(Error.CORRUPT);
        }
        byte[] body;
        long expected;
        try {
            body = Base64.getUrlDecoder().decode(s.substring(colon + 1, second));
            expected = Long.parseLong(s.substring(second + 1), 16);
        } catch (IllegalArgumentException e) {
            return Parsed.fail(Error.CORRUPT);
        }
        CRC32 crc = new CRC32();
        crc.update(body);
        if (crc.getValue() != expected) {
            return Parsed.fail(Error.CORRUPT);
        }
        DysonDesign design = read(inflate(body), false);
        if (design == null) {
            return Parsed.fail(Error.CORRUPT);
        }
        DysonDesign.Problem problem = design.validate();
        return problem == null ? new Parsed(design, null, null, version) : new Parsed(null, Error.INVALID, problem, version);
    }

    /** 网络用：压缩的二进制，节点是精确坐标。 */
    public static byte[] pack(DysonDesign design) {
        return deflate(write(design, true));
    }

    /** 网络用：坏了返回 null（不查几何合不合法）。 */
    public static DysonDesign unpack(byte[] bytes) {
        return read(inflate(bytes), true);
    }

    // ---- 二进制

    private static byte[] write(DysonDesign design, boolean exact) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(design.base().ordinal());
            out.writeByte(design.density());
            out.writeShort(design.nodeCount());
            for (int i = 0; i < design.nodeCount(); i++) {
                double[] p = design.position(i);
                if (exact) {
                    out.writeDouble(p[0]);
                    out.writeDouble(p[1]);
                    out.writeDouble(p[2]);
                } else {
                    out.writeInt((int) Math.round(SphereGeometry.latitude(p) * QUANTUM));
                    out.writeInt((int) Math.round(SphereGeometry.longitude(p) * QUANTUM));
                }
            }
            out.writeShort(design.frameCount());
            for (int i = 0; i < design.frameCount(); i++) {
                out.writeShort(design.frame(i).a());
                out.writeShort(design.frame(i).b());
            }
            out.writeShort(design.shellCount());
            for (int i = 0; i < design.shellCount(); i++) {
                int[] loop = design.shell(i).nodes();
                out.writeByte(loop.length);
                for (int v : loop) {
                    out.writeShort(v);
                }
            }
            colors(out, design.nodeColors());
            colors(out, design.frameColors());
            colors(out, design.shellColors());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    /** 颜色只存不是默认色的：下标和颜色。 */
    private static void colors(DataOutputStream out, int[] colors) throws IOException {
        int count = 0;
        for (int c : colors) {
            count += c != 0 ? 1 : 0;
        }
        out.writeShort(count);
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] != 0) {
                out.writeShort(i);
                out.writeInt(colors[i]);
            }
        }
    }

    private static int[] colors(DataInputStream in, int length) throws IOException {
        int[] colors = new int[length];
        int count = in.readUnsignedShort();
        if (count > length) {
            throw new IOException("too many colors");
        }
        for (int k = 0; k < count; k++) {
            int index = in.readUnsignedShort();
            int color = in.readInt();
            if (index >= length) {
                throw new IOException("bad color index");
            }
            colors[index] = color == 0 ? 0 : 0xFF000000 | color;
        }
        return colors;
    }

    private static DysonDesign read(byte[] bytes, boolean exact) {
        if (bytes == null) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            DysonGrid.Template base = DysonGrid.Template.byOrdinal(in.readUnsignedByte());
            int density = in.readUnsignedByte();
            int nodes = in.readUnsignedShort();
            if (nodes > DysonDesign.MAX_NODES) {
                return null;
            }
            double[][] positions = new double[nodes][];
            for (int i = 0; i < nodes; i++) {
                if (exact) {
                    positions[i] = new double[] {in.readDouble(), in.readDouble(), in.readDouble()};
                    if (!Double.isFinite(positions[i][0] + positions[i][1] + positions[i][2])
                            || Math.abs(SphereGeometry.dot(positions[i], positions[i]) - 1) > 1e-6) {
                        return null;
                    }
                } else {
                    positions[i] = SphereGeometry.fromLatLon(in.readInt() / QUANTUM, in.readInt() / QUANTUM);
                }
            }
            int frames = in.readUnsignedShort();
            if (frames > DysonDesign.MAX_FRAMES) {
                return null;
            }
            int[][] edges = new int[frames][];
            for (int i = 0; i < frames; i++) {
                edges[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
                if (edges[i][0] >= nodes || edges[i][1] >= nodes) {
                    return null;
                }
            }
            int shells = in.readUnsignedShort();
            if (shells > DysonDesign.MAX_SHELLS) {
                return null;
            }
            int[][] loops = new int[shells][];
            for (int i = 0; i < shells; i++) {
                int sides = in.readUnsignedByte();
                if (sides < 3 || sides > DysonDesign.MAX_SIDES) {
                    return null;
                }
                loops[i] = new int[sides];
                for (int k = 0; k < sides; k++) {
                    loops[i][k] = in.readUnsignedShort();
                    if (loops[i][k] >= nodes) {
                        return null;
                    }
                }
            }
            int[] nodeColors = colors(in, nodes);
            int[] frameColors = colors(in, frames);
            int[] shellColors = colors(in, shells);
            if (in.available() > 0) {
                return null;
            }
            DysonDesign design = new DysonDesign(base, Math.max(1, Math.min(base.maxDensity(), density)));
            for (int i = 0; i < nodes; i++) {
                design.rawNode(positions[i], nodeColors[i]);
            }
            for (int i = 0; i < frames; i++) {
                design.rawFrame(edges[i][0], edges[i][1], frameColors[i]);
            }
            for (int i = 0; i < shells; i++) {
                design.rawShell(loops[i], shellColors[i]);
            }
            return design;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2 + 16);
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] packed) {
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4 + 16);
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return null;
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) {
                    return null;
                }
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
