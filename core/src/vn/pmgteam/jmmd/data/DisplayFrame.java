package vn.pmgteam.jmmd.data;

import java.util.ArrayList;
import java.util.List;

public class DisplayFrame {
    public String name = "";
    public String nameEn = "";
    public int specialFlag = 0; // 0: Normal, 1: Special (Khuôn mặt, tay)
    
    public List<FrameElement> elements = new ArrayList<>();

    public static class FrameElement {
        public int type = 0; // 0 = Bone, 1 = Morph
        public int index = -1;
    }
}