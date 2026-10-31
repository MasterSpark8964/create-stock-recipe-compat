package dev.csrc.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import dev.csrc.CreateStockRecipeCompat;
import dev.csrc.config.ClientConfig;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.items.ItemHandlerHelper;

public final class CompatRecipeTransfer {
    private static long lastFailureLog;
    private record PreviewFailure(java.lang.ref.WeakReference<StockKeeperRequestMenu> menu,
                                  java.lang.ref.WeakReference<Object> source, String key, long expires) {}
    private static PreviewFailure previewFailure;

    public static boolean shouldHandle(Object source, IRecipeSlotsView slots) {
        if (!ClientConfig.ENABLE.get()) return false;
        if (source instanceof CraftingRecipe && !ClientConfig.FORCE_CRAFTING.get()) return false;
        // Also intercept invalid inputs so Create cannot fall through to a misleading partial order.
        return source instanceof Recipe<?> || ClientConfig.SYNTHETIC.get();
    }

    /** Preserve normal Create crafting unless a real material shortage requires expansion. */
    public static boolean needsRecursiveCrafting(StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots) {
        if (!ClientConfig.ENABLE.get() || !ClientConfig.RECURSIVE_MATERIALS.get()
                || !(source instanceof CraftingRecipe) || !(menu.screenReference instanceof StockKeeperRequestScreen screen))
            return false;
        var summary = menu.contentHolder.getLastClientsideStockSnapshotAsSummary();
        if (summary == null) return false;
        try {
            var snapshot = JeiRecipeIngredientExtractor.extract(source, slots);
            var entry = new JeiMaterialCraftableBigItemStack(snapshot, new SyntheticJeiRecipe(snapshot.result()));
            return MaterialAllocation.calculate(screen, entry, summary, stack -> -screen.itemsToOrder.stream()
                .filter(s -> ItemHandlerHelper.canItemStacksStack(s.stack, stack)).mapToInt(s -> s.count).sum(), -1, true)
                .getFirst() == 0;
        } catch (JeiRecipeIngredientExtractor.InvalidRecipe invalid) {
            return false;
        }
    }

