package dev.csrc.mixin;

import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {RecipesGui.class},
   remap = false
)
public interface LegacyRecipesGuiAccessor {
   @Accessor("parentScreen")
   Screen csrc$parentScreen();

   @Accessor("layouts")
   RecipeGuiLayouts csrc$layouts();
}
