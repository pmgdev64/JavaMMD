package vn.pmgteam.jmmd.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.data.Morph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MorphAnimator {
    private final MmdModel model;
    private final int morphCount;
    private final int vertexCount;
    private final List<TreeMap<Integer, Float>> tracks = new ArrayList<>();
    private final float[] weights;
    private final float[] lastWeights;
    private boolean first = true;
    private boolean touchedVertex = false;

    /** Offset vị trí vertex (vertexCount*3), cộng vào vị trí gốc. */
    public final float[] vertexOffsets;
    /** Diffuse đã áp morph material, theo index material. */
    public final float[][] materialDiffuse;
    /** Offset bone từ bone morph, Animator sẽ cộng vào. */
    public final Vector3f[] boneMorphTrans;
    public final Quaternionf[] boneMorphRot;
    /** true nếu frame này cần upload lại vị trí vertex. */
    public boolean vertexDirty = false;

    public MorphAnimator(MmdModel model, VmdMotion motion) {
        this.model = model;
        this.morphCount = model.morphs.size();
        this.vertexCount = model.vertices.size();
        this.weights = new float[morphCount];
        this.lastWeights = new float[morphCount];
        this.vertexOffsets = new float[vertexCount * 3];

        int matched = 0;
        for (Morph m : model.morphs) {
            TreeMap<Integer, Float> t = VmdNames.find(motion.morphTracks, m.name, 15);
            tracks.add(t);
            if (t != null) matched++;
        }

        materialDiffuse = new float[model.materials.size()][4];
        for (int i = 0; i < materialDiffuse.length; i++) {
            System.arraycopy(model.materials.get(i).diffuse, 0, materialDiffuse[i], 0, 4);
        }

        int bc = model.bones.size();
        boneMorphTrans = new Vector3f[bc];
        boneMorphRot = new Quaternionf[bc];
        for (int i = 0; i < bc; i++) {
            boneMorphTrans[i] = new Vector3f();
            boneMorphRot[i] = new Quaternionf();
        }

        System.out.println("[MORPH] Morph khớp track: " + matched + "/" + morphCount);
    }

    public void evaluate(float frame) {
        for (int i = 0; i < morphCount; i++) weights[i] = sample(tracks.get(i), frame);

        if (!first && Arrays.equals(weights, lastWeights)) {
            vertexDirty = false;
            return;
        }
        first = false;
        System.arraycopy(weights, 0, lastWeights, 0, morphCount);

        // Reset
        boolean hadVertex = touchedVertex;
        if (hadVertex) Arrays.fill(vertexOffsets, 0f);
        touchedVertex = false;
        for (int i = 0; i < materialDiffuse.length; i++) {
            System.arraycopy(model.materials.get(i).diffuse, 0, materialDiffuse[i], 0, 4);
        }
        for (int i = 0; i < boneMorphTrans.length; i++) {
            boneMorphTrans[i].zero();
            boneMorphRot[i].identity();
        }

        for (int i = 0; i < morphCount; i++) {
            if (Math.abs(weights[i]) > 1e-4f) apply(i, weights[i], 0);
        }

        for (float[] d : materialDiffuse) {
            for (int k = 0; k < 3; k++) d[k] = Math.max(0f, d[k]);
            d[3] = Math.max(0f, Math.min(1f, d[3]));
        }

        vertexDirty = hadVertex || touchedVertex;
    }

    private void apply(int idx, float w, int depth) {
        if (idx < 0 || idx >= morphCount || depth > 8) return;
        Morph m = model.morphs.get(idx);

        switch (m.morphType) {
            case 0: // Group
                for (Morph.MorphOffset o : m.offsets) apply(o.index, w * o.ratio, depth + 1);
                break;

            case 9: { // Flip: weight chọn 1 morph con
                int c = m.offsets.size();
                if (c == 0 || w <= 0f) break;
                int k = Math.min(c - 1, (int) (w * c));
                Morph.MorphOffset o = m.offsets.get(k);
                apply(o.index, o.ratio, depth + 1);
                break;
            }

            case 1: // Vertex
                for (Morph.MorphOffset o : m.offsets) {
                    int v = o.index;
                    if (v < 0 || v >= vertexCount) continue;
                    vertexOffsets[v * 3] += o.vertexOffset[0] * w;
                    vertexOffsets[v * 3 + 1] += o.vertexOffset[1] * w;
                    vertexOffsets[v * 3 + 2] += o.vertexOffset[2] * w;
                    touchedVertex = true;
                }
                break;

            case 2: // Bone
                for (Morph.MorphOffset o : m.offsets) {
                    int b = o.index;
                    if (b < 0 || b >= boneMorphTrans.length) continue;
                    boneMorphTrans[b].add(o.boneTranslation[0] * w, o.boneTranslation[1] * w, o.boneTranslation[2] * w);
                    Quaternionf q = new Quaternionf(o.boneRotation[0], o.boneRotation[1], o.boneRotation[2], o.boneRotation[3]);
                    if (q.lengthSquared() < 1e-6f) continue;
                    q.normalize();
                    boneMorphRot[b].mul(new Quaternionf().slerp(q, w));
                }
                break;

            case 8: // Material (chỉ diffuse)
                for (Morph.MorphOffset o : m.offsets) {
                    if (o.index < 0) {
                        for (int mi = 0; mi < materialDiffuse.length; mi++) applyMaterial(mi, o, w);
                    } else if (o.index < materialDiffuse.length) {
                        applyMaterial(o.index, o, w);
                    }
                }
                break;

            default: // UV, impulse: chưa hỗ trợ
                break;
        }
    }

    private void applyMaterial(int mi, Morph.MorphOffset o, float w) {
        float[] d = materialDiffuse[mi];
        if (o.matOperation == 0) { // nhân
            for (int k = 0; k < 4; k++) d[k] *= 1f + (o.matDiffuse[k] - 1f) * w;
        } else {                   // cộng
            for (int k = 0; k < 4; k++) d[k] += o.matDiffuse[k] * w;
        }
    }

    private static float sample(TreeMap<Integer, Float> t, float frame) {
        if (t == null || t.isEmpty()) return 0f;
        int f = (int) Math.floor(frame);
        Map.Entry<Integer, Float> lo = t.floorEntry(f);
        if (lo == null) return t.firstEntry().getValue();
        Map.Entry<Integer, Float> hi = t.higherEntry(f);
        if (hi == null) return lo.getValue();
        float span = hi.getKey() - lo.getKey();
        float a = span > 0 ? (frame - lo.getKey()) / span : 0f;
        a = Math.max(0f, Math.min(1f, a));
        return lo.getValue() + (hi.getValue() - lo.getValue()) * a;
    }
}