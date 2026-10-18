package vn.pmgteam.jmmd.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.data.Bone;
import vn.pmgteam.jmmd.physics.MmdPhysics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Animator {
    private final MmdModel model;
    private final VmdMotion motion;
    private final int n;

    // ---- Dữ liệu tĩnh ----
    private final int[] parent;
    private final Vector3f[] restLocal;
    private final Matrix4f[] invBind;
    private final int[] order;            // parent / append-parent luôn đứng trước
    private final int[] orderPos;
    private final int[] ikBones;
    private final List<TreeMap<Integer, BoneKeyframe>> tracks = new ArrayList<>();
    private final List<TreeMap<Integer, Boolean>> ikStates = new ArrayList<>();

    private final int[] appendParent;
    private final float[] appendRatio;
    private final boolean[] appendRotateOn, appendTransOn;

    private MorphAnimator morph;
    private MmdPhysics physics;
    private boolean physicsEnabled = true;

    // ---- Trạng thái mỗi frame ----
    private final Vector3f[] animTrans;
    private final Quaternionf[] animRot;
    private final Quaternionf[] ikRot;
    private final Quaternionf[] appendRot;
    private final Vector3f[] appendTrans;
    private final Matrix4f[] world;
    private final Matrix4f[] skinning;
    private final boolean[] physOverride;
    private final Quaternionf[] physLocal;

    // ---- Temp ----
    private final Vector3f tV = new Vector3f();
    private final Quaternionf tQ1 = new Quaternionf(), tQ2 = new Quaternionf();
    private final Vector3f effPos = new Vector3f(), targetPos = new Vector3f();
    private final Vector3f localEff = new Vector3f(), localTarget = new Vector3f();
    private final Vector3f axis = new Vector3f(), euler = new Vector3f();
    private final Quaternionf dq = new Quaternionf();
    private final Matrix4f invM = new Matrix4f();
    private final float[] finiteTmp = new float[16];
    private final Vector3f headW = new Vector3f(), vFrom = new Vector3f(), vTo = new Vector3f();
    private final Quaternionf curRot = new Quaternionf(), parRot = new Quaternionf(),
            delta = new Quaternionf(), newRot = new Quaternionf();

    public Animator(MmdModel model, VmdMotion motion) {
        this.model = model;
        this.motion = motion;
        this.n = model.bones.size();

        parent = new int[n];
        restLocal = new Vector3f[n];
        invBind = new Matrix4f[n];
        appendParent = new int[n];
        appendRatio = new float[n];
        appendRotateOn = new boolean[n];
        appendTransOn = new boolean[n];
        animTrans = new Vector3f[n];
        animRot = new Quaternionf[n];
        ikRot = new Quaternionf[n];
        appendRot = new Quaternionf[n];
        appendTrans = new Vector3f[n];
        world = new Matrix4f[n];
        skinning = new Matrix4f[n];
        physOverride = new boolean[n];
        physLocal = new Quaternionf[n];

        for (int i = 0; i < n; i++) {
            Bone b = model.bones.get(i);
            parent[i] = (b.parentIndex >= 0 && b.parentIndex < n && b.parentIndex != i) ? b.parentIndex : -1;

            animTrans[i] = new Vector3f();
            animRot[i] = new Quaternionf();
            ikRot[i] = new Quaternionf();
            appendRot[i] = new Quaternionf();
            appendTrans[i] = new Vector3f();
            world[i] = new Matrix4f();
            skinning[i] = new Matrix4f();
            physLocal[i] = new Quaternionf();
            invBind[i] = new Matrix4f().translation(-b.position[0], -b.position[1], -b.position[2]);

            appendRotateOn[i] = (b.flags & 0x0100) != 0;
            appendTransOn[i] = (b.flags & 0x0200) != 0;
            boolean validAppend = (appendRotateOn[i] || appendTransOn[i])
                    && b.inheritParentIndex >= 0 && b.inheritParentIndex < n && b.inheritParentIndex != i;
            appendParent[i] = validAppend ? b.inheritParentIndex : -1;
            appendRatio[i] = b.inheritParentRatio;

            tracks.add(VmdNames.find(motion.boneTracks, b.name, 15));
        }

        for (int i = 0; i < n; i++) {
            Bone b = model.bones.get(i);
            Vector3f rest = new Vector3f(b.position[0], b.position[1], b.position[2]);
            if (parent[i] >= 0) {
                Bone p = model.bones.get(parent[i]);
                rest.sub(p.position[0], p.position[1], p.position[2]);
            }
            restLocal[i] = rest;
        }

        order = new int[n];
        orderPos = new int[n];
        boolean[] visited = new boolean[n];
        int[] cnt = {0};
        for (int i = 0; i < n; i++) visit(i, visited, cnt);
        for (int k = 0; k < n; k++) orderPos[order[k]] = k;

        List<Integer> iks = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            Bone b = model.bones.get(order[k]);
            if ((b.flags & 0x0020) != 0 && b.ikTargetIndex >= 0 && b.ikTargetIndex < n && b.ikLinkIndices.length > 0) {
                iks.add(order[k]);
            }
        }
        ikBones = iks.stream().mapToInt(Integer::intValue).toArray();
        for (int ik : ikBones) {
            ikStates.add(VmdNames.find(motion.ikTracks, model.bones.get(ik).name, 20));
        }

        int matched = 0;
        for (TreeMap<Integer, BoneKeyframe> t : tracks) if (t != null) matched++;
        System.out.println("[ANIMATOR] Bone khớp track: " + matched + "/" + n + ", IK: " + ikBones.length);
    }

    public void setMorphAnimator(MorphAnimator morph) { this.morph = morph; }

    public void setPhysics(MmdPhysics physics) { this.physics = physics; }

    public void setPhysicsEnabled(boolean enabled) { this.physicsEnabled = enabled; }

    public void resetPhysics() { if (physics != null) physics.reset(); }

    private void visit(int i, boolean[] visited, int[] cnt) {
        if (visited[i]) return;
        visited[i] = true;
        if (parent[i] >= 0) visit(parent[i], visited, cnt);
        if (appendParent[i] >= 0) visit(appendParent[i], visited, cnt);
        order[cnt[0]++] = i;
    }

    // ================= SAMPLE KEYFRAME (Bezier) =================

    private void sample(TreeMap<Integer, BoneKeyframe> track, float frame, Vector3f outPos, Quaternionf outRot) {
        outPos.zero();
        outRot.identity();
        if (track == null || track.isEmpty()) return;

        int f = (int) Math.floor(frame);
        Map.Entry<Integer, BoneKeyframe> lo = track.floorEntry(f);
        if (lo == null) {
            BoneKeyframe first = track.firstEntry().getValue();
            outPos.set(first.position);
            outRot.set(first.rotation);
        } else {
            BoneKeyframe a = lo.getValue();
            Map.Entry<Integer, BoneKeyframe> hi = track.higherEntry(f);
            if (hi == null) {
                outPos.set(a.position);
                outRot.set(a.rotation);
            } else {
                BoneKeyframe b = hi.getValue();
                float span = b.time - a.time;
                float t = span > 0 ? (frame - a.time) / span : 0f;
                t = Math.max(0f, Math.min(1f, t));

                float tx = b.curve(0, t), ty = b.curve(1, t), tz = b.curve(2, t), tr = b.curve(3, t);
                outPos.x = a.position.x + (b.position.x - a.position.x) * tx;
                outPos.y = a.position.y + (b.position.y - a.position.y) * ty;
                outPos.z = a.position.z + (b.position.z - a.position.z) * tz;
                a.rotation.slerp(b.rotation, tr, outRot);
            }
        }

        if (!outRot.isFinite() || outRot.lengthSquared() < 0.0001f) outRot.identity();
        else outRot.normalize();
    }

    // ================= BONE TRANSFORM =================

    private void updateAppend(int i) {
        appendRot[i].identity();
        appendTrans[i].zero();
        int src = appendParent[i];
        if (src < 0) return;

        if (appendRotateOn[i]) {
            Quaternionf q = tQ1.set(ikRot[src]).mul(animRot[src]).mul(appendRot[src]);
            float ratio = appendRatio[i];
            if (ratio < 0f) { q.conjugate(); ratio = -ratio; }
            appendRot[i].identity().slerp(q, ratio);
        }
        if (appendTransOn[i]) {
            appendTrans[i].set(animTrans[src]).add(appendTrans[src]).mul(appendRatio[i]);
        }
    }

    private void updateBone(int i) {
        Quaternionf r = physOverride[i]
                ? tQ2.set(physLocal[i])
                : tQ2.set(ikRot[i]).mul(animRot[i]).mul(appendRot[i]);
        tV.set(restLocal[i]).add(animTrans[i]).add(appendTrans[i]);
        if (parent[i] >= 0) world[i].set(world[parent[i]]);
        else world[i].identity();
        world[i].translate(tV).rotate(r);
    }

    // ================= IK (CCD) =================

    private boolean ikEnabled(int k, float frame) {
        TreeMap<Integer, Boolean> t = ikStates.get(k);
        if (t == null) return true;
        Map.Entry<Integer, Boolean> e = t.floorEntry((int) Math.floor(frame));
        return e == null || e.getValue();
    }

    private void solveIK(int ikBone) {
        Bone b = model.bones.get(ikBone);
        int eff = b.ikTargetIndex;
        int[] links = b.ikLinkIndices;
        float limitAngle = b.ikLimitAngle;

        world[ikBone].getTranslation(targetPos);

        for (int iter = 0; iter < b.ikLoopCount; iter++) {
            for (int j = 0; j < links.length; j++) {
                int lk = links[j];
                if (lk < 0 || lk >= n) continue;

                world[eff].getTranslation(effPos);
                if (effPos.distanceSquared(targetPos) < 1e-8f) return;

                invM.set(world[lk]).invertAffine();
                invM.transformPosition(effPos, localEff);
                invM.transformPosition(targetPos, localTarget);
                if (localEff.lengthSquared() < 1e-10f || localTarget.lengthSquared() < 1e-10f) continue;
                localEff.normalize();
                localTarget.normalize();

                float dot = Math.max(-1f, Math.min(1f, localEff.dot(localTarget)));
                float angle = (float) Math.acos(dot);
                if (angle < 1e-5f) continue;
                if (limitAngle > 0f && angle > limitAngle) angle = limitAngle;

                localEff.cross(localTarget, axis);

                boolean hasLimit = j < b.ikLinkHasLimit.length && b.ikLinkHasLimit[j];
                float[] mn = hasLimit ? b.ikLinkMin[j] : null;
                float[] mx = hasLimit ? b.ikLinkMax[j] : null;

                boolean kneeLike = hasLimit
                        && Math.abs(mn[1]) < 1e-4f && Math.abs(mx[1]) < 1e-4f
                        && Math.abs(mn[2]) < 1e-4f && Math.abs(mx[2]) < 1e-4f
                        && (Math.abs(mn[0]) > 1e-4f || Math.abs(mx[0]) > 1e-4f);

                if (kneeLike) {
                    float sign = Math.abs(axis.x) > 1e-6f ? Math.signum(axis.x) : (Math.max(mn[0], mx[0]) <= 0f ? -1f : 1f);
                    axis.set(sign, 0f, 0f);
                } else {
                    if (axis.lengthSquared() < 1e-12f) continue;
                    axis.normalize();
                }

                dq.setAngleAxis(angle, axis.x, axis.y, axis.z);

                Quaternionf fin = tQ1.set(ikRot[lk]).mul(animRot[lk]).mul(dq);

                if (hasLimit) {
                    fin.normalize();
                    fin.getEulerAnglesXYZ(euler);
                    euler.x = clamp(euler.x, mn[0], mx[0]);
                    euler.y = clamp(euler.y, mn[1], mx[1]);
                    euler.z = clamp(euler.z, mn[2], mx[2]);
                    fin.rotationXYZ(euler.x, euler.y, euler.z);
                }

                tQ2.set(animRot[lk]).conjugate();
                ikRot[lk].set(fin).mul(tQ2).normalize();

                int end = Math.max(orderPos[eff], orderPos[lk]);
                for (int k = orderPos[lk]; k <= end; k++) updateBone(order[k]);
            }
        }
    }

    private static float clamp(float v, float a, float b) {
        float lo = Math.min(a, b), hi = Math.max(a, b);
        return Math.max(lo, Math.min(hi, v));
    }

    // ================= PHYSICS =================

    /** Mô phỏng particle rồi xoay các bone có rigid body động về phía particle của nó. */
    private void applyPhysics(float dt) {
        physics.simulate(world, invBind, dt);

        for (int k = 0; k < n; k++) {
            int i = order[k];
            updateBone(i); // pose animation, với parent đã có rotation từ physics

            int body = physics.bodyOfBone(i);
            if (body < 0) continue;

            world[i].getTranslation(headW);
            world[i].getNormalizedRotation(curRot);

            Bone b = model.bones.get(i);
            vFrom.set(physics.bodyBindPos(body)).sub(b.position[0], b.position[1], b.position[2]);
            vTo.set(physics.bodyPos(body)).sub(headW);
            if (vFrom.lengthSquared() < 1e-8f || vTo.lengthSquared() < 1e-8f) continue;

            curRot.transform(vFrom);
            vFrom.normalize();
            vTo.normalize();

            delta.rotationTo(vFrom, vTo);
            delta.mul(curRot, newRot);

            if (parent[i] >= 0) world[parent[i]].getNormalizedRotation(parRot).conjugate();
            else parRot.identity();
            parRot.mul(newRot, physLocal[i]).normalize();

            physOverride[i] = true;
            updateBone(i);
        }
    }

    // ================= PUBLIC =================

    public void update(float frame) {
        update(frame, 0f);
    }

    public void update(float frame, float dt) {
        Arrays.fill(physOverride, false);

        for (int i = 0; i < n; i++) {
            sample(tracks.get(i), frame, animTrans[i], animRot[i]);
            if (morph != null) {
                animTrans[i].add(morph.boneMorphTrans[i]);
                animRot[i].mul(morph.boneMorphRot[i]);
            }
            ikRot[i].identity();
        }

        // Pass 1: pose + append
        for (int k = 0; k < n; k++) {
            int i = order[k];
            updateAppend(i);
            updateBone(i);
        }

        // Pass 2: IK
        for (int k = 0; k < ikBones.length; k++) {
            if (ikEnabled(k, frame)) solveIK(ikBones[k]);
        }

        // Pass 3: tính lại append + world
        for (int k = 0; k < n; k++) {
            int i = order[k];
            updateAppend(i);
            updateBone(i);
        }

        // Pass 4: physics (tóc, váy...)
        if (physics != null && physicsEnabled) applyPhysics(dt);
    }

    /** Trả về mảng dùng lại giữa các frame. */
    public Matrix4f[] getSkinningMatrices() {
        for (int i = 0; i < n; i++) {
            skinning[i].set(world[i]).mul(invBind[i]);
            if (!isFinite(skinning[i])) skinning[i].identity();
        }
        return skinning;
    }

    private boolean isFinite(Matrix4f m) {
        m.get(finiteTmp);
        for (float f : finiteTmp) if (!Float.isFinite(f)) return false;
        return true;
    }

    public Vector3f getCenterPosition() {
        for (int i = 0; i < n; i++) {
            String name = model.bones.get(i).name;
            if (name.equals("センター") || name.equalsIgnoreCase("center")) {
                Vector3f pos = new Vector3f();
                world[i].getTranslation(pos);
                return pos;
            }
        }
        return new Vector3f(0, 0, 0);
    }
}