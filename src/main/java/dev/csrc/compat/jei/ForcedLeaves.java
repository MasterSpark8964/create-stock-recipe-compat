package dev.csrc.compat.jei;

final class ForcedLeaves {
   static ForcedLeaves.Result evaluate(int[] forcedVariant, int[] units, int[] capacity, boolean[] existing, int room) {
      if (forcedVariant.length == units.length && capacity.length == existing.length) {
         int[] required = new int[capacity.length];

         for (int i = 0; i < forcedVariant.length; i++) {
            int variant = forcedVariant[i];
            if (variant >= 0) {
               if (variant >= required.length || units[i] < 0) {
                  throw new IllegalArgumentException();
               }

               required[variant] = Math.addExact(required[variant], units[i]);
            }
         }

         int fresh = 0;

         for (int ix = 0; ix < required.length; ix++) {
            if (required[ix] > capacity[ix]) {
               return ForcedLeaves.Result.MISSING;
            }

            if (required[ix] > 0 && !existing[ix]) {
               fresh++;
            }
         }

         return fresh > room ? ForcedLeaves.Result.TOO_MANY_TYPES : ForcedLeaves.Result.POSSIBLE;
      } else {
         throw new IllegalArgumentException();
      }
   }

   private ForcedLeaves() {
   }

   static enum Result {
      POSSIBLE,
      MISSING,
      TOO_MANY_TYPES;
   }
}
