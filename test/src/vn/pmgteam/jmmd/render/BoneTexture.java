package vn.pmgteam.jmmd.render;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

public class BoneTexture {
    private int textureId;
    private int maxBones;
    private int width = 256;
    private int height;

    public BoneTexture(int maxBones) {
        this.maxBones = maxBones;
        this.height = (maxBones * 4) / width;

        textureId = GL30.glGenTextures();
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, textureId);
        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, height, 0, GL30.GL_RGBA, GL30.GL_FLOAT, 0);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_NEAREST);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_NEAREST);
    }

    public void update(Matrix4f[] matrices) {
        int count = Math.min(matrices.length, maxBones);
        FloatBuffer fb = MemoryUtil.memAllocFloat(maxBones * 16);
        try {
            Matrix4f identity = new Matrix4f();
            for (int i = 0; i < maxBones; i++) {
                (i < count ? matrices[i] : identity).get(i * 16, fb);
            }

            GL30.glBindTexture(GL30.GL_TEXTURE_2D, textureId);
            GL30.glTexSubImage2D(GL30.GL_TEXTURE_2D, 0, 0, 0, width, height, GL30.GL_RGBA, GL30.GL_FLOAT, fb);
        } finally {
            MemoryUtil.memFree(fb);
        }
    }

    public void bind(int unit) {
        GL30.glActiveTexture(GL30.GL_TEXTURE0 + unit);
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, textureId);
    }
}