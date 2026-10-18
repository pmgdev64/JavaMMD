package vn.pmgteam.jmmd.io;

import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.data.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PmxReader {

    public static MmdModel read(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buf);
            buf.flip();
            return parse(buf);
        }
    }

    private static MmdModel parse(ByteBuffer buf) {
        MmdModel model = new MmdModel();

        byte[] magic = new byte[4];
        buf.get(magic);
        if (!new String(magic, StandardCharsets.US_ASCII).equals("PMX ")) throw new IllegalArgumentException("Not a valid PMX file!");

        model.version = buf.getFloat();
        int globalsCount = buf.get();
        int[] globals = new int[globalsCount];
        for (int i = 0; i < globalsCount; i++) globals[i] = buf.get() & 0xFF;

        int textEncoding = globals[0];
        model.extraUvCount = globals[1];
        model.vertexIndexSize = globals[2];
        model.textureIndexSize = globals[3];
        model.materialIndexSize = globals[4];
        model.boneIndexSize = globals[5];
        model.morphIndexSize = globals[6];
        model.rigidBodyIndexSize = globals[7];

        Charset charset = (textEncoding == 0) ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;

        model.name = readString(buf, charset); model.nameEn = readString(buf, charset);
        model.comment = readString(buf, charset); model.commentEn = readString(buf, charset);

        // 4. VERTICES
        int vertexCount = buf.getInt();
        for (int i = 0; i < vertexCount; i++) {
            Vertex v = new Vertex();
            v.position[0] = buf.getFloat(); v.position[1] = buf.getFloat(); v.position[2] = buf.getFloat();
            v.normal[0] = buf.getFloat(); v.normal[1] = buf.getFloat(); v.normal[2] = buf.getFloat();
            v.uv[0] = buf.getFloat(); v.uv[1] = buf.getFloat();

            if (model.extraUvCount > 0) {
                v.extraUvs = new float[model.extraUvCount][4];
                for (int j = 0; j < model.extraUvCount; j++)
                    for (int k = 0; k < 4; k++) v.extraUvs[j][k] = buf.getFloat();
            }

            int weightType = buf.get();
            v.weightType = Vertex.WeightType.values()[weightType];

            switch (weightType) {
                case 0:
                    v.boneIndices[0] = readIndex(buf, model.boneIndexSize); v.boneWeights[0] = 1.0f; break;
                case 1:
                    v.boneIndices[0] = readIndex(buf, model.boneIndexSize); v.boneIndices[1] = readIndex(buf, model.boneIndexSize);
                    v.boneWeights[0] = buf.getFloat(); v.boneWeights[1] = 1.0f - v.boneWeights[0]; break;
                case 2: case 4:
                    for (int j = 0; j < 4; j++) v.boneIndices[j] = readIndex(buf, model.boneIndexSize);
                    for (int j = 0; j < 4; j++) v.boneWeights[j] = buf.getFloat(); break;
                case 3:
                    v.boneIndices[0] = readIndex(buf, model.boneIndexSize); v.boneIndices[1] = readIndex(buf, model.boneIndexSize);
                    v.boneWeights[0] = buf.getFloat(); v.boneWeights[1] = 1.0f - v.boneWeights[0];
                    for (int j = 0; j < 3; j++) v.sdefC[j] = buf.getFloat();
                    for (int j = 0; j < 3; j++) v.sdefR0[j] = buf.getFloat();
                    for (int j = 0; j < 3; j++) v.sdefR1[j] = buf.getFloat(); break;
            }
            v.edgeScale = buf.getFloat();
            model.vertices.add(v);
        }

        // 5. FACES
        int faceIndexCount = buf.getInt();
        model.indices = new int[faceIndexCount];
        for (int i = 0; i < faceIndexCount; i++) model.indices[i] = readUnsignedIndex(buf, model.vertexIndexSize);

        // 6. TEXTURES
        int textureCount = buf.getInt();
        for (int i = 0; i < textureCount; i++) model.textures.add(readString(buf, charset));

        // 7. MATERIALS
        int materialCount = buf.getInt();
        for (int i = 0; i < materialCount; i++) {
            Material mat = new Material();
            mat.name = readString(buf, charset); mat.nameEn = readString(buf, charset);
            for (int j = 0; j < 4; j++) mat.diffuse[j] = buf.getFloat();
            for (int j = 0; j < 3; j++) mat.specular[j] = buf.getFloat();
            mat.specularPower = buf.getFloat();
            for (int j = 0; j < 3; j++) mat.ambient[j] = buf.getFloat();
            mat.flags = buf.get();
            for (int j = 0; j < 4; j++) mat.edgeColor[j] = buf.getFloat();
            mat.edgeSize = buf.getFloat();
            mat.textureIndex = readIndex(buf, model.textureIndexSize);
            mat.sphereTextureIndex = readIndex(buf, model.textureIndexSize);
            mat.sphereMode = buf.get();
            int toonShared = buf.get();
            if (toonShared == 0) mat.toonIndex = readIndex(buf, model.textureIndexSize);
            else mat.toonIndex = buf.get() & 0xFF;
            mat.memo = readString(buf, charset);
            mat.faceCount = buf.getInt();
            model.materials.add(mat);
        }

        // 8. BONES
        int boneCount = buf.getInt();
        System.out.println("[DEBUG] Bone count: " + boneCount);

        for (int i = 0; i < boneCount; i++) {
            Bone bone = new Bone();
            bone.name = readString(buf, charset); bone.nameEn = readString(buf, charset);
            for (int j = 0; j < 3; j++) bone.position[j] = buf.getFloat();
            bone.parentIndex = readIndex(buf, model.boneIndexSize);
            bone.layer = buf.getInt();
            bone.flags = buf.getShort() & 0xFFFF;

            if ((bone.flags & 0x0001) != 0) bone.tailBoneIndex = readIndex(buf, model.boneIndexSize);
            else for (int j = 0; j < 3; j++) bone.tailOffset[j] = buf.getFloat();

            if ((bone.flags & 0x0100) != 0 || (bone.flags & 0x0200) != 0) {
                bone.inheritParentIndex = readIndex(buf, model.boneIndexSize);
                bone.inheritParentRatio = buf.getFloat();
            }

            if ((bone.flags & 0x0400) != 0) for (int j = 0; j < 3; j++) bone.localAxisX[j] = buf.getFloat();
            if ((bone.flags & 0x0800) != 0) {
                for (int j = 0; j < 3; j++) bone.localAxisX[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) bone.localAxisZ[j] = buf.getFloat();
            }
            if ((bone.flags & 0x2000) != 0) bone.externalParentKey = buf.getInt();

            if ((bone.flags & 0x0020) != 0) {
                bone.ikTargetIndex = readIndex(buf, model.boneIndexSize);
                bone.ikLoopCount = buf.getInt();
                bone.ikLimitAngle = buf.getFloat();
                int ikLinkCount = buf.getInt();
                bone.ikLinkIndices = new int[ikLinkCount];
                bone.ikLinkHasLimit = new boolean[ikLinkCount];
                bone.ikLinkMin = new float[ikLinkCount][3];
                bone.ikLinkMax = new float[ikLinkCount][3];
                for (int j = 0; j < ikLinkCount; j++) {
                    bone.ikLinkIndices[j] = readIndex(buf, model.boneIndexSize);
                    boolean hasLimit = buf.get() == 1;
                    bone.ikLinkHasLimit[j] = hasLimit;
                    if (hasLimit) {
                        for (int k = 0; k < 3; k++) bone.ikLinkMin[j][k] = buf.getFloat();
                        for (int k = 0; k < 3; k++) bone.ikLinkMax[j][k] = buf.getFloat();
                    }
                }
            }
            model.bones.add(bone);
        }

        try {
            // 9. MORPHS
            int morphCount = buf.getInt();
            System.out.println("[DEBUG] Morph count: " + morphCount);

            for (int i = 0; i < morphCount; i++) {
                Morph morph = new Morph();
                morph.name = readString(buf, charset); morph.nameEn = readString(buf, charset);
                morph.panel = buf.get();
                morph.morphType = buf.get();
                int offsetCount = buf.getInt();

                if (offsetCount < 0 || offsetCount > 1000000) throw new RuntimeException("offsetCount lỗi: " + offsetCount);

                for (int j = 0; j < offsetCount; j++) {
                    Morph.MorphOffset offset = new Morph.MorphOffset();
                    switch (morph.morphType) {
                        case 0: case 9:
                            offset.index = readIndex(buf, model.morphIndexSize); offset.ratio = buf.getFloat(); break;
                        case 1:
                            offset.index = readUnsignedIndex(buf, model.vertexIndexSize);
                            for (int k = 0; k < 3; k++) offset.vertexOffset[k] = buf.getFloat(); break;
                        case 2:
                            offset.index = readIndex(buf, model.boneIndexSize);
                            for (int k = 0; k < 3; k++) offset.boneTranslation[k] = buf.getFloat();
                            for (int k = 0; k < 4; k++) offset.boneRotation[k] = buf.getFloat(); break;
                        case 3: case 4: case 5: case 6: case 7:
                            offset.index = readUnsignedIndex(buf, model.vertexIndexSize);
                            for (int k = 0; k < 4; k++) offset.uvOffset[k] = buf.getFloat(); break;
                        case 8:
                            offset.index = readIndex(buf, model.materialIndexSize);
                            offset.matOperation = buf.get();
                            for (int k = 0; k < 4; k++) offset.matDiffuse[k] = buf.getFloat();
                            for (int k = 0; k < 3; k++) offset.matSpecular[k] = buf.getFloat();
                            offset.matSpecularity = buf.getFloat();
                            for (int k = 0; k < 3; k++) offset.matAmbient[k] = buf.getFloat();
                            for (int k = 0; k < 4; k++) offset.matEdgeColor[k] = buf.getFloat();
                            offset.matEdgeSize = buf.getFloat();
                            for (int k = 0; k < 4; k++) offset.matTextureTint[k] = buf.getFloat();
                            for (int k = 0; k < 4; k++) offset.matSphereTint[k] = buf.getFloat();
                            for (int k = 0; k < 4; k++) offset.matToonTint[k] = buf.getFloat(); break;
                        case 10:
                            offset.rbIndex = readIndex(buf, model.rigidBodyIndexSize);
                            offset.localFlag = buf.get();
                            for (int k = 0; k < 3; k++) offset.rbVelocity[k] = buf.getFloat();
                            for (int k = 0; k < 3; k++) offset.rbTorque[k] = buf.getFloat(); break;
                    }
                    morph.offsets.add(offset);
                }
                model.morphs.add(morph);
            }

            // 10. DISPLAY FRAMES
            int frameCount = buf.getInt();
            for (int i = 0; i < frameCount; i++) {
                DisplayFrame frame = new DisplayFrame();
                frame.name = readString(buf, charset); frame.nameEn = readString(buf, charset);
                frame.specialFlag = buf.get();
                int elementCount = buf.getInt();
                for (int j = 0; j < elementCount; j++) {
                    DisplayFrame.FrameElement el = new DisplayFrame.FrameElement();
                    el.type = buf.get();
                    el.index = (el.type == 0) ? readIndex(buf, model.boneIndexSize) : readIndex(buf, model.morphIndexSize);
                    frame.elements.add(el);
                }
                model.displayFrames.add(frame);
            }

            // 11. RIGID BODIES
            int rbCount = buf.getInt();
            for (int i = 0; i < rbCount; i++) {
                RigidBody rb = new RigidBody();
                rb.name = readString(buf, charset); rb.nameEn = readString(buf, charset);
                rb.boneIndex = readIndex(buf, model.boneIndexSize);
                rb.group = buf.get() & 0xFF;
                rb.collisionMask = buf.getShort() & 0xFFFF;
                rb.shapeType = buf.get();
                rb.width = buf.getFloat(); rb.height = buf.getFloat(); rb.depth = buf.getFloat();
                for (int j = 0; j < 3; j++) rb.position[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) rb.rotation[j] = buf.getFloat();
                rb.mass = buf.getFloat();
                rb.linearDamping = buf.getFloat();
                rb.angularDamping = buf.getFloat();
                rb.restitution = buf.getFloat();
                rb.friction = buf.getFloat();
                rb.physicsMode = buf.get();
                model.rigidBodies.add(rb);
            }

            // 12. JOINTS
            int jointCount = buf.getInt();
            for (int i = 0; i < jointCount; i++) {
                Joint jt = new Joint();
                jt.name = readString(buf, charset); jt.nameEn = readString(buf, charset);
                buf.get(); // type (PMX 2.0 chỉ có 0 = spring 6DOF)
                jt.rigidBodyAIndex = readIndex(buf, model.rigidBodyIndexSize);
                jt.rigidBodyBIndex = readIndex(buf, model.rigidBodyIndexSize);
                for (int j = 0; j < 3; j++) jt.position[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.rotation[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.positionLimitMin[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.positionLimitMax[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.rotationLimitMin[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.rotationLimitMax[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.springPosition[j] = buf.getFloat();
                for (int j = 0; j < 3; j++) jt.springRotation[j] = buf.getFloat();
                model.joints.add(jt);
            }
        } catch (Exception e) {
            System.err.println("[CẢNH BÁO] Bỏ qua phần Morphs/Frames/Physics do dữ liệu không chuẩn: " + e.getMessage());
        }

        System.out.println("[DEBUG] Rigid bodies: " + model.rigidBodies.size() + ", joints: " + model.joints.size());
        return model;
    }

    private static int readIndex(ByteBuffer buf, int size) {
        switch (size) {
            case 1: return buf.get(); case 2: return buf.getShort(); case 4: return buf.getInt();
            default: throw new IllegalArgumentException("Invalid index size: " + size);
        }
    }

    private static int readUnsignedIndex(ByteBuffer buf, int size) {
        switch (size) {
            case 1: return buf.get() & 0xFF; case 2: return buf.getShort() & 0xFFFF; case 4: return buf.getInt();
            default: throw new IllegalArgumentException("Invalid index size: " + size);
        }
    }

    private static String readString(ByteBuffer buf, Charset charset) {
        if (buf.remaining() < 4) throw new RuntimeException("Hết buffer!");
        int length = buf.getInt();
        if (length < 0 || length > buf.remaining()) throw new RuntimeException("Độ dài chuỗi lỗi: " + length);
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, charset);
    }
}