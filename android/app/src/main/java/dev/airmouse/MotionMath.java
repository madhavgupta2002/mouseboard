package dev.airmouse;

/** Relative, calibrated orientation. No magnetic compass is required. */
public final class MotionMath {
    private double[] neutral;
    private double previousX, previousY;
    private boolean first = true;
    private double smoothX, smoothY;
    private double lastTime;

    public void reset() { neutral = null; first = true; smoothX = smoothY = 0; lastTime = 0; }

    public double[] update(float[] q, double seconds, boolean turnMode, double gain) {
        // SensorManager.getQuaternionFromVector supplies [w, x, y, z].
        if (neutral == null) {
            neutral = new double[]{q[0], q[1], q[2], q[3]};
        }
        double aw = neutral[0], ax = -neutral[1], ay = -neutral[2], az = -neutral[3];
        double w = aw*q[0] - ax*q[1] - ay*q[2] - az*q[3];
        double x = aw*q[1] + ax*q[0] + ay*q[3] - az*q[2];
        double y = aw*q[2] - ax*q[3] + ay*q[0] + az*q[1];
        double z = aw*q[3] + ax*q[2] - ay*q[1] + az*q[0];
        double pitch = Math.atan2(2*(w*x+y*z), 1-2*(x*x+y*y));
        double horizontal = turnMode
            ? -Math.atan2(2*(w*z+x*y), 1-2*(y*y+z*z))
            : Math.asin(Math.max(-1, Math.min(1, 2*(w*y-z*x))));
        double vertical = -pitch;
        if (first || seconds-lastTime > 0.2 || seconds <= lastTime) {
            previousX = smoothX = horizontal;
            previousY = smoothY = vertical;
            first = false;
            lastTime = seconds;
            return new double[]{0, 0};
        }
        double dt = Math.min(0.05, seconds-lastTime);
        lastTime = seconds;
        // Time-based low-pass filtering keeps the feel consistent at 60/120 Hz.
        double alpha = 1 - Math.exp(-dt / 0.028);
        smoothX += wrap(horizontal-smoothX)*alpha;
        smoothY += wrap(vertical-smoothY)*alpha;
        double dx = wrap(smoothX-previousX)*gain;
        double dy = wrap(smoothY-previousY)*gain;
        previousX = smoothX;
        previousY = smoothY;
        // Discard sensor discontinuities; never teleport on resume/recalibration.
        if (Math.abs(dx) > 150 || Math.abs(dy) > 150) return new double[]{0, 0};
        return new double[]{dx, dy};
    }

    static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
