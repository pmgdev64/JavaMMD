package vn.pmgteam.jmmd.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BoneKeyframe {
    public int time; // Frame (30fps)
    public Vector3f position = new Vector3f();
    public Quaternionf rotation = new Quaternionf();

    /** 64 byte nội suy của VMD. Kênh: 0 = X, 1 = Y, 2 = Z, 3 = Rotation. */
    public byte[] interp = new byte[64];

    /** Đường cong Bezier của kênh `ch`: t (0..1) -> t' (0..1). */
    public float curve(int ch, float t) {
        float x1 = (interp[ch] & 0xFF) / 127f;
        float y1 = (interp[ch + 4] & 0xFF) / 127f;
        float x2 = (interp[ch + 8] & 0xFF) / 127f;
        float y2 = (interp[ch + 12] & 0xFF) / 127f;
        return bezier(x1, y1, x2, y2, t);
    }

    private static float bezier(float x1, float y1, float x2, float y2, float t) {
        if (x1 == y1 && x2 == y2) return t; // tuyến tính
        float lo = 0f, hi = 1f, s = t;
        for (int i = 0; i < 16; i++) {
            float inv = 1f - s;
            float x = 3f * inv * inv * s * x1 + 3f * inv * s * s * x2 + s * s * s;
            if (x < t) lo = s; else hi = s;
            s = (lo + hi) * 0.5f;
        }
        float inv = 1f - s;
        return 3f * inv * inv * s * y1 + 3f * inv * s * s * y2 + s * s * s;
    }
}