    public static IRecipeTransferError transfer(StockKeeperRequestMenu menu, Object source,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        // JEI polls previews repeatedly. Throttle only expensive limit failures, never
        // successful orders or stock-shortage results. A committed transfer always recalculates.
        if (!doTransfer && ClientConfig.RECURSIVE_MATERIALS.get() && previewFailure != null
                && previewFailure.menu().get() == menu && previewFailure.source().get() == source
                && System.nanoTime() - previewFailure.expires() < 0)
            return error(previewFailure.key());
        previewFailure = null;
        try {
            if (!(menu.screenReference instanceof StockKeeperRequestScreen screen)) return internalError();
            var snapshot = JeiRecipeIngredientExtractor.extract(source, slots);
            String signature = signature(snapshot.result(), snapshot.ingredients());
            for (CraftableBigItemStack old : screen.recipesToOrder) {
                if (old.recipe == source) return error("already_ordering");
                if (old instanceof JeiMaterialCraftableBigItemStack compat
                        && (compat.getSourceRecipe() == source || compat.signature().equals(signature)))
                    return error("already_ordering");
            }
            Recipe<?> backing = source instanceof Recipe<?> recipe && !(source instanceof CraftingRecipe)
                ? recipe : new SyntheticJeiRecipe(snapshot.result());
            var entry = new JeiMaterialCraftableBigItemStack(snapshot, backing);
            var summary = menu.contentHolder.getLastClientsideStockSnapshotAsSummary();
            if (summary == null) return error("stock_unavailable");
            java.util.function.Function<ItemStack, Integer> reserved = stack -> -screen.itemsToOrder.stream()
                .filter(s -> ItemHandlerHelper.canItemStacksStack(s.stack, stack)).mapToInt(s -> s.count).sum();
            if (screen.itemsToOrder.size() > 9) return error("too_many_types");
            var plan = MaterialAllocation.calculate(screen, entry, summary, reserved,
                9 - screen.itemsToOrder.size(), true);
            if (plan.getFirst() == 0) {
                var unlimited = MaterialAllocation.calculate(screen, entry, summary, reserved, -1, true);
                if (unlimited.getFirst() != 0) return error("too_many_types");
                if (!ClientConfig.RECURSIVE_MATERIALS.get()) return error("missing_stock");
                var expanded = RecursiveMaterials.expand(snapshot, screen, summary, reserved, 9 - screen.itemsToOrder.size());
                if (expanded == null) return error("dependency_missing");
                // Freeze actual leaf materials for Create's add/remove/revalidation logic.
                // A synthetic backing prevents native crafting metadata for intermediate material orders.
                entry = new JeiMaterialCraftableBigItemStack(expanded, new SyntheticJeiRecipe(expanded.result()));
                if (MaterialAllocation.calculate(screen, entry, summary, reserved, 9 - screen.itemsToOrder.size(), true)
                        .getFirst() == 0) return error("dependency_missing");
            }
            if (!doTransfer) return null;

            // Transaction boundary: Create remains responsible for adding/removing material orders.
            List<BigItemStack> before = BigItemStack.duplicateWrappers(screen.itemsToOrder);
            List<CraftableBigItemStack> recipesBefore = new ArrayList<>(screen.recipesToOrder);
            int[] countsBefore = recipesBefore.stream().mapToInt(s -> s.count).toArray();
            boolean committed = false;
            try {
                screen.recipesToOrder.add(entry);
                screen.requestCraftable(entry, maxTransfer ? entry.stack.getMaxStackSize() : 1);
                if (entry.count <= 0 || screen.itemsToOrder.size() > 9) return error("missing_stock");
                screen.searchBox.setValue("");
                screen.refreshSearchNextTick = true;
                if (ClientConfig.DEBUG.get())
                    CreateStockRecipeCompat.LOGGER.info("[CSRC] material request accepted: source={}, itemsToOrder={}",
                        source, screen.itemsToOrder);
                committed = true;
                return null;
            } finally {
                if (!committed) {
                    screen.itemsToOrder = before;
                    screen.recipesToOrder = recipesBefore;
                    for (int i = 0; i < countsBefore.length; i++) recipesBefore.get(i).count = countsBefore[i];
                }
            }
        } catch (JeiRecipeIngredientExtractor.InvalidRecipe invalid) {
            return error(invalid.key);
        } catch (CapacityMatcher.SearchLimit limit) {
            return error("search_limit");
        } catch (RecursiveMaterials.Limit limit) {
            if (!doTransfer && (limit.key.equals("dependency_time_limit") || limit.key.equals("dependency_limit")
                    || limit.key.equals("dependency_frame_limit")))
                previewFailure = new PreviewFailure(new java.lang.ref.WeakReference<>(menu),
                    new java.lang.ref.WeakReference<>(source), limit.key, System.nanoTime() + 1_000_000_000L);
            return error(limit.key);
        } catch (Throwable failure) {
            reportFailure(failure);
            return internalError();
        }
    }

    static String signature(ItemStack output, List<net.minecraft.world.item.crafting.Ingredient> ingredients) {
        var units = ingredients.stream().map(i -> Arrays.stream(i.getItems())
            .map(s -> s.copyWithCount(1).serializeNBT().toString()).sorted().toList().toString()).sorted().toList();
        return output.serializeNBT() + "|" + units;
    }
    public static IRecipeTransferError internalError() {
        return CompatJeiPlugin.helper == null ? () -> IRecipeTransferError.Type.INTERNAL
            : CompatJeiPlugin.helper.createInternalError();
    }
    public static IRecipeTransferError searchLimitError() { return error("search_limit"); }
    private static IRecipeTransferError error(String key) {
        if (ClientConfig.DEBUG.get()) CreateStockRecipeCompat.LOGGER.debug("[CSRC] Transfer rejected: {}", key);
        return CompatJeiPlugin.helper == null ? internalError() : CompatJeiPlugin.helper.createUserErrorWithTooltip(
            Component.translatable(CreateStockRecipeCompat.ID + ".error." + key));
    }
    public static void reportFailure(Throwable failure) {
        long now = System.nanoTime();
        if (now - lastFailureLog > 30_000_000_000L || lastFailureLog == 0) {
            lastFailureLog = now;
            CreateStockRecipeCompat.LOGGER.error("[CSRC] Failed to transfer JEI recipe to Create stock keeper", failure);
        }
    }
    private CompatRecipeTransfer() {}
}
