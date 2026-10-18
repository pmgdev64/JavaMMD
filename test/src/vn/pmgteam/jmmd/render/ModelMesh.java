package vn.pmgteam.jmmd.render;

import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.data.Vertex;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ModelMesh {
    public int vao, vbo, posVbo, ebo;
    public int vertexCount;
    public MmdModel model;
    public int[] sortedIndices;

    private final float[] basePos;
    private final float[] curPos;

    // Normal(3) UV(2) BoneIdx(4) BoneW(4) SdefC(3)+flag(1) SdefR0(3) SdefR1(3) = 23 float
    private static final int FLOATS = 23;

    public ModelMesh(MmdModel model) {
        this.model = model;
        vertexCount = model.vertices.size();

        basePos = new float[vertexCount * 3];
        curPos = new float[vertexCount * 3];
        float[] data = new float[vertexCount * FLOATS];

        int o = 0, p = 0;
        for (Vertex v : model.vertices) {
            basePos[p++] = v.position[0];
            basePos[p++] = v.position[1];
            basePos[p++] = v.position[2];

            data[o++] = v.normal[0];
            data[o++] = v.normal[1];
            data[o++] = v.normal[2];

            data[o++] = v.uv[0];
            data[o++] = v.uv[1];

            // Index -1 (slot không dùng) -> 0, weight = 0 nên vô hại
            for (int j = 0; j < 4; j++) data[o++] = Math.max(0, v.boneIndices[j]);
            for (int j = 0; j < 4; j++) data[o++] = v.boneWeights[j];

            boolean sdef = v.weightType == Vertex.WeightType.SDEF;
            data[o++] = v.sdefC[0];
            data[o++] = v.sdefC[1];
            data[o++] = v.sdefC[2];
            data[o++] = sdef ? 1f : 0f;
            data[o++] = v.sdefR0[0];
            data[o++] = v.sdefR0[1];
            data[o++] = v.sdefR0[2];
            data[o++] = v.sdefR1[0];
            data[o++] = v.sdefR1[1];
            data[o++] = v.sdefR1[2];
        }
        System.arraycopy(basePos, 0, curPos, 0, basePos.length);

        vao = GL30.glGenVertexArrays();
        vbo = GL30.glGenBuffers();
        posVbo = GL30.glGenBuffers();
        ebo = GL30.glGenBuffers();

        GL30.glBindVertexArray(vao);

        // Position (dynamic, để morph cập nhật)
        FloatBuffer pb = MemoryUtil.memAllocFloat(curPos.length);
        pb.put(curPos).flip();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, posVbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, pb, GL30.GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(pb);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 3 * 4, 0);
        GL30.glEnableVertexAttribArray(0);

        // Phần còn lại (static)
        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        fb.put(data).flip();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, fb, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);

        IntBuffer ib = MemoryUtil.memAllocInt(model.indices.length);
        ib.put(model.indices).flip();
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL30.glBufferData(GL30.GL_ELEMENT_ARRAY_BUFFER, ib, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        int stride = FLOATS * 4;
        GL30.glVertexAttribPointer(1, 3, GL30.GL_FLOAT, false, stride, 0);
        GL30.glEnableVertexAttribArray(1);
        GL30.glVertexAttribPointer(2, 2, GL30.GL_FLOAT, false, stride, 3 * 4);
        GL30.glEnableVertexAttribArray(2);
        GL30.glVertexAttribPointer(3, 4, GL30.GL_FLOAT, false, stride, 5 * 4);
        GL30.glEnableVertexAttribArray(3);
        GL30.glVertexAttribPointer(4, 4, GL30.GL_FLOAT, false, stride, 9 * 4);
        GL30.glEnableVertexAttribArray(4);
        GL30.glVertexAttribPointer(5, 4, GL30.GL_FLOAT, false, stride, 13 * 4);
        GL30.glEnableVertexAttribArray(5);
        GL30.glVertexAttribPointer(6, 3, GL30.GL_FLOAT, false, stride, 17 * 4);
        GL30.glEnableVertexAttribArray(6);
        GL30.glVertexAttribPointer(7, 3, GL30.GL_FLOAT, false, stride, 20 * 4);
        GL30.glEnableVertexAttribArray(7);

        GL30.glBindVertexArray(0);
    }

    /** offsets = vertexCount*3 (cộng vào vị trí gốc), hoặc null để trả về vị trí gốc. */
    public void updatePositions(float[] offsets) {
        if (offsets == null) {
            System.arraycopy(basePos, 0, curPos, 0, curPos.length);
        } else {
            for (int i = 0; i < curPos.length; i++) curPos[i] = basePos[i] + offsets[i];
        }
        FloatBuffer fb = MemoryUtil.memAllocFloat(curPos.length);
        try {
            fb.put(curPos).flip();
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, posVbo);
            GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, fb);
        } finally {
            MemoryUtil.memFree(fb);
        }
    }
}