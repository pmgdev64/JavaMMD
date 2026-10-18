package vn.pmgteam.jmmd.physics;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.data.Joint;
import vn.pmgteam.jmmd.data.RigidBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Physics đơn giản cho tóc / váy: mỗi rigid body động = 1 particle, joint = thanh giữ khoảng cách,
 * va chạm với các rigid body tĩnh (sphere / box / capsule) và mặt đất.
 * Không phụ thuộc thư viện ngoài.
 */
public class MmdPhysics {
    // ===== Tham số chỉnh =====
    /** Lực kéo về pose animation mỗi step (0..1). Cao = cứng, thấp = mềm / đung đưa nhiều. */
    public float stiffness = 0.06f;
    /** Trọng lực (đơn vị MMD / s²). */
    public float gravity = 98f;
    /** Số lần lặp ràng buộc mỗi step. */
    public int iterations = 4;
    public boolean collisions = true;
    public boolean ground = true;
    public float groundY = 0f;

    private static final float STEP = 1f / 60f;
    private static final int MAX_STEPS = 4;
    private static final float JUMP_RESET_DIST = 20f;

    private final int nb;
    private final RigidBody[] rb;
    private final int[] bone;
    private final boolean[] dynamic;
    private final Vector3f[] bindPos;
    private final Quaternionf[] bindRot;
    private final Vector3f[] pos, prev, target;
    private final Quaternionf[] colRot;
    private final float[] radius, dampStep;
    private final int[] bodyOfBone;
    private final int[] jA, jB;
    private final float[] jRest;
    private int[][] colliders;
    private boolean maskMeansCollide = true;

    private float acc = 0f;
    private boolean needReset = true;

    private final Matrix4f skin = new Matrix4f();
    private final Quaternionf q = new Quaternionf(), qInv = new Quaternionf();
    private final Vector3f t0 = new Vector3f(), t1 = new Vector3f(), t2 = new Vector3f(), t3 = new Vector3f();

    public MmdPhysics(MmdModel model) {
        nb = model.rigidBodies.size();
        int boneCount = model.bones.size();

        rb = new RigidBody[nb];
        bone = new int[nb];
        dynamic = new boolean[nb];
        bindPos = new Vector3f[nb];
        bindRot = new Quaternionf[nb];
        pos = new Vector3f[nb];
        prev = new Vector3f[nb];
        target = new Vector3f[nb];
        colRot = new Quaternionf[nb];
        radius = new float[nb];
        dampStep = new float[nb];
        bodyOfBone = new int[boneCount];
        java.util.Arrays.fill(bodyOfBone, -1);

        int dynCount = 0;
        for (int i = 0; i < nb; i++) {
            RigidBody r = model.rigidBodies.get(i);
            rb[i] = r;
            bone[i] = (r.boneIndex >= 0 && r.boneIndex < boneCount) ? r.boneIndex : -1;
            dynamic[i] = r.physicsMode != 0 && bone[i] >= 0;
            if (dynamic[i]) {
                dynCount++;
                if (bodyOfBone[bone[i]] < 0) bodyOfBone[bone[i]] = i;
            }

            bindPos[i] = new Vector3f(r.position[0], r.position[1], r.position[2]);
            // MMD: R = Ry * Rx * Rz
            bindRot[i] = new Quaternionf().rotateY(r.rotation[1]).rotateX(r.rotation[0]).rotateZ(r.rotation[2]);

            pos[i] = new Vector3f(bindPos[i]);
            prev[i] = new Vector3f(bindPos[i]);
            target[i] = new Vector3f(bindPos[i]);
            colRot[i] = new Quaternionf(bindRot[i]);

            float rad = (r.shapeType == 1) ? Math.min(r.width, Math.min(r.height, r.depth)) : r.width;
            radius[i] = Math.max(0.01f, rad);

            float damp = Math.max(0f, Math.min(0.999f, r.linearDamping));
            dampStep[i] = (float) Math.pow(1.0 - damp, STEP);
        }

        List<int[]> joints = new ArrayList<>();
        for (Joint j : model.joints) {
            int a = j.rigidBodyAIndex, b = j.rigidBodyBIndex;
            if (a < 0 || b < 0 || a >= nb || b >= nb || a == b) continue;
            if (!dynamic[a] && !dynamic[b]) continue;
            joints.add(new int[]{a, b});
        }
        jA = new int[joints.size()];
        jB = new int[joints.size()];
        jRest = new float[joints.size()];
        for (int k = 0; k < jA.length; k++) {
            jA[k] = joints.get(k)[0];
            jB[k] = joints.get(k)[1];
            jRest[k] = bindPos[jA[k]].distance(bindPos[jB[k]]);
        }

        buildColliders();
        System.out.println("[PHYSICS] Rigid body: " + nb + ", động: " + dynCount + ", joint dùng: " + jA.length);
    }

