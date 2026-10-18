package vn.pmgteam.jmmd.render;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class GridRenderer {
    private static final String VERT =
        "#version 330 core\n" +
        "layout(location = 0) in vec3 aPos;\n" +
        "layout(location = 1) in vec3 aColor;\n" +
        "uniform mat4 projection;\n" +
        "uniform mat4 view;\n" +
        "out vec3 vColor;\n" +
        "void main() {\n" +
        "    vColor = aColor;\n" +
        "    gl_Position = projection * view * vec4(aPos, 1.0);\n" +
        "}\n";

    private static final String FRAG =
        "#version 330 core\n" +
        "in vec3 vColor;\n" +
        "out vec4 FragColor;\n" +
        "void main() {\n" +
        "    FragColor = vec4(vColor, 1.0);\n" +
        "}\n";

    private final Shader shader;
    private final int vao, vbo;
    private final int vertexCount;

    /**
     * @param halfCells  số ô mỗi nửa lưới (lưới rộng halfCells*2 ô)
     * @param cellSize   kích thước 1 ô (đơn vị MMD)
     * @param majorEvery cứ bao nhiêu ô thì có 1 đường đậm
     */
    public GridRenderer(int halfCells, float cellSize, int majorEvery) {
        shader = new Shader(VERT, FRAG);

        float ext = halfCells * cellSize;
        int lines = (halfCells * 2 + 1) * 2;
        float[] data = new float[lines * 2 * 6]; // mỗi đường 2 điểm, mỗi điểm 6 float
        int o = 0;

        for (int i = -halfCells; i <= halfCells; i++) {
            float p = i * cellSize;
            float[] c;
            if (i == 0) c = new float[]{0.3f, 0.3f, 1.0f};          // trục Z (xanh dương)
            else if (i % majorEvery == 0) c = new float[]{0.65f, 0.65f, 0.65f};
            else c = new float[]{0.42f, 0.42f, 0.42f};

            // Đường chạy dọc theo Z tại x = p
            o = put(data, o, p, 0, -ext, c);
            o = put(data, o, p, 0, ext, c);

            if (i == 0) c = new float[]{1.0f, 0.3f, 0.3f};          // trục X (đỏ)

            // Đường chạy dọc theo X tại z = p
            o = put(data, o, -ext, 0, p, c);
            o = put(data, o, ext, 0, p, c);
        }

        vertexCount = data.length / 6;

        vao = GL30.glGenVertexArrays();
        vbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(vao);

        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        fb.put(data).flip();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, fb, GL30.GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);

        int stride = 6 * 4;
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, stride, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 3, GL30.GL_FLOAT, false, stride, 3 * 4);
        GL30.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);
    }

    private static int put(float[] d, int o, float x, float y, float z, float[] c) {
        d[o++] = x; d[o++] = y; d[o++] = z;
        d[o++] = c[0]; d[o++] = c[1]; d[o++] = c[2];
        return o;
    }

    public void render(Matrix4f projection, Matrix4f view) {
        shader.use();
        shader.setMat4("projection", projection);
        shader.setMat4("view", view);

        GL30.glDisable(GL30.GL_BLEND);
        GL30.glBindVertexArray(vao);
        GL30.glDrawArrays(GL30.GL_LINES, 0, vertexCount);
        GL30.glBindVertexArray(0);
    }

    public void dispose() {
        GL30.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}