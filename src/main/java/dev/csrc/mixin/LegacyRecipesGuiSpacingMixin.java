package dev.csrc.mixin;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import dev.csrc.compat.jei.LegacyButtonLayout;
import dev.csrc.compat.jei.LegacyJeiButtons;
import dev.csrc.compat.jei.WorkstationRecipes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
   value = {RecipesGui.class},
   remap = false
)
public abstract class LegacyRecipesGuiSpacingMixin {
   @Shadow
   @Final
   private IRecipeGuiLogic logic;

   @Unique
   private int csrc$room(AbstractContainerMenu menu) {
      if (LegacyJeiButtons.enabled() && menu instanceof StockKeeperRequestMenu) {
         IRecipeCategory<?> category = this.logic.getSelectedRecipeCategory();
         ResourceLocation id = category.getRecipeType().getUid();
         return !WorkstationRecipes.supportsCategory(id.getNamespace(), id.getPath()) ? 0 : LegacyButtonLayout.topRoom(category.getHeight() + 8);
      } else {
         return 0;
      }
   }

   @ModifyArg(
      method = {"updateLayout()V"},
      index = 0,
      at = @At(
         value = "INVOKE",
         target = "Lmezz/jei/gui/recipes/IRecipeGuiLogic;getVisibleRecipeLayoutsWithButtons(IILnet/minecraft/world/inventory/AbstractContainerMenu;)Ljava/util/List;"
      )
   )
   private int csrc$reserveButtonHeight(int height, int padding, AbstractContainerMenu menu) {
      return Math.max(0, height - this.csrc$room(menu));
   }

   @ModifyArg(
      method = {"updateLayout()V"},
      index = 1,
      at = @At(
         value = "INVOKE",
         target = "Lmezz/jei/gui/recipes/IRecipeGuiLogic;getVisibleRecipeLayoutsWithButtons(IILnet/minecraft/world/inventory/AbstractContainerMenu;)Ljava/util/List;"
      )
   )
   private int csrc$reserveButtonPadding(int height, int padding, AbstractContainerMenu menu) {
      return Math.max(padding, this.csrc$room(menu));
   }
}
