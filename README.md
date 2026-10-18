# JavaMMD

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![LWJGL](https://img.shields.io/badge/LWJGL-3.3%2B-blue.svg)](https://www.lwjgl.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](#license)

**JavaMMD** is an open-source, pure Java library designed to **load, animate, render, and simulate physics** for MikuMikuDance (MMD) models. 

The library supports **PMX/PMD** (model) and **VMD** (motion) formats, features built-in audio playback (MP3/WAV) synchronized with the animation, and uses a custom **XPBD** physics engine (with zero dependencies on Bullet or native physics libraries).

---

## ✨ Key Features

### 🎨 Rendering (LWJGL 3 / OpenGL 3.3+)
*   **Toon Shading & Sphere Maps**: Full support for MMD-style toon ramps (from `.bmp` files) and sphere maps (matcaps) with Multiply/Add modes.
*   **2-Sided Cloth**: Handles double-sided rendering (`gl_FrontFacing`) for skirts, hair, and capes.
*   **Outline Pass (Inverted Hull)**: Material-colored outline rendering for that classic anime aesthetic.
*   **Specular & Rim Light**: Blinn-Phong specular highlights (using PMX material parameters) and rim lighting calculated using the actual camera position (`viewPos`).
*   **Skinning**: Full support for BDEF1, BDEF2, BDEF4, SDEF, and QDEF.

### ⚙️ Physics (Pure Java XPBD)
*   **No Bullet Required**: Built from scratch using the XPBD algorithm (Müller et al. 2020) in pure Java (only requires JOML). No native physics libraries needed.
*   **6DOF Joints**: Supports position/rotation limits and spring constraints (spring position/rotation).
*   **Collision System**: Sphere, Capsule, and Box collisions (for both Kinematic & Dynamic bodies), ground collisions, and Dynamic-Dynamic collisions.
*   **Rest Slack (Anti-Jitter)**: Automatically calculates the initial penetration depth of overlapping bodies at the bind pose to prevent solver jitter.
*   **Mode 2 Support**: Physics simulation while maintaining bone positions (typically used for front hair/bangs or accessories).

### 🎬 Animation & Audio
*   **VMD Interpolation**: Accurate Bezier curve interpolation for Bone, Morph, and Camera keyframes.
*   **IK (CCD)**: Full Inverse Kinematics support with joint limits and on/off toggles.
*   **Append/Inherit**: Supports bone rotation and translation inheritance.
*   **Audio Sync**: Decodes MP3 (JLayer) or WAV (javax.sound) into PCM, plays on a dedicated thread, and synchronizes the animation frame rate to the audio.
*   **VMD Camera**: Full support for VMD camera tracks (FOV, distance, rotation).

### 🖥️ UI & Utilities
*   **MediaPlayerBar**: Built-in UI overlay with a progress bar, Play/Pause, Seek, Playback Speed, and Volume indicators.
*   **Debug Overlay (F3)**: Real-time FPS, model statistics, physics status, and CPU frame times.
*   **STB TrueType**: 2D text rendering using standard `.ttf` fonts.

---

## 🛠️ System Requirements

*   **Java 17** or higher (Java 17/21 LTS recommended).
*   **GPU** supporting OpenGL 3.3 Core Profile.
*   **OS**: Windows, Linux, macOS (via LWJGL natives).

---

## 🚀 Installation & Usage

### 1. Add Dependencies

*The project is preparing for Maven Central publication. For now, you can clone the repo and build it locally.*

```gradle
// build.gradle.kts
dependencies {
    // Core library
    implementation("io.github.pmgdev64:javamdd:0.1.0") // Coming soon to Maven Central
    
    // LWJGL 3 (Required)
    implementation("org.lwjgl:lwjgl:3.3.3")
    implementation("org.lwjgl:lwjgl-opengl:3.3.3")
    implementation("org.lwjgl:lwjgl-glfw:3.3.3")
    implementation("org.lwjgl:lwjgl-stb:3.3.3")
    
    // LWJGL Natives (Change OS classifier accordingly)
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-windows")
    
    // Math
    implementation("org.joml:joml:1.10.5")
}
```

### 2. Running the Demo

Clone the project and prepare your assets:
1. Place your PMX model at: `src/resources/test/models.pmx`
2. Place your VMD animation at: `src/resources/test/animations/anim.vmd`
3. Place your audio file (optional) at: `src/resources/test/audio/`

Run the `vn.pmgteam.jmmd.main.Main` class.

### 🎮 Keyboard Controls

| Key | Action |
| :--- | :--- |
| `Space` | Play / Pause |
| `Left` / `Right` | Seek backward / forward 1 second (Hold `Shift` to seek 1 frame) |
| `[` / `]` | Decrease / Increase playback speed |
| `Up` / `Down` | Volume Up / Down |
| `M` | Toggle Mute |
| `,` / `.` | Adjust Audio offset (sync adjustment) |
| `A` | Toggle Animation |
| `P` | Toggle Physics |
| `G` | Toggle Grid |
| `C` | Toggle Camera (Free / VMD) |
| `R` | Reset Free Camera |
| `F3` | Toggle Debug Overlay |

---

## 📚 Basic API

### Loading Models and Motions
```java
import vn.pmgteam.jmmd.base.MmdModel;
import vn.pmgteam.jmmd.io.PmxReader;
import vn.pmgteam.jmmd.io.VmdReader;
import vn.pmgteam.jmmd.animation.VmdMotion;

MmdModel model = PmxReader.read(Path.of("model.pmx"));
VmdMotion motion = VmdReader.read(Path.of("anim.vmd"));
```

### Initializing Physics
```java
import vn.pmgteam.jmmd.physics.MmdPhysics;

MmdPhysics physics = new MmdPhysics(model);
physics.gravity = 98f; // MMD Standard Gravity (9.8 * 10)
physics.maxSubstep = 1f / 240f; // Stability tuning
// (Optional) Fine-tuning
physics.jointDamping = 8f; 
```

### Initializing Render and Animator
```java
ModelMesh mesh = new ModelMesh(model);
Animator animator = new Animator(model, motion);
MorphAnimator morphs = new MorphAnimator(model, motion);

animator.setMorphAnimator(morphs);
animator.setPhysics(physics);
```

### Render Loop (Pseudo-code)
```java
// Inside your main loop
float currentFrame = 0; // Update from your timeline
float dt = 1f / 60f;

morphs.evaluate(currentFrame);
animator.update(currentFrame, dt);

Matrix4f[] boneMatrices = animator.getSkinningMatrices();
boneTexture.update(boneMatrices);

// Render...
ModelRenderer.render(mesh, shader, modelDir, morphs.materialDiffuse);
```

---

## 📂 Project Structure

```text
vn.pmgteam.jmmd
├── animation/      # Animator, MorphAnimator, VMD keyframes, IK, Camera
├── audio/          # AudioPlayer (JLayer MP3 / javax.sound WAV)
├── base/           # MmdModel, MmdReader interface
├── data/           # POJOs: Vertex, Bone, Material, RigidBody, Joint...
├── io/             # PmxReader, VmdReader (ByteBuffer parsing)
├── main/           # Entry point (Main.java) containing the render loop
├── physics/        # MmdPhysics (XPBD solver, collision, joints)
├── render/         # LWJGL wrappers: Shader, Texture, Mesh, BoneTexture
└── ui/             # UiRenderer (STB), MediaPlayerBar
```

---

## ⚠️ Technical Notes

1. **MMD Coordinate System**: MMD uses a Left-handed coordinate system (Z is forward), while OpenGL uses Right-handed. In `Main.java`, the model is rotated 180 degrees (`rotateY(Math.PI)`) to match.
2. **Rigid Body Modes**:
   *   `Mode 0`: Kinematic (follows the bone).
   *   `Mode 1`: Dynamic (pure physics).
   *   `Mode 2`: Dynamic, but position is overridden by the bone (commonly used for bangs/front hair).
3. **Textures**: The library uses `STBImage` and automatically flips them vertically (`stbi_set_flip_vertically_on_load(true)`) because MMD saves textures with a bottom-left origin.
4. **Maven Central Ready**: The project strictly avoids bundling native binaries (except instructing the user to add LWJGL natives), ensuring the artifact is cross-platform (Windows, Linux, macOS) and won't conflict with modding environments like Minecraft.

---

## 🤝 Contributing

Contributions (Pull Requests, Issues) are highly welcome. 
If you encounter physics bugs (jittering, clipping), please include the model name and the `.vmd` file to help us reproduce the issue.

---

## 📄 License

This project is distributed under the **MIT License**. 
*(Note: MMD models (PMX) and motions (VMD) usually have their own Terms of Use. Please read the `readme.txt` provided with each model).*

---
*Developed by [pmgdev64](https://github.com/pmgdev64) and contributors.*
*Powered by LWJGL 3 & JOML.*
