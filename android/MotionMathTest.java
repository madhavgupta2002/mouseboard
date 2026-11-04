package dev.airmouse;

public final class MotionMathTest {
    private static float[] x(double angle) { return new float[]{(float)Math.cos(angle/2), (float)Math.sin(angle/2), 0, 0}; }
    private static float[] y(double angle) { return new float[]{(float)Math.cos(angle/2), 0, (float)Math.sin(angle/2), 0}; }
    private static float[] z(double angle) { return new float[]{(float)Math.cos(angle/2), 0, 0, (float)Math.sin(angle/2)}; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        MotionMath m = new MotionMath();
        require(m.update(x(0), 1, false, 1000)[1] == 0, "First sample must not jump");
        double up = m.update(x(0.1), 1.016, false, 1000)[1];
        require(up < 0, "Lifting the top of the phone moves pointer up");
        m.reset(); m.update(y(0), 1, false, 1000);
        require(m.update(y(0.1), 1.016, false, 1000)[0] > 0, "Tilting right moves pointer right");
        m.reset(); m.update(z(0), 1, true, 1000);
        require(m.update(z(0.1), 1.016, true, 1000)[0] < 0, "Turning left moves pointer left");
        m.reset(); m.update(x(0.8), 1, false, 1000);
        require(Math.abs(m.update(x(0.8), 1.016, false, 1000)[1]) < 1e-6, "Calibration must accept arbitrary neutral orientation");
        require(m.update(x(1), 2, false, 1000)[1] == 0, "Long sensor gaps must not jump");
        m.reset(); m.update(z(Math.PI-0.01), 1, true, 1000);
        require(Math.abs(m.update(z(-Math.PI+0.01), 1.016, true, 1000)[0]) < 20, "Angle wrap must not jump");
        require(Math.abs(MotionMath.wrap(2*Math.PI + 0.2)-0.2) < 1e-9, "Angle wrap");
        m.reset(); m.update(x(0), 1, false, 1000);
        for (int n = 1; n <= 120; n++) m.update(x(0.1), 1+n/60.0, false, 1000);
        require(Math.abs(m.update(x(0.1), 3.02, false, 1000)[1]) < 0.001, "Stationary phone settles to zero movement");
        System.out.println("PASS: 8 motion and calibration checks");
    }
}
