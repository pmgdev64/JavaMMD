package vn.pmgteam.jmmd.data;

public class Joint {
    public String name = "";
    public String nameEn = "";
    public int rigidBodyAIndex = -1;
    public int rigidBodyBIndex = -1;
    
    public float[] position = new float[3];
    public float[] rotation = new float[3];
    
    public float[] positionLimitMin = new float[3];
    public float[] positionLimitMax = new float[3];
    public float[] rotationLimitMin = new float[3];
    public float[] rotationLimitMax = new float[3];
    public float[] springPosition = new float[3];
    public float[] springRotation = new float[3];
}