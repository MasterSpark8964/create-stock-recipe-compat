package dev.csrc.mixin;

import dev.csrc.compat.jei.LegacyButtonLayout;
import dev.csrc.compat.jei.LegacyJeiButtons;
import dev.csrc.compat.jei.WorkstationRecipes;
import java.util.List;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {RecipeGuiLayouts.class},
   remap = false
)
public abstract class LegacyRecipeGuiSpacingMixin {
   @Shadow
   @Final
   private List<RecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

   @Shadow
   private int getRecipeXOffset(ImmutableRect2i rect, ImmutableRect2i area) {
      throw new AssertionError();
   }

   @Shadow
   private void updateRecipeButtonPositions() {
      throw new AssertionError();
   }

   @Inject(
      method = {"updateLayout(Lmezz/jei/common/util/ImmutableRect2i;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void csrc$layoutWithButtonRoom(ImmutableRect2i area, int recipesPerPage, CallbackInfo ci) {
      if (LegacyJeiButtons.isStockKeeperContext() && !this.recipeLayoutsWithButtons.isEmpty()) {
         IRecipeLayoutDrawable<?> first = this.recipeLayoutsWithButtons.get(0).recipeLayout();
         if (WorkstationRecipes.supportsLayout(first)) {
            ImmutableRect2i border = new ImmutableRect2i(first.getRectWithBorder());
            Rect2i bookmark = first.getRecipeBookmarkButtonArea();
            int extra = Math.max(0, border.getY() - (first.getRect().getY() + bookmark.getY() - bookmark.getHeight() - 2));
            if (extra != 0) {
               int x = this.getRecipeXOffset(border, area);
               LegacyButtonLayout.Rows rows = LegacyButtonLayout.rows(area.getY(), area.getHeight(), border.getHeight(), recipesPerPage, extra);
               int y = rows.firstTop();

               for (RecipeLayoutWithButtons<?> entry : this.recipeLayoutsWithButtons) {
                  IRecipeLayoutDrawable<?> layout = entry.recipeLayout();
                  Rect2i frame = layout.getRectWithBorder();
                  Rect2i rect = layout.getRect();
                  layout.setPosition(x - frame.getX() + rect.getX(), y - frame.getY() + rect.getY());
                  y += rows.stride();
               }

               this.updateRecipeButtonPositions();
               ci.cancel();
            }
         }
      }
   }
}
