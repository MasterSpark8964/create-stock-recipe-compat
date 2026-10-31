package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

final class PendingReservations {
   static Function<ItemStack, Integer> of(List<BigItemStack> items) {
      return stack -> {
         long total = 0L;

         for (BigItemStack item : items) {
            if (ItemHandlerHelper.canItemStacksStack(item.stack, stack)) {
               total += (long)Math.max(0, item.count);
            }
         }

         return -((int)Math.min(total, 2147483647L));
      };
   }

   private PendingReservations() {
   }
}
