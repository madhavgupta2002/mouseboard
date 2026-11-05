package dev.airmouse;

public final class MotionMathTest {
    private static int checks;
    private static float[] x(double angle) { return new float[]{(float)Math.cos(angle/2), (float)Math.sin(angle/2), 0, 0}; }
    private static float[] y(double angle) { return new float[]{(float)Math.cos(angle/2), 0, (float)Math.sin(angle/2), 0}; }
    private static float[] z(double angle) { return new float[]{(float)Math.cos(angle/2), 0, 0, (float)Math.sin(angle/2)}; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static float[] pitchAndTurn(double pitch, double yaw) {
        double cp = Math.cos(pitch/2), sp = Math.sin(pitch/2);
        double cy = Math.cos(yaw/2), sy = Math.sin(yaw/2);
        return new float[]{(float)(cy*cp), (float)(cy*sp), (float)(sy*sp), (float)(sy*cp)};
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
        m.reset(); m.update(pitchAndTurn(0, 0), 1, true, 1000);
        double[] diagonal = m.update(pitchAndTurn(.05, .08), 1.016, true, 1000);
        require(diagonal[0] < -.1 && diagonal[1] < -.1,
            "Default turning mode preserves both horizontal and vertical movement");
        m.reset(); m.update(pitchAndTurn(0, 0), 1, true, 1000);
        double[] opposite = m.update(pitchAndTurn(-.05, -.08), 1.016, true, 1000);
        require(opposite[0] > .1 && opposite[1] > .1,
            "Turning and pitching in the opposite direction reverses both axes");
        m.reset(); m.update(pitchAndTurn(.6, 1.1), 1, true, 1000);
        double[] tilted = m.update(pitchAndTurn(.64, 1.16), 1.016, true, 1000);
        require(tilted[0] < -.1 && tilted[1] < -.1,
            "A tilted neutral grip retains horizontal turning and vertical pitching");
        require(m.update(pitchAndTurn(.64, 1.16), 3, true, 1000)[0] == 0,
            "Turning mode also rebases without a jump after a sensor gap");
        System.out.println("PASS: "+checks+" motion and calibration checks");
    }
}
