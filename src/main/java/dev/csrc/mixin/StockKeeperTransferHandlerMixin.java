package dev.csrc.mixin;

import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import dev.csrc.compat.jei.CapacityMatcher;
import dev.csrc.compat.jei.CompatRecipeTransfer;
import dev.csrc.compat.jei.PreviewCache;
import dev.csrc.config.ClientConfig;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {StockKeeperTransferHandler.class},
   remap = false
)
public abstract class StockKeeperTransferHandlerMixin {
   @Inject(
      method = {"transferRecipe(Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void csrc$transfer(
      StockKeeperRequestMenu menu,
      Object source,
      IRecipeSlotsView slots,
      Player player,
      boolean maxTransfer,
      boolean doTransfer,
      CallbackInfoReturnable<IRecipeTransferError> cir
   ) {
      if (player.level().isClientSide()) {
         if (!doTransfer && (Boolean)ClientConfig.ENABLE.get()) {
            PreviewCache.Hit cached = PreviewCache.get(menu, source, slots, maxTransfer);
            if (cached != null) {
               cir.setReturnValue(cached.error());
               return;
            }
         }

         try {
            if (CompatRecipeTransfer.shouldHandle(source, slots)) {
               IRecipeTransferError error = CompatRecipeTransfer.transfer(menu, source, slots, player, maxTransfer, doTransfer);
               if (!doTransfer && (Boolean)ClientConfig.ENABLE.get()) {
                  PreviewCache.put(menu, source, slots, maxTransfer, error);
               }

               cir.setReturnValue(error);
            }
         } catch (CapacityMatcher.SearchLimit var9) {
            cir.setReturnValue(CompatRecipeTransfer.searchLimitError());
         } catch (Throwable var10) {
            CompatRecipeTransfer.reportFailure(var10);
            cir.setReturnValue(CompatRecipeTransfer.internalError());
         }
      }
   }

   @Inject(
      method = {"transferRecipe(Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"},
      at = {@At("RETURN")}
   )
   private void csrc$rememberNativePreview(
      StockKeeperRequestMenu menu,
      Object source,
      IRecipeSlotsView slots,
      Player player,
      boolean maximum,
      boolean doTransfer,
      CallbackInfoReturnable<IRecipeTransferError> cir
   ) {
      if (!doTransfer && (Boolean)ClientConfig.ENABLE.get() && player.level().isClientSide()) {
         PreviewCache.put(menu, source, slots, maximum, (IRecipeTransferError)cir.getReturnValue());
      }
   }
}
