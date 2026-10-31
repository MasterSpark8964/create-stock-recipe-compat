import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Runs recovered algorithms against the archived original Jar in isolated class loaders. */
public final class RecoveryEquivalence {
    static String allocate(ClassLoader loader, int[][] candidates, int[] stock, boolean[] existing, int limit, int rounds) throws Exception {
        try {
            Class<?> matcher = loader.loadClass("dev.csrc.compat.jei.CapacityMatcher");
            Object instance = matcher.getConstructor(int[][].class, int[].class, boolean[].class, int.class)
                .newInstance(candidates, stock, existing, limit);
            Object plan = matcher.getMethod("maximum", int.class).invoke(instance, rounds);
            return plan.getClass().getMethod("rounds").invoke(plan) + ":" + Arrays.deepToString((int[][])plan.getClass().getMethod("amounts").invoke(plan));
        } catch (InvocationTargetException error) {
            return "throws:" + error.getCause().getClass().getName() + ":" + error.getCause().getMessage();
        }
    }
    public static void main(String[] args) throws Exception {
        try (var original = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             var recovered = new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var random = new Random(46001);
            for (int trial = 0; trial < 3000; trial++) {
                int kinds = 1 + random.nextInt(10), slots = 1 + random.nextInt(12);
                int[][] candidates = new int[slots][];
                int[] stock = new int[kinds];
                boolean[] existing = new boolean[kinds];
                for (int k = 0; k < kinds; k++) { stock[k] = random.nextInt(30); existing[k] = random.nextBoolean(); }
                for (int s = 0; s < slots; s++) {
                    candidates[s] = new int[random.nextInt(kinds + 1)];
                    for (int k = 0; k < candidates[s].length; k++) candidates[s][k] = random.nextInt(kinds);
                }
                int limit = random.nextInt(10), rounds = random.nextInt(65);
                String expected = allocate(original, candidates, stock, existing, limit, rounds);
                String actual = allocate(recovered, candidates, stock, existing, limit, rounds);
                if (!expected.equals(actual)) throw new AssertionError("Allocation mismatch at case " + trial + " expected=" + expected + " actual=" + actual);
            }
            Class<?> before = original.loadClass("dev.csrc.compat.jei.LegacyButtonLayout");
            Class<?> after = recovered.loadClass("dev.csrc.compat.jei.LegacyButtonLayout");
            for (int trial = 0; trial < 5000; trial++) {
                int height = random.nextInt(400);
                if (!before.getMethod("topRoom", int.class).invoke(null, height).equals(after.getMethod("topRoom", int.class).invoke(null, height)))
                    throw new AssertionError("Top spacing mismatch");
                Object[] parameters = {random.nextInt(200), random.nextInt(600), height, random.nextInt(10), random.nextInt(50)};
                String expected = before.getMethod("rows", int.class, int.class, int.class, int.class, int.class).invoke(null, parameters).toString();
                String actual = after.getMethod("rows", int.class, int.class, int.class, int.class, int.class).invoke(null, parameters).toString();
                if (!expected.equals(actual)) throw new AssertionError("Button layout mismatch");
            }
            System.out.println("PASS: 3000 material allocation cases and 5000 button layout cases match the original Jar.");
        }
    }
}
