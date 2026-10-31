package dev.csrc.compat.jei;

public final class LegacyButtonLayout {
   public static int topRoom(int borderedRecipeHeight) {
      return Math.max(0, 43 - borderedRecipeHeight);
   }

   public static LegacyButtonLayout.Rows rows(int areaTop, int availableHeight, int recipeHeight, int count, int topRoom) {
      int recipes = Math.max(1, count);
      int rowHeight = recipeHeight + topRoom;
      int remaining = Math.max(availableHeight, rowHeight) - recipes * rowHeight;
      int gap = Math.max(0, remaining / (recipes + 1));
      return new LegacyButtonLayout.Rows(areaTop + gap + topRoom, rowHeight + gap);
   }

   private LegacyButtonLayout() {
   }

   public static record Rows(int firstTop, int stride) {
   }
}
