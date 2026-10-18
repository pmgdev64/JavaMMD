package vn.pmgteam.jmmd.data;

public class RigidBody {
    public String name = "";
    public String nameEn = "";
    public int boneIndex = -1;
    public int group = 0;
    public int collisionMask = 0;
    
    public int shapeType = 0; // 0: Sphere, 1: Box, 2: Capsule
    public float width = 0f;
    public float height = 0f;
    public float depth = 0f;
    
    public float[] position = new float[3];
    public float[] rotation = new float[3]; // Radians
    
    public float mass = 0f;
    public float linearDamping = 0f;
    public float angularDamping = 0f;
    public float restitution = 0f;
    public float friction = 0f;
    public int physicsMode = 0; // 0: Follow Bone, 1: Physics, 2: Physics + Bone
}