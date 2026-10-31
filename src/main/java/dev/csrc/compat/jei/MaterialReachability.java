package dev.csrc.compat.jei;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MaterialReachability<K> {
   static final int UNREACHABLE = 1000000;
   private final Map<K, Integer> depths = new HashMap<>();
   private final boolean opaqueOutputs;

   MaterialReachability(Collection<K> stock, List<MaterialReachability.Edge<K>> edges, int limit, boolean opaqueOutputs, Runnable checkpoint) {
      this.opaqueOutputs = opaqueOutputs;
      stock.forEach(item -> this.depths.put((K)item, 0));

      for (int pass = 0; pass <= edges.size(); pass++) {
         boolean changed = false;

         for (MaterialReachability.Edge<K> edge : edges) {
            checkpoint.run();
            int depth = this.recipeDepth(edge.inputs());
            if (depth < this.depths.getOrDefault(edge.output(), 1000000)) {
               this.depths.put(edge.output(), depth);
               changed = true;
            }
         }

         if (!changed) {
            break;
         }
      }
   }

   int ingredientDepth(MaterialReachability.Requirement<K> input) {
      if (input.uncertain()) {
         return 0;
      } else {
         int best = 1000000;

         for (K item : input.alternatives()) {
            best = Math.min(best, this.depths.getOrDefault(item, this.opaqueOutputs ? 0 : 1000000));
         }

         return best;
      }
   }

   int recipeDepth(List<MaterialReachability.Requirement<K>> inputs) {
      int depth = 0;

      for (MaterialReachability.Requirement<K> input : inputs) {
         depth = Math.max(depth, this.ingredientDepth(input));
      }

      return depth == 1000000 ? depth : depth + 1;
   }

   static record Edge<K>(K output, List<MaterialReachability.Requirement<K>> inputs) {
      Edge(K output, List<MaterialReachability.Requirement<K>> inputs) {
         inputs = List.copyOf(inputs);
         this.output = output;
         this.inputs = inputs;
      }
   }

   static record Requirement<K>(List<K> alternatives, boolean uncertain) {
      Requirement(List<K> alternatives, boolean uncertain) {
         alternatives = List.copyOf(alternatives);
         this.alternatives = alternatives;
         this.uncertain = uncertain;
      }
   }
}
