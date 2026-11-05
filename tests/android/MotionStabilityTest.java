package dev.airmouse;

/** Exercises the whole path from simulated orientation to integer HID motion. */
public final class MotionStabilityTest {
    private static final double GAIN = 1550;
    private static int checks;

    private static float[] tilt(double horizontal, double vertical) {
        double x = -vertical / 2, y = horizontal / 2;
        return new float[]{(float)(Math.cos(x)*Math.cos(y)), (float)(Math.sin(x)*Math.cos(y)),
            (float)(Math.cos(x)*Math.sin(y)), (float)(Math.sin(x)*Math.sin(y))};
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS: " + message);
    }

    private static double[] sample(MotionMath motion, double x, double y, double time) {
        return motion.update(tilt(x, y), time, false, GAIN);
    }

    public static void main(String[] args) {
        MotionMath motion = new MotionMath();
        MouseReports reports = new MouseReports();
        sample(motion, 0, 0, 1);
        int noiseTravel = 0;
        for (int n = 1; n <= 600; n++) {
            double t = n / 60.0;
            double noise = .0018*Math.sin(2*Math.PI*8*t) + .0006*Math.sin(2*Math.PI*17*t)
                + .00025*Math.sin(2*Math.PI*t);
            double[] delta = sample(motion, noise, -noise, 1+t);
            reports.move(delta[0], delta[1]);
            byte[] report = reports.next();
            if (report != null) noiseTravel += Math.abs(report[1]) + Math.abs(report[2]);
        }
        System.out.println("METRIC: stationary noise travel = " + noiseTravel + " HID pixels / 10 s");
        check(noiseTravel <= 8, "stationary hand/sensor noise is suppressed before HID output");

        motion.reset();
        sample(motion, 0, 0, 1);
        double slowTravel = 0, wrongWay = 0;
        for (int n = 1; n <= 180; n++) {
            double t = n / 60.0;
            double[] delta = sample(motion, .004*t, 0, 1+t);
            slowTravel += delta[0];
            wrongWay += Math.max(0, -delta[0]);
        }
        System.out.printf("METRIC: slow aim = %.2f / %.2f pixels%n", slowTravel, .012*GAIN);
        check(slowTravel > .012*GAIN - 2 && slowTravel <= .012*GAIN,
            "slow deliberate movement accumulates instead of disappearing in a per-frame dead zone");
        check(wrongWay < .001, "slow aiming never reverses direction");

        double[] travel = new double[2];
        for (int index = 0; index < 2; index++) {
            int hz = index == 0 ? 60 : 120;
            motion.reset();
            sample(motion, 0, 0, 1);
            for (int n = 1; n <= hz; n++) travel[index] += sample(motion, .6*n/hz, 0, 1+n/(double)hz)[0];
        }
        System.out.printf("METRIC: fast sweep = %.2f / %.2f pixels at 60 / 120 Hz%n", travel[0], travel[1]);
        check(travel[0] > .6*GAIN*.95 && travel[1] > .6*GAIN*.95,
            "fast deliberate sweeps retain at least 95 percent of their motion");
        check(Math.abs(travel[0]-travel[1]) < 5, "motion stays consistent at 60 and 120 Hz");

        motion.reset();
        sample(motion, 0, 0, 1);
        double sideways = 0;
        for (int n = 1; n <= 120; n++) {
            double t = n / 60.0;
            double[] delta = sample(motion, .0015*Math.sin(2*Math.PI*8*t), .4*t, 1+t);
            sideways += Math.abs(delta[0]);
        }
        check(sideways < 3, "moving one axis does not unfilter tremor on the other axis");

        motion.reset();
        sample(motion, 0, 0, 1);
        sample(motion, .2, 0, 1.016);
        motion.reset();
        double[] recentered = sample(motion, .2, 0, 2);
        check(recentered[0] == 0 && recentered[1] == 0, "recenter discards pending filter motion");
        check(sample(motion, .2, 0, 2.016)[0] == 0, "recenter has no settling drift");

        motion.reset();
        sample(motion, 0, 0, 1);
        double spikeTravel = Math.abs(sample(motion, .9, 0, 1.016)[0]);
        for (int n = 2; n <= 120; n++) spikeTravel += Math.abs(sample(motion, 0, 0, 1+n/60.0)[0]);
        check(spikeTravel == 0, "a sensor discontinuity cannot leak through as a delayed cursor jump");

        motion.reset();
        sample(motion, 0, 0, 1);
        double[] invalid = motion.update(new float[]{Float.NaN, 0, 0, 0}, 1.016, false, GAIN);
        check(invalid[0] == 0 && invalid[1] == 0 && sample(motion, 0, 0, 1.033)[0] == 0,
            "an invalid sensor sample cannot poison later motion");

        motion.reset();
        sample(motion, 0, 0, 1);
        for (int n = 1; n <= 120; n++) sample(motion, .2*n/120, 0, 1+n/60.0);
        double settling = 0, lateTravel = 0;
        for (int n = 1; n <= 120; n++) {
            double delta = sample(motion, .2, 0, 3+n/60.0)[0];
            settling += delta;
            if (n > 30) lateTravel += Math.abs(delta);
        }
        System.out.printf("METRIC: stop settling = %.3f pixels; after 0.5 s = %.3f pixels%n", settling, lateTravel);
        check(settling >= 0 && settling < 12 && lateTravel < .1,
            "stopping a sweep settles promptly without continuing drift");
        System.out.println(checks + " motion stability checks passed.");
    }
}
