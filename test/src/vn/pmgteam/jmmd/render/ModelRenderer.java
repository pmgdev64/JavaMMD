package vn.pmgteam.jmmd.render;

import org.lwjgl.opengl.GL30;
import vn.pmgteam.jmmd.data.Material;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelRenderer {
    private static Map<String, Texture> textureCache = new HashMap<>();

    private static class RenderMaterial {
        Material mat;
        float[] diffuse;
        int indexOffset;
    }

    /** diffuseOverride[i] = diffuse đã áp morph của material i (null = dùng diffuse gốc). */
    public static void render(ModelMesh mesh, Shader shader, File modelDir, float[][] diffuseOverride) {
        GL30.glBindVertexArray(mesh.vao);

        List<RenderMaterial> opaqueMats = new ArrayList<>();
        List<RenderMaterial> transparentMats = new ArrayList<>();

        int currentIndexOffset = 0;
        for (int mi = 0; mi < mesh.model.materials.size(); mi++) {
            Material mat = mesh.model.materials.get(mi);
            if (mat.faceCount == 0) continue;

            float[] d = (diffuseOverride != null && mi < diffuseOverride.length) ? diffuseOverride[mi] : mat.diffuse;

            if (d[3] <= 0.01f) {
                currentIndexOffset += mat.faceCount;
                continue;
            }
            RenderMaterial rm = new RenderMaterial();
            rm.mat = mat;
            rm.diffuse = d;
            rm.indexOffset = currentIndexOffset;

            if (d[3] < 0.99f) transparentMats.add(rm);
            else opaqueMats.add(rm);

            currentIndexOffset += mat.faceCount;
        }

        // PASS 1: OPAQUE
        shader.setBool("isOutlinePass", false);
        GL30.glDisable(GL30.GL_BLEND);
        GL30.glDepthMask(true);
        for (RenderMaterial rm : opaqueMats) drawMaterial(mesh, shader, modelDir, rm);

        // PASS 2: OUTLINE
        shader.setBool("isOutlinePass", true);
        shader.setFloat("outlineThickness", 0.02f);
        GL30.glEnable(GL30.GL_CULL_FACE);
        GL30.glCullFace(GL30.GL_FRONT);
        GL30.glDepthMask(false);
        for (RenderMaterial rm : opaqueMats) {
            if ((rm.mat.flags & 0x10) != 0) {
                GL30.glDrawElements(GL30.GL_TRIANGLES, rm.mat.faceCount, GL30.GL_UNSIGNED_INT, (long) rm.indexOffset * Integer.BYTES);
            }
        }

        // PASS 3: TRANSPARENT
        shader.setBool("isOutlinePass", false);
        GL30.glEnable(GL30.GL_BLEND);
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glDepthMask(false);
        for (RenderMaterial rm : transparentMats) drawMaterial(mesh, shader, modelDir, rm);

        GL30.glDepthMask(true);
        GL30.glDisable(GL30.GL_BLEND);
        GL30.glEnable(GL30.GL_CULL_FACE);
        GL30.glCullFace(GL30.GL_BACK);
        GL30.glBindVertexArray(0);
    }

    private static void drawMaterial(ModelMesh mesh, Shader shader, File modelDir, RenderMaterial rm) {
        Material mat = rm.mat;
        float[] d = rm.diffuse;
        shader.setVec4("materialColor", d[0], d[1], d[2], d[3]);

        if ((mat.flags & 0x01) != 0) GL30.glDisable(GL30.GL_CULL_FACE);
        else { GL30.glEnable(GL30.GL_CULL_FACE); GL30.glCullFace(GL30.GL_BACK); }

        boolean hasTex = false;
        if (mat.textureIndex >= 0 && mat.textureIndex < mesh.model.textures.size()) {
            String texName = mesh.model.textures.get(mat.textureIndex).replace("\\", "/");
            String path = new File(modelDir, texName).getAbsolutePath();

            Texture texture = textureCache.computeIfAbsent(path, p -> new Texture(p));
            if (texture.loaded) {
                GL30.glActiveTexture(GL30.GL_TEXTURE0);
                texture.bind();
                shader.setInt("texture_diffuse1", 0);
                hasTex = true;
            }
        }
        shader.setInt("hasTexture", hasTex ? 1 : 0);

        GL30.glDrawElements(GL30.GL_TRIANGLES, mat.faceCount, GL30.GL_UNSIGNED_INT, (long) rm.indexOffset * Integer.BYTES);
    }
}