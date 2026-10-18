package vn.pmgteam.jmmd.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    public Vector3f target = new Vector3f(0.0f, 10.0f, 0.0f); 
    public float distance = 35.0f; 
    public float yaw = 0.0f; 
    public float pitch = 0.0f; 
    
    public Vector3f position = new Vector3f();

    public Camera() {
        updatePosition();
    }

    public void reset() {
        target.set(0.0f, 10.0f, 0.0f);
        distance = 35.0f;
        yaw = 0.0f;
        pitch = 0.0f;
        updatePosition();
    }

    public void updatePosition() {
        float x = (float) (distance * Math.cos(pitch) * Math.sin(yaw));
        float y = (float) (distance * Math.sin(pitch));
        float z = (float) (distance * Math.cos(pitch) * Math.cos(yaw));
        position.set(target.x + x, target.y + y, target.z + z);
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, target, new Vector3f(0.0f, 1.0f, 0.0f));
    }

    public void rotate(float xOffset, float yOffset) {
        yaw -= xOffset * 0.005f;
        pitch += yOffset * 0.005f;
        
        float maxPitch = (float) Math.toRadians(89.0);
        if (pitch > maxPitch) pitch = maxPitch;
        if (pitch < -maxPitch) pitch = -maxPitch;
        
        updatePosition();
    }

    public void zoom(float yOffset) {
        distance -= yOffset * 2.0f;
        if (distance < 5.0f) distance = 5.0f; 
        if (distance > 200.0f) distance = 200.0f;
        updatePosition();
    }

    // Hàm mới: Cập nhật điểm nhìn vào trung tâm model
    public void setTarget(Vector3f newTarget) {
        this.target.set(newTarget);
        updatePosition();
    }
}