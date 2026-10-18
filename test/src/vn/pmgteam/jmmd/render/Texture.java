package vn.pmgteam.jmmd.render;

import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Texture {
    public int id;
    /** false nếu file không đọc được (ví dụ .dds), renderer sẽ vẽ màu trơn thay vì đen. */
    public boolean loaded = false;

    public Texture(String filepath) {
        id = GL30.glGenTextures();
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);

        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_REPEAT);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_REPEAT);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR_MIPMAP_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer image = STBImage.stbi_load(filepath, width, height, channels, 4);

            if (image == null) {
                System.err.println("FAILED TO LOAD TEXTURE: " + filepath);
                System.err.println("Reason: " + STBImage.stbi_failure_reason());
                return;
            }

            GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA, width.get(0), height.get(0), 0,
                    GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, image);
            GL30.glGenerateMipmap(GL30.GL_TEXTURE_2D);
            loaded = true;

            STBImage.stbi_image_free(image);
        }
    }

    public void bind() {
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);
    }
}