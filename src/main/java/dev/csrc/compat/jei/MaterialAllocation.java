package dev.csrc.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.BitSet;
import java.util.function.Function;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.ItemHandlerHelper;
import dev.csrc.config.ClientConfig;

/** Supplies only allocation data; Create still mutates orders and sends its original packet. */
public final class MaterialAllocation {
    public static Pair<Integer, List<List<BigItemStack>>> calculate(
            StockKeeperRequestScreen screen, JeiMaterialCraftableBigItemStack entry,
            InventorySummary summary, Function<ItemStack, Integer> modifier, int limit, boolean once) {
        if (summary == null) return Pair.of(0, List.of());
        var ingredients = entry.getIngredients();
        // Count expansion reuses the same Ingredient instance. Match each instance only once.
        var candidateCache = new IdentityHashMap<Ingredient, int[]>();
        var matchesByIngredient = new IdentityHashMap<Ingredient, BitSet>();
        List<Ingredient> uniqueIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (candidateCache.containsKey(ingredient)) continue;
            candidateCache.put(ingredient, null);
            matchesByIngredient.put(ingredient, new BitSet());
            uniqueIngredients.add(ingredient);
        }
        List<BigItemStack> stock = new ArrayList<>();
        List<Ingredient> matching = new ArrayList<>();
        for (BigItemStack stack : summary.getStacksByCount()) {
            matching.clear();
            for (Ingredient ingredient : uniqueIngredients)
                if (ingredient.test(stack.stack)) matching.add(ingredient);
            if (matching.isEmpty()) continue;
            long available = (long) summary.getCountOf(stack.stack) + modifier.apply(stack.stack);
            if (available > 0) {
                if (stock.size() >= ClientConfig.MAX_STOCK.get()) throw new CapacityMatcher.SearchLimit();
                int index = stock.size();
                stock.add(new BigItemStack(stack.stack, (int) Math.min(available, BigItemStack.INF)));
                for (Ingredient ingredient : matching) matchesByIngredient.get(ingredient).set(index);
            }
        }
        int[] counts = stock.stream().mapToInt(s -> s.count).toArray();
        boolean[] existing = new boolean[stock.size()];
        for (int j = 0; j < stock.size(); j++) {
            ItemStack item = stock.get(j).stack;
            existing[j] = screen.itemsToOrder.stream().anyMatch(s -> ItemHandlerHelper.canItemStacksStack(s.stack, item));
        }
        int[][] candidates = ingredients.stream().map(i -> candidateCache.computeIfAbsent(i,
            key -> matchesByIngredient.get(key).stream().toArray()))
            .toArray(int[][]::new);
        var solver = new CapacityMatcher(candidates, counts, existing, limit);
        int output = entry.getOutputCount(null);
        var allocation = solver.maximum(Math.min(once ? 1 : Integer.MAX_VALUE, BigItemStack.INF / output));
        if (allocation.rounds() == 0) return Pair.of(0, List.of());
        List<List<BigItemStack>> result = new ArrayList<>();
        for (int[] row : allocation.amounts()) {
            List<BigItemStack> selected = new ArrayList<>();
            for (int j = 0; j < row.length; j++)
                if (row[j] > 0) selected.add(new BigItemStack(stock.get(j).stack, row[j]));
            result.add(selected);
        }
        return Pair.of(allocation.rounds() * output, result);
    }
    private MaterialAllocation() {}
}
