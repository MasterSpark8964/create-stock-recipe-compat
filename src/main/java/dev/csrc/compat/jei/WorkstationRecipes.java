package dev.csrc.compat.jei;

import java.util.Set;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public final class WorkstationRecipes {
   private static final Set<String> CATEGORIES = Set.of("crafting", "smithing", "stonecutting", "smelting", "blasting", "smoking", "campfire");

   public static boolean supportsCategory(String namespace, String path) {
      return "minecraft".equals(namespace) && CATEGORIES.contains(path);
   }

   public static boolean supportsRecipe(Object source) {
      if (NstRecipeCompat.recipe(source) != null) {
         return false;
      } else if (!(source instanceof Recipe<?> recipe)) {
         return false;
      } else {
         RecipeType<?> type = recipe.getType();
         return type == RecipeType.CRAFTING && recipe instanceof CraftingRecipe
            || type == RecipeType.SMITHING
               && recipe instanceof SmithingRecipe
               && !(recipe instanceof SmithingTrimRecipe)
               && recipe.getSerializer() != RecipeSerializer.SMITHING_TRIM
            || type == RecipeType.STONECUTTING && recipe instanceof StonecutterRecipe
            || (type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING)
               && recipe instanceof AbstractCookingRecipe;
      }
   }

   public static boolean supportsLayout(IRecipeLayoutDrawable<?> layout) {
      ResourceLocation id = layout.getRecipeCategory().getRecipeType().getUid();

      try {
         return supportsCategory(id.getNamespace(), id.getPath()) && supportsRecipe(layout.getRecipe());
      } catch (LinkageError | RuntimeException var3) {
         CompatRecipeTransfer.reportFailure(var3);
         return false;
      }
   }

   private WorkstationRecipes() {
   }
}
