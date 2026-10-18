package vn.pmgteam.jmmd.render;

import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;

public class Shader {
    private int programId;

    public Shader(String vertSrc, String fragSrc) {
        int vertId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertId, vertSrc);
        GL20.glCompileShader(vertId);
        if (GL20.glGetShaderi(vertId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Vertex compile error: " + GL20.glGetShaderInfoLog(vertId));
        }

        int fragId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragId, fragSrc);
        GL20.glCompileShader(fragId);
        if (GL20.glGetShaderi(fragId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Fragment compile error: " + GL20.glGetShaderInfoLog(fragId));
        }

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertId);
        GL20.glAttachShader(programId, fragId);
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Program link error: " + GL20.glGetProgramInfoLog(programId));
        }
        GL20.glDeleteShader(vertId);
        GL20.glDeleteShader(fragId);
    }

    public void use() { GL20.glUseProgram(programId); }

    public void setMat4(String name, Matrix4f mat) {
        int loc = GL20.glGetUniformLocation(programId, name);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mat.get(0, fb);
            GL20.glUniformMatrix4fv(loc, false, fb);
        }
    }

    public void setMat4Array(String name, Matrix4f[] mats) {
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc == -1) loc = GL20.glGetUniformLocation(programId, name + "[0]");
        if (loc == -1) {
            System.err.println("CẢNH BÁO: Không tìm thấy Uniform '" + name + "' trong Shader!");
            return;
        }

        FloatBuffer fb = MemoryUtil.memAllocFloat(mats.length * 16);
        try {
            // get(index, buffer): ghi tại vị trí tuyệt đối, không phụ thuộc position
            for (int i = 0; i < mats.length; i++) mats[i].get(i * 16, fb);
            // position = 0, limit = capacity -> upload đủ mats.length ma trận
            GL20.glUniformMatrix4fv(loc, false, fb);
        } finally {
            MemoryUtil.memFree(fb);
        }
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(GL20.glGetUniformLocation(programId, name), x, y, z, w);
    }
    public void setVec3(String name, float x, float y, float z) {
        GL20.glUniform3f(GL20.glGetUniformLocation(programId, name), x, y, z);
    }
    public void setInt(String name, int val) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, name), val);
    }
    public void setFloat(String name, float val) {
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, name), val);
    }
    public void setBool(String name, boolean val) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, name), val ? 1 : 0);
    }
}