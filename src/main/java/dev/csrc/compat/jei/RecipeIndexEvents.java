package dev.csrc.compat.jei;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.level.LevelEvent.Unload;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "create_stock_recipe_compat",
   value = {Dist.CLIENT}
)
public final class RecipeIndexEvents {
   @SubscribeEvent
   public static void recipes(RecipesUpdatedEvent event) {
      RecursiveMaterials.invalidateIndex();
   }

   @SubscribeEvent
   public static void tags(TagsUpdatedEvent event) {
      RecursiveMaterials.invalidateIndex();
   }

   @SubscribeEvent
   public static void logout(LoggingOut event) {
      RecursiveMaterials.invalidateIndex();
      LegacyJeiButtons.clear();
   }

   @SubscribeEvent
   public static void unload(Unload event) {
      if (event.getLevel().isClientSide()) {
         RecursiveMaterials.invalidateIndex();
         LegacyJeiButtons.clear();
      }
   }
}
