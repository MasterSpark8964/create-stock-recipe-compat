package dev.csrc.compat.jei;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Small capacitated matching solver, independent of Minecraft for exhaustive tests. */
public final class CapacityMatcher {
    public static final class SearchLimit extends RuntimeException {
        public SearchLimit() { super("Material matching safety budget exceeded"); }
    }
    private static final class Edge {
        final int to, reverse, initial;
        int remaining;
        Edge(int to, int reverse, int capacity) {
            this.to = to; this.reverse = reverse; this.initial = this.remaining = capacity;
        }
    }
    private int budget = 200_000;
    private int branches = 512;
    private final int[][] candidates;
    private final int[] capacities;
    private final boolean[] existing;
    private final int newTypeLimit;
    private final boolean singleChoice;
    private final Set<BitSet> visited = new HashSet<>();
    public record Allocation(int rounds, int[][] amounts) {}

    public CapacityMatcher(int[][] candidates, int[] capacities, boolean[] existing, int newTypeLimit) {
        if (candidates == null || capacities == null || existing == null || existing.length != capacities.length)
            throw new IllegalArgumentException("Mismatched matching inputs");
        if (candidates.length == 0 || candidates.length > 2048 || capacities.length > 32768
                || (long) candidates.length * capacities.length > 16_777_216L)
            throw new SearchLimit();
        this.candidates = new int[candidates.length][];
        this.capacities = capacities.clone();
        this.existing = existing.clone();
        this.newTypeLimit = newTypeLimit;
        long edges = 0;
        boolean unique = true;
        for (int i = 0; i < candidates.length; i++) {
            int[] row = candidates[i];
            if (row == null) throw new IllegalArgumentException("Null candidate row");
            edges += row.length;
            if (edges > 262144) throw new SearchLimit();
            for (int index : row)
                if (index < 0 || index >= capacities.length) throw new IllegalArgumentException("Invalid candidate index");
            // Parallel edges for duplicate indices would overwrite the returned allocation.
            this.candidates[i] = Arrays.stream(row).distinct().toArray();
            unique &= this.candidates[i].length == 1;
        }
        singleChoice = unique;
    }