    /** Đổi cách hiểu collisionMask nếu va chạm trông sai (true = bit 1 là có va chạm). */
    public void setMaskMeansCollide(boolean v) {
        maskMeansCollide = v;
        buildColliders();
    }

    private boolean canCollide(int a, int b) {
        int ga = rb[a].group & 15, gb = rb[b].group & 15;
        boolean ab = ((rb[a].collisionMask >> gb) & 1) != 0;
        boolean ba = ((rb[b].collisionMask >> ga) & 1) != 0;
        return maskMeansCollide ? (ab && ba) : (!ab && !ba);
    }

    private void buildColliders() {
        colliders = new int[nb][];
        for (int i = 0; i < nb; i++) {
            if (!dynamic[i]) { colliders[i] = new int[0]; continue; }
            List<Integer> list = new ArrayList<>();
            for (int c = 0; c < nb; c++) {
                if (!dynamic[c] && canCollide(i, c)) list.add(c);
            }
            colliders[i] = list.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    // ================= API cho Animator =================

    public int bodyOfBone(int boneIndex) {
        return (boneIndex >= 0 && boneIndex < bodyOfBone.length) ? bodyOfBone[boneIndex] : -1;
    }

    public Vector3f bodyPos(int body) { return pos[body]; }

    public Vector3f bodyBindPos(int body) { return bindPos[body]; }

    public void reset() { needReset = true; }

    /** Gọi mỗi frame SAU khi pose animation (kể cả IK) đã tính xong world[]. */
    public void simulate(Matrix4f[] world, Matrix4f[] invBind, float dt) {
        // 1) Pose animation -> vị trí body tĩnh (collider) và target của body động
        for (int i = 0; i < nb; i++) {
            int b = bone[i];
            if (b < 0) {
                t0.set(bindPos[i]);
                q.set(bindRot[i]);
            } else {
                skin.set(world[b]).mul(invBind[b]);
                skin.transformPosition(bindPos[i], t0);
                skin.getNormalizedRotation(q).mul(bindRot[i]);
            }
            colRot[i].set(q);

            if (dynamic[i]) {
                target[i].set(t0);
            } else {
                if (!needReset && t0.distanceSquared(pos[i]) > JUMP_RESET_DIST * JUMP_RESET_DIST) needReset = true;
                pos[i].set(t0);
            }
        }

        if (needReset) {
            for (int i = 0; i < nb; i++) {
                if (!dynamic[i]) continue;
                pos[i].set(target[i]);
                prev[i].set(target[i]);
            }
            acc = 0f;
            needReset = false;
        }

        // 2) Step cố định 60Hz
        acc = Math.min(acc + dt, STEP * MAX_STEPS);
        while (acc >= STEP) {
            step();
            acc -= STEP;
        }
    }

    // ================= Mô phỏng =================

    private void step() {
        float g = gravity * STEP * STEP;

        for (int i = 0; i < nb; i++) {
            if (!dynamic[i]) continue;
            Vector3f p = pos[i], pv = prev[i], tg = target[i];

            float vx = (p.x - pv.x) * dampStep[i];
            float vy = (p.y - pv.y) * dampStep[i];
            float vz = (p.z - pv.z) * dampStep[i];
            pv.set(p);

            p.x += vx;
            p.y += vy - g;
            p.z += vz;

            p.x += (tg.x - p.x) * stiffness;
            p.y += (tg.y - p.y) * stiffness;
            p.z += (tg.z - p.z) * stiffness;
        }

        for (int it = 0; it < iterations; it++) {
            for (int j = 0; j < jRest.length; j++) solveRod(j);

            for (int i = 0; i < nb; i++) {
                if (!dynamic[i]) continue;
                if (collisions) {
                    for (int c : colliders[i]) collide(i, c);
                }
                if (ground) pos[i].y = Math.max(pos[i].y, groundY + radius[i]);
            }
        }
    }

    private void solveRod(int j) {
        int a = jA[j], b = jB[j];
        float wa = dynamic[a] ? 1f : 0f;
        float wb = dynamic[b] ? 1f : 0f;
        float ws = wa + wb;
        if (ws == 0f) return;

        Vector3f pa = pos[a], pb = pos[b];
        t0.set(pb).sub(pa);
        float len = t0.length();
        if (len < 1e-6f) return;

        float k = (len - jRest[j]) / len / ws;
        pa.fma(k * wa, t0);
        pb.fma(-k * wb, t0);
    }

    private void collide(int i, int c) {
        Vector3f p = pos[i];
        RigidBody s = rb[c];
        float r = radius[i];

        switch (s.shapeType) {
            case 0: // Sphere
                pushFromPoint(p, pos[c], s.width + r);
                break;

            case 2: { // Capsule (trục Y cục bộ)
                t1.set(0f, 1f, 0f);
                colRot[c].transform(t1);
                float h = s.height * 0.5f;
                t2.set(pos[c]).fma(-h, t1);          // đầu A của đoạn
                t0.set(p).sub(t2);
                float u = Math.max(0f, Math.min(2f * h, t0.dot(t1)));
                t3.set(t2).fma(u, t1);               // điểm gần nhất trên đoạn
                pushFromPoint(p, t3, s.width + r);
                break;
            }

            case 1: { // Box (width/height/depth là nửa kích thước)
                qInv.set(colRot[c]).conjugate();
                t1.set(p).sub(pos[c]);
                qInv.transform(t1);

                float hx = s.width, hy = s.height, hz = s.depth;
                float cx = Math.max(-hx, Math.min(hx, t1.x));
                float cy = Math.max(-hy, Math.min(hy, t1.y));
                float cz = Math.max(-hz, Math.min(hz, t1.z));
                float dx = t1.x - cx, dy = t1.y - cy, dz = t1.z - cz;
                float d2 = dx * dx + dy * dy + dz * dz;

                if (d2 > 1e-12f) {
                    if (d2 >= r * r) return;
                    float f = r / (float) Math.sqrt(d2);
                    t1.set(cx + dx * f, cy + dy * f, cz + dz * f);
                } else {
                    // Nằm trong box: đẩy ra mặt gần nhất
                    float px = hx - Math.abs(t1.x), py = hy - Math.abs(t1.y), pz = hz - Math.abs(t1.z);
                    if (px <= py && px <= pz) t1.x = (t1.x >= 0f ? 1f : -1f) * (hx + r);
                    else if (py <= pz)        t1.y = (t1.y >= 0f ? 1f : -1f) * (hy + r);
                    else                      t1.z = (t1.z >= 0f ? 1f : -1f) * (hz + r);
                }
                colRot[c].transform(t1);
                p.set(pos[c]).add(t1);
                break;
            }

            default:
                break;
        }
    }

    private void pushFromPoint(Vector3f p, Vector3f center, float minDist) {
        t0.set(p).sub(center);
        float d2 = t0.lengthSquared();
        if (d2 >= minDist * minDist) return;
        float d = (float) Math.sqrt(d2);
        if (d < 1e-6f) { t0.set(0f, 1f, 0f); d = 1f; }
        p.set(center).fma(minDist / d, t0);
    }
}