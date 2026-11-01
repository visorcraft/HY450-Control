package com.visorcraft.hy450control;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Keystone that makes the picture a rectangle on the wall.
 *
 * The built-in camera looks along the beam, so a rectangle in that camera is
 * still a trapezoid on the wall. These insets were measured on the wall for
 * this ceiling mount at REFERENCE_PITCH degrees: the far edge is the bottom,
 * the bottom right sits at 105, and the bottom left sits at 80. Other tilts
 * scale from that measurement. Order is ltx, lty, rtx, rty, lbx, lby, rbx, rby.
 */
final class WallRectangle {
    static final float REFERENCE_PITCH = 10.545f;
    private static final int FAR_LEFT_X = 80;
    private static final int FAR_LEFT_Y = 40;
    private static final int FAR_RIGHT_X = 105;
    private static final int FAR_RIGHT_Y = 40;
    private static final String TILT = "/sys/devices/platform/prj343x/xyz_data";

    static final class Pose {
        final float pitch;
        final boolean ceiling;

        Pose(float pitch, boolean ceiling) {
            this.pitch = pitch;
            this.ceiling = ceiling;
        }
    }

    private WallRectangle() {}

    static Pose pose() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(TILT));
        try {
            String line = reader.readLine();
            if (line == null) throw new IllegalStateException("Tilt sensor returned nothing");
            String[] part = line.trim().split(",");
            if (part.length < 3) throw new IllegalStateException("Tilt sensor returned " + line);
            int y = Integer.parseInt(part[1].trim());
            int z = Integer.parseInt(part[2].trim());
            float pitch = (float) Math.toDegrees(Math.atan2(Math.abs(y), Math.abs(z)));
            return new Pose(pitch, z < 0);
        } finally {
            reader.close();
        }
    }

    static int[] insets(Pose pose) {
        float scale = pose.pitch / REFERENCE_PITCH;
        if (!(scale > 0f)) scale = 0f;
        // The sensor jitters by a fraction of a degree. Keep the measured corners.
        if (Math.abs(pose.pitch - REFERENCE_PITCH) <= 0.6f) scale = 1f;
        int leftX = Math.round(FAR_LEFT_X * scale);
        int leftY = Math.round(FAR_LEFT_Y * scale);
        int rightX = Math.round(FAR_RIGHT_X * scale);
        int rightY = Math.round(FAR_RIGHT_Y * scale);
        if (pose.ceiling) return new int[] {0, 0, 0, 0, leftX, leftY, rightX, rightY};
        return new int[] {leftX, leftY, rightX, rightY, 0, 0, 0, 0};
    }
}
