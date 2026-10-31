package dev.csrc.compat.jei;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CapacityMatcher {
   private int budget = 200000;
   private int branches = 512;
   private final int[][] candidates;
   private final int[] capacities;
   private final boolean[] existing;
   private final int newTypeLimit;
   private final boolean singleChoice;
   private final Set<BitSet> visited = new HashSet<>();

   public CapacityMatcher(int[][] candidates, int[] capacities, boolean[] existing, int newTypeLimit) {
      if (candidates == null || capacities == null || existing == null || existing.length != capacities.length) {
         throw new IllegalArgumentException("Mismatched matching inputs");
      } else if (candidates.length != 0
         && candidates.length <= 2048
         && capacities.length <= 32768
         && (long)candidates.length * (long)capacities.length <= 16777216L) {
         this.candidates = new int[candidates.length][];
         this.capacities = (int[])capacities.clone();
         this.existing = (boolean[])existing.clone();
         this.newTypeLimit = newTypeLimit;
         long edges = 0L;
         boolean unique = true;

         for (int i = 0; i < candidates.length; i++) {
            int[] row = candidates[i];
            if (row == null) {
               throw new IllegalArgumentException("Null candidate row");
            }

            edges += (long)row.length;
            if (edges > 262144L) {
               throw new CapacityMatcher.SearchLimit();
            }

            for (int index : row) {
               if (index < 0 || index >= capacities.length) {
                  throw new IllegalArgumentException("Invalid candidate index");
               }
            }

            this.candidates[i] = Arrays.stream(row).distinct().toArray();
            unique &= this.candidates[i].length == 1;
         }

         this.singleChoice = unique;
      } else {
         throw new CapacityMatcher.SearchLimit();
      }
   }

   public CapacityMatcher.Allocation maximum(int maxRounds) {
      if (maxRounds < 0) {
         throw new IllegalArgumentException("maxRounds");
      } else if (maxRounds == 0) {
         return new CapacityMatcher.Allocation(0, new int[0][]);
      } else if (this.singleChoice) {
         int[] occurrences = new int[this.capacities.length];

         for (int[] row : this.candidates) {
            occurrences[row[0]]++;
         }

         int upper = maxRounds;

         for (int j = 0; j < occurrences.length; j++) {
            if (occurrences[j] != 0) {
               upper = Math.min(upper, Math.max(0, this.capacities[j]) / occurrences[j]);
            }
         }

         int[][] plan = upper == 0 ? null : this.match(upper);
         return plan == null ? new CapacityMatcher.Allocation(0, new int[0][]) : new CapacityMatcher.Allocation(upper, plan);
      } else {
         int high = maxRounds;
         BitSet relevant = new BitSet(this.capacities.length);

         for (int[] row : this.candidates) {
            long available = 0L;

            for (int jx : row) {
               available += (long)Math.max(0, this.capacities[jx]);
               relevant.set(jx);
            }

            high = (int)Math.min((long)high, available);
         }

         long total = 0L;

         for (int jx = relevant.nextSetBit(0); jx >= 0; jx = relevant.nextSetBit(jx + 1)) {
            total += (long)Math.max(0, this.capacities[jx]);
         }

         high = (int)Math.min((long)high, total / (long)this.candidates.length);
         int low = 0;
         int[][] best = new int[0][];

         while (low < high) {
            int mid = low + (int)(((long)high - (long)low + 1L) / 2L);
            int[][] plan = this.match(mid);
            if (plan == null) {
               high = mid - 1;
            } else {
               low = mid;
               best = plan;
            }
         }

         return new CapacityMatcher.Allocation(low, best);
      }
   }

   public int[][] match(int rounds) {
      if (rounds < 1) {
         throw new IllegalArgumentException("rounds");
      } else {
         this.budget = 200000;
         this.branches = 512;
         this.visited.clear();
         return this.singleChoice ? this.direct(rounds) : this.constrained(rounds, new BitSet(this.capacities.length));
      }
   }

   private int[][] direct(int rounds) {
      long[] required = new long[this.capacities.length];
      int newTypes = 0;

      for (int[] row : this.candidates) {
         int j = row[0];
         if (required[j] == 0L && !this.existing[j]) {
            newTypes++;
         }

         required[j] += (long)rounds;
         if (required[j] > (long)this.capacities[j]) {
            return null;
         }
      }

      if (this.newTypeLimit >= 0 && newTypes > this.newTypeLimit) {
         return null;
      } else {
         int[][] plan = new int[this.candidates.length][this.capacities.length];

         for (int i = 0; i < this.candidates.length; i++) {
            plan[i][this.candidates[i][0]] = rounds;
         }

         return plan;
      }
   }

   private int[][] constrained(int rounds, BitSet excluded) {
      if (!this.visited.add((BitSet)excluded.clone())) {
         return null;
      } else if (--this.branches < 0) {
         throw new CapacityMatcher.SearchLimit();
      } else {
         int[][] plan = this.flow(rounds, excluded);
         if (plan != null && this.newTypeLimit >= 0) {
            BitSet usedNew = new BitSet(this.capacities.length);

            for (int[] row : plan) {
               for (int j = 0; j < row.length; j++) {
                  if (row[j] > 0 && !this.existing[j]) {
                     usedNew.set(j);
                  }
               }
            }

            if (usedNew.cardinality() <= this.newTypeLimit) {
               return plan;
            } else {
               int[][] var10 = null;

               for (int jx = usedNew.nextSetBit(0); jx >= 0; jx = usedNew.nextSetBit(jx + 1)) {
                  excluded.set(jx);
                  int[][] alternative = this.constrained(rounds, excluded);
                  excluded.clear(jx);
                  if (alternative != null) {
                     return alternative;
                  }
               }

               return null;
            }
         } else {
            return plan;
         }
      }
   }

   private int[][] flow(int rounds, BitSet excluded) {
      int n = this.candidates.length;
      int m = this.capacities.length;
      int sink = n + m + 1;
      List<List<CapacityMatcher.Edge>> graph = new ArrayList<>(sink + 1);

      for (int i = 0; i <= sink; i++) {
         graph.add(new ArrayList<>());
      }

      for (int i = 0; i < n; i++) {
         add(graph, 0, i + 1, rounds);

         for (int j : this.candidates[i]) {
            if (!excluded.get(j) && this.capacities[j] > 0) {
               add(graph, i + 1, n + j + 1, rounds);
            }
         }
      }

      for (int jx = 0; jx < m; jx++) {
         add(graph, n + jx + 1, sink, Math.max(0, this.capacities[jx]));
      }

      long needed = (long)n * (long)rounds;
      long total = 0L;
      int[] level = new int[sink + 1];

      while (total < needed) {
         Arrays.fill(level, -1);
         level[0] = 0;
         ArrayDeque<Integer> queue = new ArrayDeque<>();
         queue.add(0);

         while (!queue.isEmpty()) {
            int node = queue.remove();
            if (--this.budget < 0) {
               throw new CapacityMatcher.SearchLimit();
            }

            for (CapacityMatcher.Edge edge : graph.get(node)) {
               if (edge.remaining > 0 && level[edge.to] == -1) {
                  level[edge.to] = level[node] + 1;
                  queue.add(edge.to);
               }
            }
         }

         if (level[sink] == -1) {
            return null;
         }

         int[] next = new int[sink + 1];

         int amount;
         while ((amount = this.send(graph, level, next, 0, sink, Integer.MAX_VALUE)) > 0) {
            total += (long)amount;
         }
      }

      int[][] plan = new int[n][m];

      for (int i = 0; i < n; i++) {
         for (CapacityMatcher.Edge edgex : graph.get(i + 1)) {
            if (edgex.to > n && edgex.to < sink) {
               plan[i][edgex.to - n - 1] = edgex.initial - edgex.remaining;
            }
         }
      }

      return plan;
   }

   private int send(List<List<CapacityMatcher.Edge>> graph, int[] level, int[] next, int node, int sink, int amount) {
      if (--this.budget < 0) {
         throw new CapacityMatcher.SearchLimit();
      } else if (node == sink) {
         return amount;
      } else {
         List<CapacityMatcher.Edge> edges = graph.get(node);

         while (next[node] < edges.size()) {
            CapacityMatcher.Edge edge = edges.get(next[node]);
            if (edge.remaining > 0 && level[edge.to] == level[node] + 1) {
               int sent = this.send(graph, level, next, edge.to, sink, Math.min(amount, edge.remaining));
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
   }

   private static void add(List<List<CapacityMatcher.Edge>> graph, int from, int to, int capacity) {
      CapacityMatcher.Edge forward = new CapacityMatcher.Edge(to, graph.get(to).size(), capacity);
      CapacityMatcher.Edge reverse = new CapacityMatcher.Edge(from, graph.get(from).size(), 0);
      graph.get(from).add(forward);
      graph.get(to).add(reverse);
   }

   public static record Allocation(int rounds, int[][] amounts) {
   }

   private static final class Edge {
      final int to;
      final int reverse;
      final int initial;
      int remaining;

      Edge(int to, int reverse, int capacity) {
         this.to = to;
         this.reverse = reverse;
         this.initial = this.remaining = capacity;
      }
   }

   public static final class SearchLimit extends RuntimeException {
      public SearchLimit() {
         super("Material matching safety budget exceeded");
      }
   }
}
