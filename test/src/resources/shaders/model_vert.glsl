#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aTexCoord;
layout (location = 3) in vec4 aBoneIndices;
layout (location = 4) in vec4 aBoneWeights;
layout (location = 5) in vec4 aSdefC;   // xyz = C, w = 1 nếu là SDEF
layout (location = 6) in vec3 aSdefR0;
layout (location = 7) in vec3 aSdefR1;

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoord;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

// Ma trận xương lưu trong texture RGBA32F rộng 256 texel, mỗi ma trận = 4 texel liền nhau
uniform sampler2D uBoneTex;

uniform bool isOutlinePass;
uniform float outlineThickness;

mat4 getBone(int i) {
    int base = i * 4;
    ivec2 p = ivec2(base % 256, base / 256);
    return mat4(
        texelFetch(uBoneTex, p, 0),
        texelFetch(uBoneTex, p + ivec2(1, 0), 0),
        texelFetch(uBoneTex, p + ivec2(2, 0), 0),
        texelFetch(uBoneTex, p + ivec2(3, 0), 0));
}

vec4 matToQuat(mat3 m) {
    float tr = m[0][0] + m[1][1] + m[2][2];
    vec4 q;
    if (tr > 0.0) {
        float s = sqrt(tr + 1.0) * 2.0;
        q = vec4((m[1][2] - m[2][1]) / s, (m[2][0] - m[0][2]) / s, (m[0][1] - m[1][0]) / s, 0.25 * s);
    } else if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
        float s = sqrt(1.0 + m[0][0] - m[1][1] - m[2][2]) * 2.0;
        q = vec4(0.25 * s, (m[1][0] + m[0][1]) / s, (m[2][0] + m[0][2]) / s, (m[1][2] - m[2][1]) / s);
    } else if (m[1][1] > m[2][2]) {
        float s = sqrt(1.0 + m[1][1] - m[0][0] - m[2][2]) * 2.0;
        q = vec4((m[1][0] + m[0][1]) / s, 0.25 * s, (m[2][1] + m[1][2]) / s, (m[2][0] - m[0][2]) / s);
    } else {
        float s = sqrt(1.0 + m[2][2] - m[0][0] - m[1][1]) * 2.0;
        q = vec4((m[2][0] + m[0][2]) / s, (m[2][1] + m[1][2]) / s, 0.25 * s, (m[0][1] - m[1][0]) / s);
    }
    return normalize(q);
}

mat3 quatToMat(vec4 q) {
    float x = q.x, y = q.y, z = q.z, w = q.w;
    return mat3(
        1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y + z * w),       2.0 * (x * z - y * w),
        2.0 * (x * y - z * w),       1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z + x * w),
        2.0 * (x * z + y * w),       2.0 * (y * z - x * w),       1.0 - 2.0 * (x * x + y * y));
}

void main() {
    int maxBones = textureSize(uBoneTex, 0).y * 64; // 256 texel / 4 = 64 ma trận mỗi hàng

    vec3 skinnedPos = aPos;
    vec3 skinnedNormal = aNormal;

    int i0 = int(aBoneIndices.x + 0.5);
    int i1 = int(aBoneIndices.y + 0.5);

    if (aSdefC.w > 0.5 && i0 < maxBones && i1 < maxBones) {
        // ===== SDEF =====
        float w0 = aBoneWeights.x;
        float w1 = 1.0 - w0;
        mat4 m0 = getBone(i0);
        mat4 m1 = getBone(i1);

        vec3 c = aSdefC.xyz;
        vec3 r0 = aSdefR0;
        vec3 r1 = aSdefR1;
        vec3 rw = r0 * w0 + r1 * w1;
        r0 = c + r0 - rw;
        r1 = c + r1 - rw;
        vec3 cr0 = (c + r0) * 0.5;
        vec3 cr1 = (c + r1) * 0.5;

        vec4 q0 = matToQuat(mat3(m0));
        vec4 q1 = matToQuat(mat3(m1));
        if (dot(q0, q1) < 0.0) q1 = -q1;
        mat3 rot = quatToMat(normalize(q0 * w0 + q1 * w1));

        skinnedPos = rot * (aPos - c) + (m0 * vec4(cr0, 1.0)).xyz * w0 + (m1 * vec4(cr1, 1.0)).xyz * w1;
        skinnedNormal = rot * aNormal;
    } else {
        // ===== BDEF1/2/4 (QDEF xử lý như BDEF4) =====
        mat4 boneTransform = mat4(0.0);
        float totalWeight = 0.0;

        for (int i = 0; i < 4; i++) {
            int boneIndex = int(aBoneIndices[i] + 0.5);
            float weight = aBoneWeights[i];

            if (weight > 0.0 && boneIndex >= 0 && boneIndex < maxBones) {
                boneTransform += getBone(boneIndex) * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight > 0.001) {
            boneTransform = boneTransform / totalWeight;
        } else {
            boneTransform = mat4(1.0);
        }

        skinnedPos = (boneTransform * vec4(aPos, 1.0)).xyz;
        skinnedNormal = mat3(boneTransform) * aNormal;
    }

    // Chống lỗi NaN và Infinity
    if (any(isnan(skinnedPos)) || any(isinf(skinnedPos))) {
        skinnedPos = aPos;
        skinnedNormal = aNormal;
    }

    if (isOutlinePass) {
        skinnedPos += skinnedNormal * outlineThickness;
    }

    vec4 worldPos = model * vec4(skinnedPos, 1.0);
    FragPos = worldPos.xyz;
    Normal = mat3(transpose(inverse(model))) * skinnedNormal;
    TexCoord = aTexCoord;

    gl_Position = projection * view * worldPos;
}