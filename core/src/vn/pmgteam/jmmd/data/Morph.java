package vn.pmgteam.jmmd.data;

import java.util.ArrayList;
import java.util.List;

public class Morph {
    public String name = "";
    public String nameEn = "";
    public int panel = 0; 
    public int morphType = 0; 
    
    // Chứa data thô tuỳ theo morphType
    public List<MorphOffset> offsets = new ArrayList<>();

    public static class MorphOffset {
        public int index = -1;
        
        // Dùng cho Group, Flip
        public float ratio = 0f;

        // Dùng cho Vertex
        public float[] vertexOffset = new float[3];

        // Dùng cho Bone
        public float[] boneTranslation = new float[3];
        public float[] boneRotation = new float[4]; // Quaternion

        // Dùng cho UV (0-3)
        public float[] uvOffset = new float[4];

        // Dùng cho Material
        public int matOperation = 0;
        public float[] matDiffuse = new float[4];
        public float[] matSpecular = new float[3];
        public float matSpecularity = 0f;
        public float[] matAmbient = new float[3];
        public float[] matEdgeColor = new float[4];
        public float matEdgeSize = 0f;
        public float[] matTextureTint = new float[4];
        public float[] matSphereTint = new float[4];
        public float[] matToonTint = new float[4];

        // Dùng cho Impulse (Vật lý)
        public int rbIndex = -1;
        public int localFlag = 0;
        public float[] rbVelocity = new float[3];
        public float[] rbTorque = new float[3];
    }
}