    /** Largest complete batch, capped by the caller's representable output quantity. */
    public Allocation maximum(int maxRounds) {
        if (maxRounds < 0) throw new IllegalArgumentException("maxRounds");
        if (maxRounds == 0) return new Allocation(0, new int[0][]);
        if (singleChoice) {
            int[] occurrences = new int[capacities.length];
            for (int[] row : candidates) occurrences[row[0]]++;
            int upper = maxRounds;
            for (int j = 0; j < occurrences.length; j++)
                if (occurrences[j] != 0)
                    upper = Math.min(upper, Math.max(0, capacities[j]) / occurrences[j]);
            int[][] plan = upper == 0 ? null : match(upper);
            return plan == null ? new Allocation(0, new int[0][]) : new Allocation(upper, plan);
        }
        // Per-input capacity is a necessary bound; shared stock is still resolved by flow.
        int high = maxRounds;
        BitSet relevant = new BitSet(capacities.length);
        for (int[] row : candidates) {
            long available = 0;
            for (int j : row) {
                available += Math.max(0, capacities[j]);
                relevant.set(j);
            }
            high = (int) Math.min(high, available);
        }
        long total = 0;
        for (int j = relevant.nextSetBit(0); j >= 0; j = relevant.nextSetBit(j + 1))
            total += Math.max(0, capacities[j]);
        high = (int) Math.min(high, total / candidates.length);
        int low = 0;
        int[][] best = new int[0][];
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            int[][] plan = match(mid);
            if (plan == null) high = mid - 1;
            else { low = mid; best = plan; }
        }
        return new Allocation(low, best);
    }

    /** Each row needs rounds units. Null means no complete allocation, never a partial plan. */
    public int[][] match(int rounds) {
        if (rounds < 1) throw new IllegalArgumentException("rounds");
        // Each binary-search probe is an independent bounded matching problem.
        budget = 200_000;
        branches = 512;
        visited.clear();
        if (singleChoice) return direct(rounds);
        return constrained(rounds, new BitSet(capacities.length));
    }

    /** Fixed ingredients need only capacity totals, not a flow graph or type-set search. */
    private int[][] direct(int rounds) {
        long[] required = new long[capacities.length];
        int newTypes = 0;
        for (int[] row : candidates) {
            int j = row[0];
            if (required[j] == 0 && !existing[j]) newTypes++;
            required[j] += rounds;
            if (required[j] > capacities[j]) return null;
        }
        if (newTypeLimit >= 0 && newTypes > newTypeLimit) return null;
        int[][] plan = new int[candidates.length][capacities.length];
        for (int i = 0; i < candidates.length; i++) plan[i][candidates[i][0]] = rounds;
        return plan;
    }

    private int[][] constrained(int rounds, BitSet excluded) {
        // Different exclusion orders can reach the same state; explore it only once per probe.
        if (!visited.add((BitSet) excluded.clone())) return null;
        if (--branches < 0) throw new SearchLimit();
        int[][] plan = flow(rounds, excluded);
        if (plan == null || newTypeLimit < 0) return plan;
        BitSet usedNew = new BitSet(capacities.length);
        for (int[] row : plan)
            for (int j = 0; j < row.length; j++)
                if (row[j] > 0 && !existing[j]) usedNew.set(j);
        if (usedNew.cardinality() <= newTypeLimit) return plan;
        // Only the used-type set is needed below. Do not retain a potentially 64 MiB
        // allocation matrix in every parent frame while searching alternative type sets.
        plan = null;
        // Any valid smaller set must exclude at least one member of this oversized set.
        for (int j = usedNew.nextSetBit(0); j >= 0; j = usedNew.nextSetBit(j + 1)) {
            excluded.set(j);
            int[][] alternative = constrained(rounds, excluded);
            excluded.clear(j);
            if (alternative != null) return alternative;
        }
        return null;
    }

    private int[][] flow(int rounds, BitSet excluded) {
        int n = candidates.length, m = capacities.length, sink = n + m + 1;
        List<List<Edge>> graph = new ArrayList<>(sink + 1);
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            add(graph, 0, i + 1, rounds);
            for (int j : candidates[i])
                if (!excluded.get(j) && capacities[j] > 0) add(graph, i + 1, n + j + 1, rounds);
        }
        for (int j = 0; j < m; j++) add(graph, n + j + 1, sink, Math.max(0, capacities[j]));
        long needed = (long) n * rounds, total = 0;
        int[] level = new int[sink + 1];
        while (total < needed) {
            Arrays.fill(level, -1);
            level[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty()) {
                int node = queue.remove();
                if (--budget < 0) throw new SearchLimit();
                var edges = graph.get(node);
                for (Edge edge : edges) {
                    if (edge.remaining <= 0 || level[edge.to] != -1) continue;
                    level[edge.to] = level[node] + 1;
                    queue.add(edge.to);
                }
            }
            if (level[sink] == -1) return null;
            int[] next = new int[sink + 1];
            int amount;
            while ((amount = send(graph, level, next, 0, sink, Integer.MAX_VALUE)) > 0) {
                total += amount;
            }
        }
        int[][] plan = new int[n][m];
        for (int i = 0; i < n; i++)
            for (Edge edge : graph.get(i + 1))
                if (edge.to > n && edge.to < sink) plan[i][edge.to - n - 1] = edge.initial - edge.remaining;
        return plan;
    }

    private int send(List<List<Edge>> graph, int[] level, int[] next, int node, int sink, int amount) {
        if (--budget < 0) throw new SearchLimit();
        if (node == sink) return amount;
        var edges = graph.get(node);
        while (next[node] < edges.size()) {
            Edge edge = edges.get(next[node]);
            if (edge.remaining > 0 && level[edge.to] == level[node] + 1) {
                int sent = send(graph, level, next, edge.to, sink, Math.min(amount, edge.remaining));
                if (sent > 0) {
                    edge.remaining -= sent;
                    graph.get(edge.to).get(edge.reverse).remaining += sent;
                    return sent;
                }
            }
            next[node]++;
        }
        return 0;
    }

    private static void add(List<List<Edge>> graph, int from, int to, int capacity) {
        Edge forward = new Edge(to, graph.get(to).size(), capacity);
        Edge reverse = new Edge(from, graph.get(from).size(), 0);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
    }
}
