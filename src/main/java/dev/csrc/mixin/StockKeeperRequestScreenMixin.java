package dev.csrc.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.csrc.compat.jei.CompatRecipeTransfer;
import dev.csrc.compat.jei.JeiMaterialCraftableBigItemStack;
import dev.csrc.compat.jei.MaterialAllocation;
import java.util.List;
import java.util.function.Function;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {StockKeeperRequestScreen.class},
   remap = false
)
public abstract class StockKeeperRequestScreenMixin {
   @Inject(
      method = {"maxCraftable"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void csrc$allocate(
      CraftableBigItemStack entry,
      InventorySummary summary,
      Function<ItemStack, Integer> modifier,
      int newTypeLimit,
      CallbackInfoReturnable<Pair<Integer, List<List<BigItemStack>>>> cir
   ) {
      if (entry instanceof JeiMaterialCraftableBigItemStack compat) {
         try {
            cir.setReturnValue(MaterialAllocation.calculate((StockKeeperRequestScreen)this, compat, summary, modifier, newTypeLimit, false));
         } catch (Throwable var8) {
            CompatRecipeTransfer.reportFailure(var8);
            cir.setReturnValue(Pair.of(0, List.of()));
         }
      }
   }
}
