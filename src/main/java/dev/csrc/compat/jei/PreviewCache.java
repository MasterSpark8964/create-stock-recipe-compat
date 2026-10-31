package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import dev.csrc.config.ClientConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;

public final class PreviewCache {
   private static final IdentityHashMap<Object, PreviewCache.Entry> entries = new IdentityHashMap<>();
   private static Object screenIdentity;
   private static Object stockIdentity;
   private static String orderIdentity;
   private static List<Object> orderedRecipes = List.of();
   private static int[] orderedCounts = new int[0];
   private static int options;

   private static void context(StockKeeperRequestMenu menu) {
      Object summary = ((StockTickerBlockEntity)menu.contentHolder).getLastClientsideStockSnapshotAsSummary();
      StringBuilder order = new StringBuilder();
      List<Object> recipes = new ArrayList<>();
      List<Integer> counts = new ArrayList<>();
      if (menu.screenReference instanceof StockKeeperRequestScreen screen) {
         for (BigItemStack item : screen.itemsToOrder) {
            order.append(item.stack.serializeNBT()).append(':').append(item.count).append(';');
         }

         for (CraftableBigItemStack recipe : screen.recipesToOrder) {
            recipes.add(recipe);
            counts.add(recipe.count);
         }
      }

      String identity = order.toString();
      boolean sameRecipes = recipes.size() == orderedRecipes.size();

      for (int i = 0; sameRecipes && i < recipes.size(); i++) {
         sameRecipes = recipes.get(i) == orderedRecipes.get(i) && counts.get(i) == orderedCounts[i];
      }

      int flags = (ClientConfig.ENABLE.get() ? 1 : 0)
         | (ClientConfig.SYNTHETIC.get() ? 2 : 0)
         | (ClientConfig.ALLOW_NON_ITEM_OUTPUTS.get() ? 4 : 0)
         | (ClientConfig.BATCH_UNITS.get() ? 8 : 0);
      if (screenIdentity != menu.screenReference || stockIdentity != summary || !identity.equals(orderIdentity) || !sameRecipes || flags != options) {
         entries.clear();
         screenIdentity = menu.screenReference;
         stockIdentity = summary;
         orderIdentity = identity;
         options = flags;
         orderedRecipes = recipes;
         orderedCounts = counts.stream().mapToInt(Integer::intValue).toArray();
      }
   }

   public static PreviewCache.Hit get(StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots, boolean maximum) {
      try {
         context(menu);
      } catch (LinkageError | RuntimeException var5) {
         clear();
         CompatRecipeTransfer.reportFailure(var5);
         return null;
      }

      PreviewCache.Entry entry = entries.get(source);
      return entry != null && entry.slots() == slots && entry.maximum() == maximum && System.nanoTime() - entry.until() < 0L ? entry.hit() : null;
   }

   public static void put(StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots, boolean maximum, IRecipeTransferError error) {
      try {
         context(menu);
      } catch (LinkageError | RuntimeException var6) {
         clear();
         CompatRecipeTransfer.reportFailure(var6);
         return;
      }

      if (entries.size() >= 128 && !entries.containsKey(source)) {
         entries.clear();
      }

      entries.put(source, new PreviewCache.Entry(slots, maximum, System.nanoTime() + 500000000L, new PreviewCache.Hit(error)));
   }

   static int size() {
      return entries.size();
   }

   public static void clear() {
      entries.clear();
      screenIdentity = null;
      stockIdentity = null;
      orderIdentity = null;
      orderedRecipes = List.of();
      orderedCounts = new int[0];
   }

   private PreviewCache() {
   }

   private static record Entry(Object slots, boolean maximum, long until, PreviewCache.Hit hit) {
   }

   public static record Hit(IRecipeTransferError error) {
   }
}
