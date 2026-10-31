package dev.csrc.compat.jei;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

final class RecoloringPolicy {
   private static final Set<String> COLORS = Set.of(
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   );
   private static final Set<String> FAMILIES = Set.of(
      "wool", "carpet", "terracotta", "glass", "glass_pane", "concrete_powder", "bed", "banner", "shulker_box", "candle"
   );
   private static final Set<String> PLAIN_FAMILIES = Set.of("terracotta", "glass", "glass_pane", "shulker_box", "candle");

   static boolean excluded(Recipe<?> recipe, ItemStack output) {
      String outputFamily = family(output);
      if (outputFamily == null) {
         return false;
      } else {
         boolean dye = false;
         boolean source = false;

         for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] alternatives = ingredient.getItems();
            if (alternatives.length != 0) {
               boolean allDyes = true;

               for (ItemStack item : alternatives) {
                  allDyes &= item.getItem() instanceof DyeItem;
                  source |= outputFamily.equals(family(item));
               }

               dye |= allDyes;
            }
         }

         return excluded(dye, source);
      }
   }

   static boolean excluded(boolean hasDyeInput, boolean hasSameFamilyInput) {
      return hasDyeInput && hasSameFamilyInput;
   }

   private static String family(ItemStack item) {
      if (item.is(ItemTags.WOOL)) {
         return "wool";
      } else if (item.is(ItemTags.WOOL_CARPETS)) {
         return "carpet";
      } else {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
         return family(id.getNamespace(), id.getPath());
      }
   }

   static String family(String namespace, String path) {
      if (!"minecraft".equals(namespace)) {
         return null;
      } else if (PLAIN_FAMILIES.contains(path)) {
         return path;
      } else {
         for (String color : COLORS) {
            if (path.startsWith(color + "_")) {
               String suffix = path.substring(color.length() + 1);
               if (suffix.equals("stained_glass")) {
                  suffix = "glass";
               }

               if (suffix.equals("stained_glass_pane")) {
                  suffix = "glass_pane";
               }

               if (FAMILIES.contains(suffix)) {
                  return suffix;
               }
            }
         }

         return null;
      }
   }

   private RecoloringPolicy() {
   }
}
