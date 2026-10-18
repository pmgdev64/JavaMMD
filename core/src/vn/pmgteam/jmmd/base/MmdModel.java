package vn.pmgteam.jmmd.base;

import java.util.ArrayList;
import java.util.List;

import vn.pmgteam.jmmd.data.Bone;
import vn.pmgteam.jmmd.data.DisplayFrame;
import vn.pmgteam.jmmd.data.Joint;
import vn.pmgteam.jmmd.data.Material;
import vn.pmgteam.jmmd.data.Morph;
import vn.pmgteam.jmmd.data.RigidBody;
import vn.pmgteam.jmmd.data.Vertex;

public class MmdModel {

    // ===== Header =====
    public float version;          
    public int extraUvCount;       
    public int vertexIndexSize;    
    public int textureIndexSize;   
    public int materialIndexSize;  
    public int boneIndexSize;      
    public int morphIndexSize;     
    public int rigidBodyIndexSize; 

    // ===== Metadata =====
    public String name      = "";
    public String nameEn    = "";
    public String comment   = "";
    public String commentEn = "";

    // ===== Geometry =====
    public final List<Vertex> vertices = new ArrayList<>();
    public int[] indices = new int[0];

    // ===== Materials & textures =====
    public final List<String>   textures  = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();

    // ===== Skeleton / animation =====
    public final List<Bone>  bones  = new ArrayList<>();
    public final List<Morph> morphs = new ArrayList<>();
    
    // ĐÃ THÊM DÒNG NÀY ĐỂ FIX LỖI
    public final List<DisplayFrame> displayFrames = new ArrayList<>();

    // ===== Physics =====
    public final List<RigidBody> rigidBodies = new ArrayList<>();
    public final List<Joint>     joints      = new ArrayList<>();

    @Override
    public String toString() {
        return String.format(
            "MmdModel[v%.1f, '%s', %d verts, %d faces, %d mats, %d bones, %d morphs, %d frames]",
            version, name, vertices.size(), indices.length / 3,
            materials.size(), bones.size(), morphs.size(), displayFrames.size());
    }
}