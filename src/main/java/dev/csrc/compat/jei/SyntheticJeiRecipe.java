package dev.csrc.compat.jei;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class SyntheticJeiRecipe implements Recipe<Container> {
   private static final AtomicLong NEXT_ID = new AtomicLong();
   private final ResourceLocation id = new ResourceLocation("create_stock_recipe_compat", "synthetic/" + NEXT_ID.incrementAndGet());
   private final ItemStack result;

   public SyntheticJeiRecipe(ItemStack result) {
      this.result = result.copy();
   }

   public boolean matches(Container container, Level level) {
      return false;
   }

   public ItemStack assemble(Container container, RegistryAccess access) {
      return this.result.copy();
   }

   public boolean canCraftInDimensions(int width, int height) {
      return false;
   }

   public ItemStack getResultItem(RegistryAccess access) {
      return this.result.copy();
   }

   public ResourceLocation getId() {
      return this.id;
   }

   public RecipeSerializer<?> getSerializer() {
      return RecipeSerializer.SHAPELESS_RECIPE;
   }

   public RecipeType<?> getType() {
      return RecipeType.CRAFTING;
   }
}
