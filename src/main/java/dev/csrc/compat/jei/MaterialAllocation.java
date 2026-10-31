package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.csrc.config.ClientConfig;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.items.ItemHandlerHelper;

public final class MaterialAllocation {
   public static Pair<Integer, List<List<BigItemStack>>> calculate(
      StockKeeperRequestScreen screen,
      JeiMaterialCraftableBigItemStack entry,
      InventorySummary summary,
      Function<ItemStack, Integer> modifier,
      int limit,
      boolean once
   ) {
      if (summary == null) {
         return Pair.of(0, List.of());
      } else {
         List<Ingredient> ingredients = entry.getIngredients();
         IdentityHashMap<Ingredient, int[]> candidateCache = new IdentityHashMap<>();
         IdentityHashMap<Ingredient, BitSet> matchesByIngredient = new IdentityHashMap<>();
         List<Ingredient> uniqueIngredients = new ArrayList<>();

         for (Ingredient ingredient : ingredients) {
            if (!candidateCache.containsKey(ingredient)) {
               candidateCache.put(ingredient, null);
               matchesByIngredient.put(ingredient, new BitSet());
               uniqueIngredients.add(ingredient);
            }
         }

         List<BigItemStack> stock = new ArrayList<>();
         List<BigItemStack> candidatesByCount = relevantStock(summary, uniqueIngredients);
         List<Ingredient> matching = new ArrayList<>();

         for (BigItemStack stack : candidatesByCount) {
            matching.clear();

            for (Ingredient ingredientx : uniqueIngredients) {
               if (ingredientx.test(stack.stack)) {
                  matching.add(ingredientx);
               }
            }

            if (!matching.isEmpty()) {
               long available = (long)summary.getCountOf(stack.stack) + (long)modifier.apply(stack.stack).intValue();
               if (available > 0L) {
                  if (stock.size() >= (Integer)ClientConfig.MAX_STOCK.get()) {
                     throw new CapacityMatcher.SearchLimit();
                  }

                  int index = stock.size();
                  stock.add(new BigItemStack(stack.stack, (int)Math.min(available, 1000000000L)));

                  for (Ingredient ingredientxx : matching) {
                     matchesByIngredient.get(ingredientxx).set(index);
                  }
               }
            }
         }

         int[] counts = stock.stream().mapToInt(s -> s.count).toArray();
         boolean[] existing = new boolean[stock.size()];

         for (int j = 0; j < stock.size(); j++) {
            ItemStack item = stock.get(j).stack;
            existing[j] = screen.itemsToOrder.stream().anyMatch(s -> ItemHandlerHelper.canItemStacksStack(s.stack, item));
         }

         int[][] candidates = ingredients.stream()
            .map(i -> candidateCache.computeIfAbsent(i, key -> matchesByIngredient.get(key).stream().toArray()))
            .toArray(int[][]::new);
         CapacityMatcher solver = new CapacityMatcher(candidates, counts, existing, limit);
         int output = entry.getOutputCount(null);
         CapacityMatcher.Allocation allocation = solver.maximum(Math.min(once ? 1 : Integer.MAX_VALUE, 1000000000 / output));
         if (allocation.rounds() == 0) {
            return Pair.of(0, List.of());
         } else {
            List<List<BigItemStack>> result = new ArrayList<>();

            for (int[] row : allocation.amounts()) {
               List<BigItemStack> selected = new ArrayList<>();

               for (int j = 0; j < row.length; j++) {
                  if (row[j] > 0) {
                     selected.add(new BigItemStack(stock.get(j).stack, row[j]));
                  }
               }

               result.add(selected);
            }

            return Pair.of(allocation.rounds() * output, result);
         }
      }
   }

   static List<BigItemStack> relevantStock(InventorySummary summary, List<Ingredient> ingredients) {
      for (Ingredient ingredient : ingredients) {
         if (!knownItems(ingredient, 0)) {
            return summary.getStacksByCount();
         }
      }

      Set<BigItemStack> relevant = Collections.newSetFromMap(new IdentityHashMap<>());
      Set<Item> checkedItems = Collections.newSetFromMap(new IdentityHashMap<>());
      Set<Ingredient> checkedIngredients = Collections.newSetFromMap(new IdentityHashMap<>());
      Map<Item, List<BigItemStack>> itemMap = summary.getItemMap();

      for (Ingredient ingredientx : ingredients) {
         if (checkedIngredients.add(ingredientx)) {
            for (ItemStack candidate : ingredientx.getItems()) {
               if (checkedItems.add(candidate.getItem())) {
                  List<BigItemStack> bucket = itemMap.get(candidate.getItem());
                  if (bucket != null) {
                     relevant.addAll(bucket);
                  }
               }
            }
         }
      }

      List<BigItemStack> result = new ArrayList<>(relevant);
      result.sort(Comparator.<BigItemStack>comparingInt(s -> s.count).reversed());
      return result;
   }

   private static boolean knownItems(Ingredient ingredient, int depth) {
      if (depth > 16) {
         return false;
      } else if (ingredient.getClass() == Ingredient.class || ingredient.getClass() == StrictNBTIngredient.class) {
         return true;
      } else {
         return ingredient instanceof CompoundIngredient compound ? compound.getChildren().stream().allMatch(child -> knownItems(child, depth + 1)) : false;
      }
   }

   private MaterialAllocation() {
   }
}
