package dev.csrc.mixin;

import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import dev.csrc.compat.jei.CompatRecipeTransfer;
import dev.csrc.compat.jei.CapacityMatcher;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StockKeeperTransferHandler.class, remap = false)
public abstract class StockKeeperTransferHandlerMixin {
    // Full descriptor avoids also targeting javac's erased AbstractContainerMenu bridge method.
    @Inject(method = "transferRecipe(Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
        at = @At("HEAD"), cancellable = true)
    private void csrc$transfer(StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots,
            Player player, boolean maxTransfer, boolean doTransfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (!player.level().isClientSide()) return;
        try {
            if (CompatRecipeTransfer.shouldHandle(source, slots)
                    || CompatRecipeTransfer.needsRecursiveCrafting(menu, source, slots))
                cir.setReturnValue(CompatRecipeTransfer.transfer(menu, source, slots, player, maxTransfer, doTransfer));
        } catch (CapacityMatcher.SearchLimit limit) {
            cir.setReturnValue(CompatRecipeTransfer.searchLimitError());
        } catch (Throwable failure) {
            CompatRecipeTransfer.reportFailure(failure);
            cir.setReturnValue(CompatRecipeTransfer.internalError());
        }
    }
}
