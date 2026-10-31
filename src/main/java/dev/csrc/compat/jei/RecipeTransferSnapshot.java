package dev.csrc.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record RecipeTransferSnapshot(Object sourceRecipe, ItemStack result, List<Ingredient> ingredients, int outputCount) {
   public RecipeTransferSnapshot(Object sourceRecipe, ItemStack result, List<Ingredient> ingredients, int outputCount) {
      result = result.copy();
      ingredients = List.copyOf(ingredients);
      if (!result.isEmpty() && outputCount > 0 && !ingredients.isEmpty()) {
         this.sourceRecipe = sourceRecipe;
         this.result = result;
         this.ingredients = ingredients;
         this.outputCount = outputCount;
      } else {
         throw new IllegalArgumentException("Invalid material snapshot");
      }
   }

   public ItemStack result() {
      return this.result.copy();
   }
}
