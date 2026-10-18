package vn.pmgteam.jmmd.data;

public class Material {
    public String name   = "";
    public String nameEn = "";

    public float[] diffuse  = {1, 1, 1, 1}; // RGBA
    public float[] specular = {0, 0, 0};    // RGB
    public float   specularPower = 1f;
    public float[] ambient  = {0, 0, 0};    // RGB

    /** PMX material flags: 0x01 no-cull, 0x10 draw-edge, ... */
    public int     flags = 0;

    public float[] edgeColor = {0, 0, 0, 1};
    public float   edgeSize  = 1f;

    public int textureIndex    = -1;  // vào MmdModel.textures
    public int sphereTextureIndex = -1;
    public int sphereMode      = 0;   // 0=none, 1=mult, 2=add, 3=sub
    public int toonIndex       = -1;  // có thể trỏ vào textures (PMX 2.0+) hoặc toon built-in
    public String memo = "";

    /** Số face (mỗi face = 3 index) mà material này dùng. */
    public int faceCount = 0;
}