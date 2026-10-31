package dev.csrc.mixin;

import java.util.List;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {RecipeGuiLayouts.class},
   remap = false
)
public interface LegacyRecipeGuiLayoutsAccessor {
   @Accessor("recipeLayoutsWithButtons")
   List<RecipeLayoutWithButtons<?>> csrc$visibleLayouts();
}
