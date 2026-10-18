package vn.pmgteam.jmmd.animation;

import java.nio.charset.Charset;
import java.util.Map;

/** VMD cắt tên ở 15 byte (bone/morph) hoặc 20 byte (IK) Shift-JIS; cắt tên PMX giống vậy để so khớp. */
public final class VmdNames {
    private static final Charset SJIS = Charset.forName("Shift_JIS");

    private VmdNames() {}

    public static String truncate(String name, int maxBytes) {
        byte[] b = name.getBytes(SJIS);
        if (b.length <= maxBytes) return name;
        return new String(b, 0, maxBytes, SJIS);
    }

    public static <T> T find(Map<String, T> map, String name, int maxBytes) {
        T v = map.get(name);
        if (v == null) v = map.get(truncate(name, maxBytes));
        return v;
    }
}