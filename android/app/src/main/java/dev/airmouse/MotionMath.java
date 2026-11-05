package dev.airmouse;

/** Relative, calibrated orientation. No magnetic compass is required. */
public final class MotionMath {
    private double[] neutral;
    private boolean first = true;
    private double lastTime;
    private final Axis horizontalAxis = new Axis();
    private final Axis verticalAxis = new Axis();

    public void reset() { neutral = null; first = true; lastTime = 0; }

    public double[] update(float[] q, double seconds, boolean turnMode, double gain) {
        if (q == null || q.length < 4 || !Double.isFinite(seconds) ||
                !Double.isFinite(gain) || gain <= 0) return new double[]{0, 0};
        for (float value : q) if (!Float.isFinite(value)) return new double[]{0, 0};
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
            horizontalAxis.reset(horizontal);
            verticalAxis.reset(vertical);
            first = false;
            lastTime = seconds;
            return new double[]{0, 0};
        }
        double dt = seconds-lastTime;
        lastTime = seconds;
        // Discard implausible one-frame jumps before they enter the filters.
        if (Math.abs(wrap(horizontal-horizontalAxis.raw)) > .35 ||
                Math.abs(wrap(vertical-verticalAxis.raw)) > .35) {
            horizontalAxis.reset(horizontal);
            verticalAxis.reset(vertical);
            return new double[]{0, 0};
        }
        double dx = horizontalAxis.update(horizontal, dt)*gain;
        double dy = verticalAxis.update(vertical, dt)*gain;
        if (Math.abs(dx) > 150 || Math.abs(dy) > 150) {
            horizontalAxis.reset(horizontal);
            verticalAxis.reset(vertical);
            return new double[]{0, 0};
        }
        return new double[]{dx, dy};
    }

    private static final class Axis {
        // Strong smoothing at rest, with a wider bandwidth during a sweep.
        // Each axis adapts independently so horizontal motion cannot unlock vertical tremor.
        private static final double MIN_CUTOFF_HZ = 1.5, SPEED_WEIGHT = 10;
        private static final double SPEED_CUTOFF_HZ = 1.0;
        private static final double HOLD_RADIUS = .00065; // ~0.037 degrees
        private double raw, filtered, emitted, speed;

        void reset(double angle) { raw = filtered = emitted = angle; speed = 0; }

        double update(double angle, double dt) {
            double velocity = wrap(angle-raw)/dt;
            raw = angle;
            speed += alpha(dt, SPEED_CUTOFF_HZ)*(velocity-speed);
            double cutoff = MIN_CUTOFF_HZ + SPEED_WEIGHT*Math.abs(speed);
            filtered = wrap(filtered + alpha(dt, cutoff)*wrap(angle-filtered));
            double distance = wrap(filtered-emitted);
            // Hysteresis is relative to the last emitted position, not the last
            // sample: slow intentional motion still accumulates and gets through.
            if (Math.abs(distance) <= HOLD_RADIUS) return 0;
            double delta = distance - Math.copySign(HOLD_RADIUS, distance);
            emitted = wrap(emitted + delta);
            return delta;
        }

        private static double alpha(double dt, double cutoff) {
            return 1-Math.exp(-2*Math.PI*cutoff*dt);
        }
    }

    static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
