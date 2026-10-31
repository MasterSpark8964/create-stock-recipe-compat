package dev.csrc.compat.jei;

import java.util.List;
import java.util.function.Function;

final class ProductionSearch {
   static <T, S> S directFirst(List<T> recipes, Function<T, S> direct, Function<T, S> recursive) {
      for (T recipe : recipes) {
         S result = direct.apply(recipe);
         if (result != null) {
            return result;
         }
      }

      for (T recipex : recipes) {
         S result = recursive.apply(recipex);
         if (result != null) {
            return result;
         }
      }

      return null;
   }

   private ProductionSearch() {
   }
}
