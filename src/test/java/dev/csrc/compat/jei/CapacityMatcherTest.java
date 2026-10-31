package dev.csrc.compat.jei;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CapacityMatcherTest {
    @Test void maximumFixedBatchRespectsSharedStockAndCap() {
        var solver = new CapacityMatcher(new int[][]{{0}, {0}, {1}}, new int[]{100, 80}, new boolean[2], 2);
        var result = solver.maximum(Integer.MAX_VALUE);
        assertEquals(50, result.rounds());
        assertArrayEquals(new int[]{50, 0}, result.amounts()[0]);
        assertArrayEquals(new int[]{50, 0}, result.amounts()[1]);
        assertArrayEquals(new int[]{0, 50}, result.amounts()[2]);
        assertEquals(7, solver.maximum(7).rounds());
        assertEquals(0, solver.maximum(0).rounds());
        assertThrows(IllegalArgumentException.class, () -> solver.maximum(-1));
    }
    @Test void maximumFixedBatchStillChecksTypeLimit() {
        assertEquals(0, new CapacityMatcher(new int[][]{{0}, {1}}, new int[]{100, 100},
            new boolean[2], 1).maximum(100).rounds());
    }
    @Test void maximumHandlesEmptyCandidatesAndUnavailableStock() {
        assertEquals(0, new CapacityMatcher(new int[][]{{}}, new int[]{100},
            new boolean[1], 1).maximum(100).rounds());
        assertEquals(0, new CapacityMatcher(new int[][]{{0}}, new int[]{-1},
            new boolean[1], 1).maximum(100).rounds());
    }
    @Test void maximumFlowHandlesIntegerBoundary() {
        var result = new CapacityMatcher(new int[][]{{0, 1}},
            new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, new boolean[2], 1).maximum(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, result.rounds());
        assertEquals(Integer.MAX_VALUE, Arrays.stream(result.amounts()[0]).asLongStream().sum());
    }
    @Test void maximumBatchesAgreeWithIndependentBruteForce() {
        Random random = new Random(112);
        for (int trial = 0; trial < 2000; trial++) {
            int n = 1 + random.nextInt(4), m = 1 + random.nextInt(4), cap = random.nextInt(7);
            int[][] rows = IntStream.range(0, n).mapToObj(i -> IntStream.range(0, m)
                .filter(j -> random.nextBoolean()).toArray()).toArray(int[][]::new);
            int[] counts = IntStream.range(0, m).map(i -> random.nextInt(6)).toArray();
            boolean[] existing = new boolean[m];
            for (int j = 0; j < m; j++) existing[j] = random.nextBoolean();
            int limit = random.nextInt(m + 1), expected = 0;
            for (int rounds = 1; rounds <= cap; rounds++) {
                if (!brute(rows, counts.clone(), existing, new int[m], limit, rounds, 0)) break;
                expected = rounds;
            }
            var result = new CapacityMatcher(rows, counts, existing, limit).maximum(cap);
            assertEquals(expected, result.rounds(), "maximum trial " + trial);
            if (expected == 0) { assertEquals(0, result.amounts().length); continue; }
            int[] used = new int[m];
            for (int i = 0; i < n; i++) {
                assertEquals(expected, Arrays.stream(result.amounts()[i]).sum());
                for (int j = 0; j < m; j++) {
                    if (result.amounts()[i][j] > 0) {
                        int index = j;
                        assertTrue(Arrays.stream(rows[i]).anyMatch(k -> k == index));
                    }
                    used[j] += result.amounts()[i][j];
                }
            }
            int types = 0;
            for (int j = 0; j < m; j++) {
                assertTrue(used[j] <= counts[j]);
                if (used[j] > 0 && !existing[j]) types++;
            }
            assertTrue(types <= limit);
        }
    }
    @Test void duplicateCandidatesDoNotLoseAllocatedUnits() {
        int[][] result = match(new int[][]{{0, 0, 1}, {0}}, new int[]{2, 1}, new boolean[2], 2, 1);
        assertNotNull(result);
        assertEquals(1, Arrays.stream(result[0]).sum());
        assertEquals(1, Arrays.stream(result[1]).sum());
        assertArrayEquals(new int[]{2}, match(new int[][]{{0, 0}}, new int[]{2}, new boolean[1], 1, 2)[0]);
    }
    @Test void solverOwnsItsInputSnapshot() {
        int[][] rows = {{0}};
        int[] counts = {2};
        boolean[] existing = {true};
        var solver = new CapacityMatcher(rows, counts, existing, 0);
        rows[0][0] = 99; counts[0] = 0; existing[0] = false;
        assertArrayEquals(new int[]{2}, solver.match(2)[0]);
    }
    @Test void fixedInputsAccumulateWithoutIntegerOverflow() {
        assertNull(match(new int[][]{{0}, {0}}, new int[]{Integer.MAX_VALUE},
            new boolean[1], 1, Integer.MAX_VALUE));
    }
    @Test void flowProbesHaveIndependentBudgets() {
        int[][] rows = IntStream.range(0, 256).mapToObj(i -> new int[]{0, 1}).toArray(int[][]::new);
        var solver = new CapacityMatcher(rows, new int[]{512, 512}, new boolean[2], 2);
        for (int i = 0; i < 500; i++) assertNotNull(solver.match(2));
        assertNull(solver.match(5));
        assertNotNull(solver.match(4));
    }
    @Test void configuredMaximumRemainsWithinBudgetAcrossBinarySearch() {
        int[][] units = IntStream.range(0, 256).mapToObj(i -> new int[]{0}).toArray(int[][]::new);
        var solver = new CapacityMatcher(units, new int[]{1_000_000_000}, new boolean[1], 1);
        for (int round = 1; round <= 3_906_250; round = round * 2) assertNotNull(solver.match(round));
        // A later probe must not inherit the work spent by earlier probes.
        for (int probe = 0; probe < 500; probe++) assertNotNull(solver.match(1));
    }
    @Test void rejectsMalformedCandidateIndicesBeforeFlowConstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> new CapacityMatcher(new int[][]{{1}}, new int[]{1}, new boolean[1], 1));
        assertThrows(IllegalArgumentException.class,
            () -> new CapacityMatcher(new int[][]{{0}}, new int[]{1}, new boolean[0], 1));
    }
    @Test void overlappingAlternativesReassignEarlierChoice() {
        int[][] result = match(new int[][]{{0, 1}, {0}}, new int[]{1, 1}, new boolean[2], 9, 1);
        assertArrayEquals(new int[]{0, 1}, result[0]);
        assertArrayEquals(new int[]{1, 0}, result[1]);
    }
    @Test void repeatedUnitsConsumeRealCapacity() {
        int[][] units = IntStream.range(0, 64).mapToObj(i -> new int[]{0}).toArray(int[][]::new);
        assertNotNull(match(units, new int[]{64}, new boolean[1], 1, 1));
        assertNull(match(units, new int[]{63}, new boolean[1], 1, 1));
    }
    @Test void multipleRoundsAndSharedCapacity() {
        assertNotNull(match(new int[][]{{0, 1}, {0}}, new int[]{16, 16}, new boolean[2], 2, 16));
        assertNull(match(new int[][]{{0, 1}, {0}}, new int[]{15, 17}, new boolean[2], 2, 16));
    }
    @Test void tenTypesRejectedButNinePlusExistingAccepted() {
        int[][] units = IntStream.range(0, 10).mapToObj(i -> new int[]{i}).toArray(int[][]::new);
        int[] counts = IntStream.range(0, 10).map(i -> 1).toArray();
        assertNull(match(units, counts, new boolean[10], 9, 1));
        boolean[] existing = new boolean[10]; existing[0] = true;
        assertNotNull(match(units, counts, existing, 9, 1));
    }
    @Test void selectsAlternativeThatFitsTypeLimit() {
        int[][] result = match(new int[][]{{0, 1}, {2, 1}}, new int[]{1, 2, 1}, new boolean[3], 1, 1);
        assertArrayEquals(new int[]{0, 1, 0}, result[0]);
        assertArrayEquals(new int[]{0, 1, 0}, result[1]);
    }
    @Test void fullOrderCanReuseExistingType() {
        assertNotNull(match(new int[][]{{0}}, new int[]{1}, new boolean[]{true}, 0, 1));
        assertNull(match(new int[][]{{0}}, new int[]{1}, new boolean[]{false}, 0, 1));
    }
    @Test void largeCountsDoNotExpandIntoBillionsOfObjects() {
        int[][] plan = match(new int[][]{{0}, {0}}, new int[]{1_000_000_000}, new boolean[1], 1, 500_000_000);
        assertEquals(500_000_000, plan[0][0]);
        assertEquals(500_000_000, plan[1][0]);
    }
    @Test void randomCasesAgreeWithIndependentBruteForce() {
        Random r = new Random(289);
        for (int trial = 0; trial < 2000; trial++) {
            int n = 1 + r.nextInt(5), m = 1 + r.nextInt(4), rounds = 1 + r.nextInt(2);
            int[][] candidates = new int[n][];
            for (int i = 0; i < n; i++) candidates[i] = IntStream.range(0, m).filter(j -> r.nextBoolean()).toArray();
            int[] counts = IntStream.range(0, m).map(j -> r.nextInt(5)).toArray();
            boolean[] existing = new boolean[m];
            for (int j = 0; j < m; j++) existing[j] = r.nextBoolean();
            int limit = r.nextInt(m + 1);
            boolean expected = brute(candidates, counts.clone(), existing, new int[m], limit, rounds, 0);
            int[][] actual = match(candidates, counts, existing, limit, rounds);
            assertEquals(expected, actual != null, "trial " + trial);
            if (actual == null) continue;
            int[] used = new int[m];
            for (int i = 0; i < n; i++) {
                assertEquals(rounds, Arrays.stream(actual[i]).sum());
                for (int j = 0; j < m; j++) {
                    if (actual[i][j] > 0) {
                        int index = j;
                        assertTrue(Arrays.stream(candidates[i]).anyMatch(k -> k == index));
                    }
                    used[j] += actual[i][j];
                }
            }
            int types = 0;
            for (int j = 0; j < m; j++) {
                assertTrue(used[j] <= counts[j]);
                if (used[j] > 0 && !existing[j]) types++;
            }
            assertTrue(types <= limit);
        }
    }
    private static int[][] match(int[][] candidates, int[] counts, boolean[] existing, int limit, int rounds) {
        return new CapacityMatcher(candidates, counts, existing, limit).match(rounds);
    }
    private static boolean brute(int[][] candidates, int[] counts, boolean[] existing, int[] used,
                                 int limit, int rounds, int index) {
        if (index == candidates.length * rounds) return true;
        for (int j : candidates[index / rounds]) {
            if (counts[j] == 0) continue;
            boolean newType = used[j] == 0 && !existing[j];
            if (newType && limit == 0) continue;
            counts[j]--; used[j]++;
            if (brute(candidates, counts, existing, used, limit - (newType ? 1 : 0), rounds, index + 1)) return true;
            counts[j]++; used[j]--;
        }
        return false;
    }
}
