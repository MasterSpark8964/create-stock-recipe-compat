package dev.csrc.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({SmithingTransformRecipe.class})
public interface SmithingTransformIngredientsAccessor {
   @Accessor("template")
   Ingredient csrc$template();

   @Accessor("base")
   Ingredient csrc$base();

   @Accessor("addition")
   Ingredient csrc$addition();
}
