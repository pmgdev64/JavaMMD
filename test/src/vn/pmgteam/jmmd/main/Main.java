package vn.pmgteam.jmmd.main;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;

import vn.pmgteam.jmmd.animation.Animator;
import vn.pmgteam.jmmd.animation.MorphAnimator;
import vn.pmgteam.jmmd.animation.VmdMotion;
import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.io.PmxReader;
import vn.pmgteam.jmmd.io.VmdReader;
import vn.pmgteam.jmmd.physics.MmdPhysics;
import vn.pmgteam.jmmd.render.BoneTexture;
import vn.pmgteam.jmmd.render.Camera;
import vn.pmgteam.jmmd.render.GridRenderer;
import vn.pmgteam.jmmd.render.ModelMesh;
import vn.pmgteam.jmmd.render.ModelRenderer;
import vn.pmgteam.jmmd.render.Shader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static boolean isAnimating = true;  // phím A
    private static boolean showGrid = true;     // phím G
    private static boolean physicsOn = true;    // phím P
    private static boolean physicsReset = false;

    private static final float NEAR = 1.0f;
    private static final float FAR = 1000f;

    private static String loadResource(String path) throws IOException {
        try (InputStream is = Main.class.getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String localPath = "src" + path;
        Path file = Paths.get(localPath);
        if (Files.exists(file)) return Files.readString(file, StandardCharsets.UTF_8);
        throw new IOException("Không tìm thấy resource: " + path);
    }

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, 4);

        long window = GLFW.glfwCreateWindow(800, 600, "vn.pmgteam.jmmd in Maven Repository", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create GLFW window");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GL.createCapabilities();
        GL30.glEnable(GL30.GL_DEPTH_TEST);
        GL30.glEnable(GL30.GL_CULL_FACE);
        GL30.glEnable(GL30.GL_MULTISAMPLE);
        GL30.glCullFace(GL30.GL_BACK);

        Camera camera = new Camera();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), 800f / 600f, NEAR, FAR);

        GLFW.glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w <= 0 || h <= 0) return;
            GL30.glViewport(0, 0, w, h);
            projection.identity().perspective((float) Math.toRadians(45.0f), (float) w / h, NEAR, FAR);
        });

        double[] lastX = {0}, lastY = {0};
        boolean[] leftPressed = {false};
        boolean[] isFirstMouse = {true};

        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                leftPressed[0] = (action == GLFW.GLFW_PRESS);
                if (leftPressed[0]) {
                    double[] x = new double[1], y = new double[1];
                    GLFW.glfwGetCursorPos(window, x, y);
                    lastX[0] = x[0];
                    lastY[0] = y[0];
                }
            }
        });

        GLFW.glfwSetScrollCallback(window, (win, xoffset, yoffset) -> camera.zoom((float) yoffset));

        GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                if (key == GLFW.GLFW_KEY_R) camera.reset();
                if (key == GLFW.GLFW_KEY_A) {
                    isAnimating = !isAnimating;
                    physicsReset = true;
                    System.out.println("[ANIMATION] " + (isAnimating ? "BẬT" : "TẮT"));
                }
                if (key == GLFW.GLFW_KEY_G) {
                    showGrid = !showGrid;
                    System.out.println("[GRID] " + (showGrid ? "BẬT" : "TẮT"));
                }
                if (key == GLFW.GLFW_KEY_P) {
                    physicsOn = !physicsOn;
                    physicsReset = true;
                    System.out.println("[PHYSICS] " + (physicsOn ? "BẬT" : "TẮT"));
                }
            }
        });

        String vertSrc = loadResource("/resources/shaders/model_vert.glsl");
        String fragSrc = loadResource("/resources/shaders/model_frag.glsl");
        Shader shader = new Shader(vertSrc, fragSrc);

        GridRenderer grid = new GridRenderer(50, 1.0f, 10);

        Path pmxPath = Paths.get("src/resources/test/models.pmx");
        File modelDir = pmxPath.getParent() != null ? pmxPath.getParent().toFile() : new File(".");

        MmdModel model = PmxReader.read(pmxPath);
        ModelMesh mesh = new ModelMesh(model);

        Path vmdPath = Paths.get("src/resources/test/animations/anim.vmd");
        VmdMotion motion = VmdReader.read(vmdPath);

        Animator animator = new Animator(model, motion);
        MorphAnimator morphs = new MorphAnimator(model, motion);
        animator.setMorphAnimator(morphs);

        MmdPhysics physics = new MmdPhysics(model);
        animator.setPhysics(physics);

        int boneCount = Math.max(model.bones.size(), 1);
        System.out.println("[MAIN] Bone count: " + boneCount);
        Matrix4f[] boneMatrices = new Matrix4f[boneCount];
        for (int i = 0; i < boneCount; i++) boneMatrices[i] = new Matrix4f();

        int maxBones = ((boneCount + 63) / 64) * 64;
        BoneTexture boneTex = new BoneTexture(maxBones);

        Matrix4f modelMat = new Matrix4f().rotateY((float) Math.toRadians(180));

        double lastTime = GLFW.glfwGetTime();
        float currentFrame = 0f;

        while (!GLFW.glfwWindowShouldClose(window)) {
            double now = GLFW.glfwGetTime();
            double dt = Math.min(now - lastTime, 0.1);
            lastTime = now;

            if (isAnimating) {
                currentFrame += (float) (dt * 30.0);
                if (currentFrame > motion.maxFrame) {
                    currentFrame = 0f;
                    physicsReset = true; // animation nhảy về đầu -> reset physics
                }
            }

            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(window, xpos, ypos);

            if (isFirstMouse[0]) {
                lastX[0] = xpos[0];
                lastY[0] = ypos[0];
                isFirstMouse[0] = false;
            }

            float xOffset = (float) (xpos[0] - lastX[0]);
            float yOffset = (float) (lastY[0] - ypos[0]);
            lastX[0] = xpos[0];
            lastY[0] = ypos[0];

            if (leftPressed[0]) camera.rotate(xOffset, -yOffset);

            GL30.glClearColor(0.3f, 0.3f, 0.3f, 1.0f);
            GL30.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

            if (isAnimating) {
                animator.setPhysicsEnabled(physicsOn);
                if (physicsReset) {
                    animator.resetPhysics();
                    physicsReset = false;
                }

                morphs.evaluate(currentFrame);
                animator.update(currentFrame, (float) dt);
                Matrix4f[] skinning = animator.getSkinningMatrices();
                for (int i = 0; i < boneCount; i++) boneMatrices[i].set(skinning[i]);
                if (morphs.vertexDirty) mesh.updatePositions(morphs.vertexOffsets);
            } else {
                for (int i = 0; i < boneCount; i++) boneMatrices[i].identity();
            }

            Matrix4f view = camera.getViewMatrix();

            if (showGrid) grid.render(projection, view);

            boneTex.update(boneMatrices);

            shader.use();
            shader.setMat4("projection", projection);
            shader.setMat4("view", view);
            shader.setMat4("model", modelMat);
            shader.setVec3("lightPos", 10f, 20f, 10f);

            boneTex.bind(1);
            shader.setInt("uBoneTex", 1);

            ModelRenderer.render(mesh, shader, modelDir, morphs.materialDiffuse);

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }

        grid.dispose();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}