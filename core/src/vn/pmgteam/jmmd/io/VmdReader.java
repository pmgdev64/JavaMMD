package vn.pmgteam.jmmd.io;

import vn.pmgteam.jmmd.animation.BoneKeyframe;
import vn.pmgteam.jmmd.animation.VmdMotion;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.TreeMap;

public class VmdReader {

    public static VmdMotion read(Path path) throws IOException {
        VmdMotion motion = new VmdMotion();
        Charset sjis = Charset.forName("Shift_JIS");

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buf);
            buf.flip();

            // Header (30 bytes) + Model Name (20 bytes)
            buf.position(buf.position() + 50);

            // ===== Bone keyframes =====
            int boneKeyframeCount = buf.getInt();
            for (int i = 0; i < boneKeyframeCount; i++) {
                String boneName = readName(buf, 15, sjis);
                int time = buf.getInt();

                BoneKeyframe kf = new BoneKeyframe();
                kf.time = time;
                kf.position.x = buf.getFloat();
                kf.position.y = buf.getFloat();
                kf.position.z = buf.getFloat();

                kf.rotation.x = buf.getFloat();
                kf.rotation.y = buf.getFloat();
                kf.rotation.z = buf.getFloat();
                kf.rotation.w = buf.getFloat();

                buf.get(kf.interp); // 64 byte Bezier

                motion.boneTracks.computeIfAbsent(boneName, k -> new TreeMap<>()).put(time, kf);
                if (time > motion.maxFrame) motion.maxFrame = time;
            }

            // ===== Morph keyframes (15 + 4 + 4 byte) =====
            if (buf.remaining() >= 4) {
                int morphCount = buf.getInt();
                for (int i = 0; i < morphCount; i++) {
                    if (buf.remaining() < 23) break;
                    String name = readName(buf, 15, sjis);
                    int frame = buf.getInt();
                    float weight = buf.getFloat();
                    motion.morphTracks.computeIfAbsent(name, k -> new TreeMap<>()).put(frame, weight);
                    if (frame > motion.maxFrame) motion.maxFrame = frame;
                }
            }

            // ===== Camera (61), Light (28), Self shadow (9): bỏ qua =====
            skipSection(buf, 61);
            skipSection(buf, 28);
            skipSection(buf, 9);

            // ===== IK on/off =====
            if (buf.remaining() >= 4) {
                int count = buf.getInt();
                for (int i = 0; i < count; i++) {
                    if (buf.remaining() < 9) break;
                    int frame = buf.getInt();
                    buf.get(); // show flag
                    int ikCount = buf.getInt();
                    for (int j = 0; j < ikCount; j++) {
                        if (buf.remaining() < 21) break;
                        String name = readName(buf, 20, sjis);
                        boolean on = buf.get() != 0;
                        motion.ikTracks.computeIfAbsent(name, k -> new TreeMap<>()).put(frame, on);
                    }
                }
            }
        }

        System.out.println("[VMD] Bone tracks: " + motion.boneTracks.size()
                + ", morph tracks: " + motion.morphTracks.size()
                + ", IK tracks: " + motion.ikTracks.size()
                + ", maxFrame: " + motion.maxFrame);
        return motion;
    }

    private static String readName(ByteBuffer buf, int size, Charset cs) {
        byte[] b = new byte[size];
        buf.get(b);
        int len = 0;
        while (len < size && b[len] != 0) len++; // phần sau byte 0 là padding rác
        return new String(b, 0, len, cs);
    }

    private static void skipSection(ByteBuffer buf, int recordSize) {
        if (buf.remaining() < 4) return;
        int count = buf.getInt();
        if (count < 0) return;
        long target = (long) buf.position() + (long) count * recordSize;
        buf.position((int) Math.min(buf.limit(), target));
    }
}