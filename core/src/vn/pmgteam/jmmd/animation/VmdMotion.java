package vn.pmgteam.jmmd.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class VmdMotion {
    /** Tên xương -> keyframe theo thời gian */
    public Map<String, TreeMap<Integer, BoneKeyframe>> boneTracks = new HashMap<>();
    /** Tên morph -> (frame -> weight) */
    public Map<String, TreeMap<Integer, Float>> morphTracks = new HashMap<>();
    /** Tên IK bone -> (frame -> bật/tắt) */
    public Map<String, TreeMap<Integer, Boolean>> ikTracks = new HashMap<>();
    public int maxFrame = 0;
}