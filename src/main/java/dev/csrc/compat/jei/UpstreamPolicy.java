package dev.csrc.compat.jei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;

final class UpstreamPolicy {
   static boolean excluded(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         CompoundTag tag = stack.getTag();
         return excluded(
            stack.getItem() instanceof EnchantedBookItem,
            tag != null && tag.contains("StoredEnchantments"),
            tag != null && tag.contains("Enchantments"),
            tag != null && tag.contains("Trim"),
            id.getNamespace(),
            id.getPath()
         );
      }
   }

   static boolean excluded(boolean book, boolean stored, boolean enchanted, boolean trim, String namespace, String path) {
      return book || stored || enchanted || trim || "minecraft".equals(namespace) && path.endsWith("_armor_trim_smithing_template");
   }

   private UpstreamPolicy() {
   }
}
