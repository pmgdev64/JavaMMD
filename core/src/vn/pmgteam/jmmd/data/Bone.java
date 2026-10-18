package vn.pmgteam.jmmd.data;

public class Bone {
    public String name   = "";
    public String nameEn = "";

    public float[] position = new float[3];
    public int     parentIndex = -1;
    public int     layer       = 0;
    public int     flags       = 0;

    /** Chỉ có nếu flag 0x0001 (connect-to-bone) bật. */
    public int   tailBoneIndex = -1;
    public float[] tailOffset  = new float[3];

    /** Append: flag 0x0100 (rotation), 0x0200 (translation). */
    public int   inheritParentIndex = -1;
    public float[] inheritParentOffset = new float[3];
    public float   inheritParentRatio  = 0f;

    public int   operationType = 0;

    public int   localAxisXIndex = -1;
    public int   localAxisZIndex = -1;
    public float[] localAxisX    = {1, 0, 0};
    public float[] localAxisZ    = {0, 0, 1};
    public int   externalParentKey = -1;

    public int   ikTargetIndex   = -1;
    public int   ikLoopCount     = 0;
    public float ikLimitAngle    = 0f;
    public int[] ikLinkIndices   = new int[0];
    /** Giới hạn góc (radian, euler XYZ) của từng link. */
    public boolean[] ikLinkHasLimit = new boolean[0];
    public float[][] ikLinkMin = new float[0][];
    public float[][] ikLinkMax = new float[0][];
}