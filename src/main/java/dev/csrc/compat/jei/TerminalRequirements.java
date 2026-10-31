package dev.csrc.compat.jei;

final class TerminalRequirements {
   static TerminalRequirements.Result check(int[][] rows, int[] capacities, boolean[] used, int remainingTypes) {
      try {
         if (new CapacityMatcher(rows, capacities, used, remainingTypes).match(1) != null) {
            return TerminalRequirements.Result.POSSIBLE;
         } else {
            return remainingTypes >= 0 && new CapacityMatcher(rows, capacities, used, -1).match(1) != null
               ? TerminalRequirements.Result.TYPES
               : TerminalRequirements.Result.MISSING;
         }
      } catch (CapacityMatcher.SearchLimit var5) {
         return TerminalRequirements.Result.UNKNOWN;
      }
   }

   private TerminalRequirements() {
   }

   static enum Result {
      POSSIBLE,
      MISSING,
      TYPES,
      UNKNOWN;
   }
}
