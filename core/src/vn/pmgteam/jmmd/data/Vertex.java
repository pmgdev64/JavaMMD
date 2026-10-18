package vn.pmgteam.jmmd.data;

/**
 * Vertex PMX. Cấu trúc biến thiên theo globals:
 *  - thêm extraUvCount * 4 float nếu extraUvCount > 0
 *  - weightType quyết định kích thước phần skinning
 */
public class Vertex {
    public enum WeightType {
        BDEF1(0), BDEF2(1), BDEF4(2), SDEF(3), QDEF(4);
        public final int id;
        WeightType(int id) { this.id = id; }
    }

    public float[] position = new float[3];
    public float[] normal   = new float[3];
    public float[] uv       = new float[2];
    /** null nếu extraUvCount == 0; mỗi phần tử là float[4]. */
    public float[][] extraUvs;

    public WeightType weightType = WeightType.BDEF1;
    /** 1..4 phần tử tuỳ weightType; -1 = không dùng. */
    public int[]   boneIndices = {-1, -1, -1, -1};
    public float[] boneWeights = {0f, 0f, 0f, 0f};

    /** Chỉ dùng cho SDEF. */
    public float[] sdefC  = new float[3];
    public float[] sdefR0 = new float[3];
    public float[] sdefR1 = new float[3];

    /** Hệ số scale cho edge rendering (PMX). */
    public float edgeScale = 1f;
}