package dev.csrc.compat.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.BitSet;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.csrc.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.items.ItemHandlerHelper;

/** Per-transfer bounded search. Surplus intermediate products are virtual, never package contents. */
public final class RecursiveMaterials {
    public static final class Limit extends RuntimeException {
        final String key;
        Limit(String key) { super(key); this.key = key; }
    }
    private record Production(ResourceLocation id, ItemStack output, List<Ingredient> inputs) {}
    private final List<BigItemStack> stock = new ArrayList<>();
    private final List<Production> recipes = new ArrayList<>();
    private final List<SmithingRecipe> smithingRecipes = new ArrayList<>();
    private final List<ItemStack> oversizedOutputs = new ArrayList<>();
    private final IdentityHashMap<SmithingRecipe, List<Predicate<ItemStack>>> smithingPredicates = new IdentityHashMap<>();
    private final boolean[] existing;
    private final int typeLimit, unitLimit, depthLimit;
    private final int frameLimit;
    private final long deadline;
    private final IdentityHashMap<Predicate<ItemStack>, int[]> stockCandidates = new IdentityHashMap<>();
    private final IdentityHashMap<Predicate<ItemStack>, List<Production>> productionCandidates = new IdentityHashMap<>();
    private final IdentityHashMap<Predicate<ItemStack>, List<SmithingRecipe>> smithingCandidates = new IdentityHashMap<>();
    private final IdentityHashMap<SmithingRecipe, ItemStack> smithingPreviews = new IdentityHashMap<>();
    private int budget;
    private int frames;
    private boolean hitDepthLimit, hitUnitLimit, hitTypeLimit;

    private final class State {
        final int[] available, used;
        final List<ItemStack> surplus;
        int units, newTypes;
        State() {
            available = stock.stream().mapToInt(s -> s.count).toArray();
            used = new int[stock.size()];
            surplus = new ArrayList<>();
        }
        State(State previous) {
            available = previous.available.clone();
            used = previous.used.clone();
            surplus = new ArrayList<>();
            previous.surplus.forEach(s -> surplus.add(s.copy()));
            units = previous.units;
            newTypes = previous.newTypes;
        }
    }

    private RecursiveMaterials(StockKeeperRequestScreen screen, InventorySummary summary,
            Function<ItemStack, Integer> modifier, int typeLimit) {
        this.typeLimit = typeLimit;
        unitLimit = ClientConfig.MAX_UNITS.get();
        depthLimit = ClientConfig.RECURSIVE_DEPTH.get();
        budget = ClientConfig.DEPENDENCY_BUDGET.get();
        frameLimit = ClientConfig.DEPENDENCY_FRAMES.get();
        deadline = System.nanoTime() + ClientConfig.PLANNING_MS.get() * 1_000_000L;
        for (BigItemStack item : summary.getStacksByCount()) {
            spend();
            long count = (long) summary.getCountOf(item.stack) + modifier.apply(item.stack);
            if (count <= 0) continue;
            if (stock.size() >= ClientConfig.MAX_STOCK.get()) throw new Limit("dependency_stock_limit");
            stock.add(new BigItemStack(item.stack.copyWithCount(1), (int) Math.min(count, BigItemStack.INF)));
        }
        existing = new boolean[stock.size()];
        for (int i = 0; i < stock.size(); i++) {
            ItemStack item = stock.get(i).stack;
            existing[i] = screen.itemsToOrder.stream().anyMatch(s -> ItemHandlerHelper.canItemStacksStack(s.stack, item));
        }
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // Query only vanilla workstation types. A custom machine recipe may inherit
        // CraftingRecipe, but that does not make it a crafting-table recipe.
        var manager = level.getRecipeManager();
        List<Recipe<?>> workstationRecipes = new ArrayList<>();
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.CRAFTING));
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMITHING));
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMELTING));
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.BLASTING));
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMOKING));
        workstationRecipes.addAll(manager.getAllRecipesFor(RecipeType.STONECUTTING));
        for (Recipe<?> recipe : workstationRecipes) {
            spend();
            // Do not even query trim previews: these generate huge unrelated NBT branches.
            if (recipe instanceof SmithingTrimRecipe || recipe.getSerializer() == RecipeSerializer.SMITHING_TRIM)
                continue;
            // Use the public smithing contract, including mod/data-pack implementations.
            // Do not use getIngredients/getResultItem as the authoritative smithing recipe.
            if (recipe.getType() == RecipeType.SMITHING && recipe instanceof SmithingRecipe smithing) {
                ItemStack preview = recipe.getResultItem(level.registryAccess()).copy();
                if (ignoredOutput(preview)) continue;
                smithingPreviews.put(smithing, preview);
                if (recipes.size() + smithingRecipes.size() >= ClientConfig.MAX_RECIPES.get()) throw new Limit("dependency_recipe_limit");
                smithingRecipes.add(smithing);
                continue;
            }
            // These native contracts expose deterministic item results and one item per ingredient slot.
            // Custom processing recipes may hide fluids/probabilities, so are deliberately not inferred.
            boolean supported = recipe.getType() == RecipeType.CRAFTING && recipe instanceof CraftingRecipe
                || (recipe.getType() == RecipeType.SMELTING || recipe.getType() == RecipeType.BLASTING
                    || recipe.getType() == RecipeType.SMOKING) && recipe instanceof AbstractCookingRecipe
                || recipe.getType() == RecipeType.STONECUTTING && recipe instanceof StonecutterRecipe;
            if (!supported || recipe.isSpecial()) continue;
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || ignoredOutput(output)) continue;
            List<Ingredient> inputs = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
            if (inputs.isEmpty()) continue;
            if (inputs.size() > unitLimit) { oversizedOutputs.add(output.copy()); continue; }
            if (recipes.size() + smithingRecipes.size() >= ClientConfig.MAX_RECIPES.get()) throw new Limit("dependency_recipe_limit");
            recipes.add(new Production(recipe.getId(), output.copy(), inputs));
        }
        recipes.sort(Comparator.comparing(p -> p.id().toString()));
        smithingRecipes.sort(Comparator.comparing(p -> p.getId().toString()));
    }

    public static RecipeTransferSnapshot expand(RecipeTransferSnapshot root, StockKeeperRequestScreen screen,
            InventorySummary summary, Function<ItemStack, Integer> modifier, int typeLimit) {
        // Prove a terminal book shortage before collecting the entire recipe index.
        List<Ingredient> books = root.ingredients().stream().filter(RecursiveMaterials::onlyIgnoredOutputs).toList();
        if (!books.isEmpty()) {
            var required = new RecipeTransferSnapshot(root.sourceRecipe(), root.result(), books, 1);
            var entry = new JeiMaterialCraftableBigItemStack(required, new SyntheticJeiRecipe(root.result()));
            if (MaterialAllocation.calculate(screen, entry, summary, modifier, -1, true).getFirst() == 0)
                return null;
        }
        var planner = new RecursiveMaterials(screen, summary, modifier, typeLimit);
        Set<ResourceLocation> path = new HashSet<>();
        if (root.sourceRecipe() instanceof Recipe<?> recipe) path.add(recipe.getId());
        State solved = planner.resolveAll(root.ingredients(), 0, 0, path, planner.new State(), state -> state);
        if (solved == null) {
            // An incomplete search must not be reported as proven material shortage.
            if (planner.hitDepthLimit) throw new Limit("dependency_depth_limit");
            if (planner.hitUnitLimit) throw new Limit("too_many_units");
            if (planner.hitTypeLimit) throw new Limit("too_many_types");
            return null;
        }
        if (solved.units == 0) return null;
        List<Ingredient> leaves = new ArrayList<>();
        for (int j = 0; j < solved.used.length; j++) {
            if (solved.used[j] == 0) continue;
            // Freeze the selected stock variant, including absence/presence of NBT.
            Ingredient exact = StrictNBTIngredient.of(planner.stock.get(j).stack);
            for (int count = 0; count < solved.used[j]; count++) leaves.add(exact);
        }
        return new RecipeTransferSnapshot(root.sourceRecipe(), root.result(), leaves, root.outputCount());
    }

    private State resolveAll(List<Ingredient> inputs, int index, int depth, Set<ResourceLocation> path,
            State state, Function<State, State> next) {
        spend();
        if (++frames > frameLimit) { frames--; throw new Limit("dependency_frame_limit"); }
        try {
            if (index == inputs.size()) return next.apply(state);
            if (index == 0 && state.units + inputs.size() <= unitLimit) {
                State direct = reserveDirect(inputs, state);
                if (direct != null) {
                    State completed = next.apply(direct);
                    if (completed != null) return completed;
                }
            }
            return resolve(inputs.get(index), depth, path, state,
                (selected, item) -> resolveAll(inputs, index + 1, depth, path, selected, next));
        } finally { frames--; }
    }

    private State resolve(Predicate<ItemStack> ingredient, int depth, Set<ResourceLocation> path,
            State state, BiFunction<State, ItemStack, State> next) {
        // Reuse surplus from earlier intermediate batches before producing another one.
        for (int j = 0; j < state.surplus.size(); j++) {
            spend();
            ItemStack item = state.surplus.get(j);
            if (item.isEmpty() || !ingredient.test(item)) continue;
            State branch = new State(state);
            branch.surplus.get(j).shrink(1);
            State result = next.apply(branch, item.copyWithCount(1));
            if (result != null) return result;
        }
        // Reserve real stock globally, with backtracking for overlapping ingredient alternatives.
        for (int j : stockCandidates(ingredient)) {
            spend();
            if (state.available[j] == 0) continue;
            if (state.units == unitLimit) { hitUnitLimit = true; continue; }
            boolean newType = state.used[j] == 0 && !existing[j];
            if (newType && typeLimit >= 0 && state.newTypes >= typeLimit) { hitTypeLimit = true; continue; }
            State branch = new State(state);
            branch.available[j]--;
            branch.used[j]++;
            branch.units++;
            if (newType) branch.newTypes++;
            State result = next.apply(branch, stock.get(j).stack.copyWithCount(1));
            if (result != null) return result;
        }
        // Existing books remain usable stock; missing books are terminal raw materials.
        // Never infer how to produce their combinatorial StoredEnchantments variants.
        if (onlyIgnoredOutputs(ingredient)) return null;
        for (ItemStack output : oversizedOutputs) {
            spend();
            if (ingredient.test(output)) hitUnitLimit = true;
        }
        var productions = productionCandidates(ingredient);
        if (depth >= depthLimit) {
            // A terminal missing ingredient is not itself proof that a deeper recipe exists.
            hitDepthLimit |= productions.stream().anyMatch(r -> !path.contains(r.id()))
                || smithingCandidates(ingredient).stream().anyMatch(r -> !path.contains(r.getId()));
            return null;
        }
        for (Production recipe : productions) {
            spend();
            if (path.contains(recipe.id())) continue;
            Set<ResourceLocation> childPath = new HashSet<>(path);
            childPath.add(recipe.id());
            State result = resolveAll(recipe.inputs(), 0, depth + 1, childPath, state, produced -> {
                State branch = new State(produced);
                if (recipe.output().getCount() > 1)
                    branch.surplus.add(recipe.output().copyWithCount(recipe.output().getCount() - 1));
                return next.apply(branch, recipe.output().copyWithCount(1));
            });
            if (result != null) return result;
        }
        // Prefer recipes whose preview resembles the target, but dynamic/custom previews
        // are not authoritative: nonmatching previews are still considered in the second pass.
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
            for (SmithingRecipe recipe : smithingCandidates(ingredient)) {
                spend();
                if (path.contains(recipe.getId())) continue;
                Set<ResourceLocation> childPath = new HashSet<>(path);
                childPath.add(recipe.getId());
                var predicates = smithingPredicates.computeIfAbsent(recipe,
                    r -> List.of(r::isTemplateIngredient, r::isBaseIngredient, r::isAdditionIngredient));
                State result = smithingSlot(predicates.get(0), depth + 1, childPath, state,
                    (withTemplate, template) -> smithingSlot(predicates.get(1), depth + 1, childPath, withTemplate,
                        (withBase, base) -> smithingSlot(predicates.get(2), depth + 1, childPath, withBase,
                            (withAddition, addition) -> {
                                spend();
                                if (template.isEmpty() && base.isEmpty() && addition.isEmpty()) return null;
                                var inventory = new SimpleContainer(template.copy(), base.copy(), addition.copy());
                                if (!recipe.matches(inventory, level)) return null;
                                ItemStack actual = recipe.assemble(inventory, level.registryAccess());
                                // This checks inherited enchantments, damage, trim and custom NBT
                                // against the actual requested ingredient, not a guessed result stack.
                                if (actual.isEmpty() || ignoredOutput(actual) || !ingredient.test(actual)) return null;
                                State branch = new State(withAddition);
                                if (actual.getCount() > 1) branch.surplus.add(actual.copyWithCount(actual.getCount() - 1));
                                return next.apply(branch, actual.copyWithCount(1));
                            })));
                if (result != null) return result;
            }
        return null;
    }

    private State smithingSlot(Predicate<ItemStack> accepts, int depth, Set<ResourceLocation> path,
            State state, BiFunction<State, ItemStack, State> next) {
        spend();
        // A mod may explicitly permit an empty slot. Never assume that an absent template is allowed.
        if (accepts.test(ItemStack.EMPTY)) {
            State result = next.apply(state, ItemStack.EMPTY);
            if (result != null) return result;
        }
        return resolve(accepts, depth, path, state, next);
    }

    private int[] stockCandidates(Predicate<ItemStack> ingredient) {
        return stockCandidates.computeIfAbsent(ingredient, key -> {
            var indices = java.util.stream.IntStream.builder();
            for (int j = 0; j < stock.size(); j++) {
                spend();
                if (key.test(stock.get(j).stack)) indices.accept(j);
            }
            return indices.build().toArray();
        });
    }

    private List<Production> productionCandidates(Predicate<ItemStack> ingredient) {
        return productionCandidates.computeIfAbsent(ingredient, key -> {
            List<Production> found = new ArrayList<>();
            for (Production recipe : recipes) {
                spend();
                if (key.test(recipe.output())) found.add(recipe);
            }
            return found;
        });
    }

    private List<SmithingRecipe> smithingCandidates(Predicate<ItemStack> ingredient) {
        return smithingCandidates.computeIfAbsent(ingredient, key -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return List.of();
            List<SmithingRecipe> preferred = new ArrayList<>(), other = new ArrayList<>();
            ItemStack[] declared = key instanceof Ingredient required ? required.getItems() : new ItemStack[0];
            boolean knownItemSet = key instanceof Ingredient required && hasKnownItemSet(required, 0);
            for (SmithingRecipe recipe : smithingRecipes) {
                spend();
                ItemStack preview = smithingPreviews.computeIfAbsent(recipe,
                    r -> r.getResultItem(level.registryAccess()).copy());
                boolean sameItem = !preview.isEmpty()
                    && java.util.Arrays.stream(declared).anyMatch(s -> s.is(preview.getItem()));
                // Only the exact transform implementation guarantees an invariant output item.
                // NBT is still checked using assemble; unknown/subclass/dynamic recipes are not pruned.
                if (knownItemSet && declared.length > 0 && recipe.getClass() == SmithingTransformRecipe.class
                        && !preview.isEmpty() && !sameItem) continue;
                if (sameItem || (!preview.isEmpty() && key.test(preview))) preferred.add(recipe);
                else other.add(recipe);
            }
            preferred.addAll(other);
            return List.copyOf(preferred);
        });
    }

    private static boolean hasKnownItemSet(Ingredient ingredient, int depth) {
        if (depth > 16) return false;
        if (ingredient.isVanilla() || ingredient.getClass() == StrictNBTIngredient.class) return true;
        return ingredient.getClass() == CompoundIngredient.class
            && ((CompoundIngredient) ingredient).getChildren().stream().allMatch(i -> hasKnownItemSet(i, depth + 1));
    }

    private static boolean ignoredOutput(ItemStack stack) {
        return stack.getItem() instanceof EnchantedBookItem
            || (stack.hasTag() && stack.getTag().contains("StoredEnchantments"));
    }

    private static boolean onlyIgnoredOutputs(Predicate<ItemStack> predicate) {
        if (!(predicate instanceof Ingredient ingredient) || !hasKnownItemSet(ingredient, 0)) return false;
        ItemStack[] candidates = ingredient.getItems();
        return candidates.length > 0 && java.util.Arrays.stream(candidates).allMatch(RecursiveMaterials::ignoredOutput);
    }

    /** Reserve an entire directly stocked recipe without one Java continuation per input unit. */
    private State reserveDirect(List<Ingredient> inputs, State state) {
        int[][] candidates = inputs.stream().map(this::stockCandidates).toArray(int[][]::new);
        for (int[] row : candidates) if (row.length == 0) return null;
        // Compact columns to available, relevant stock. A small recipe in a large warehouse
        // must not allocate a dense matrix with every unrelated warehouse item as a column.
        BitSet relevant = new BitSet(stock.size());
        var distinctRows = new IdentityHashMap<int[], Boolean>();
        for (int[] row : candidates) {
            if (distinctRows.put(row, Boolean.TRUE) != null) continue;
            boolean available = false;
            for (int j : row) {
                spend();
                if (state.available[j] <= 0) continue;
                relevant.set(j);
                available = true;
            }
            if (!available) return null;
        }
        int[] toGlobal = relevant.stream().toArray();
        int[] toLocal = new int[stock.size()];
        Arrays.fill(toLocal, -1);
        int[] capacities = new int[toGlobal.length];
        boolean[] usedTypes = new boolean[toGlobal.length];
        long total = 0;
        for (int j = 0; j < toGlobal.length; j++) {
            int global = toGlobal[j];
            toLocal[global] = j;
            capacities[j] = state.available[global];
            total += capacities[j];
            usedTypes[j] = existing[global] || state.used[global] > 0;
        }
        if (total < inputs.size()) return null;
        var remapped = new IdentityHashMap<int[], int[]>();
        int[][] compact = new int[candidates.length][];
        for (int i = 0; i < candidates.length; i++) {
            compact[i] = remapped.computeIfAbsent(candidates[i], row -> {
                var indices = java.util.stream.IntStream.builder();
                for (int global : row) {
                    spend();
                    if (toLocal[global] >= 0) indices.add(toLocal[global]);
                }
                return indices.build().toArray();
            });
        }
        int[][] plan;
        try {
            plan = new CapacityMatcher(compact, capacities, usedTypes,
                typeLimit < 0 ? -1 : typeLimit - state.newTypes).match(1);
        } catch (CapacityMatcher.SearchLimit limit) {
            return null; // This shortcut is optional; retain the bounded recursive search.
        }
        spend();
        if (plan == null) return null;
        State branch = new State(state);
        for (int[] row : plan) {
            for (int j = 0; j < row.length; j++) {
                if (row[j] == 0) continue;
                int global = toGlobal[j];
                if (branch.used[global] == 0 && !existing[global]) branch.newTypes++;
                branch.available[global] -= row[j];
                branch.used[global] += row[j];
                branch.units += row[j];
            }
        }
        return branch;
    }

    private void spend() {
        if (--budget < 0) throw new Limit("dependency_limit");
        if (System.nanoTime() - deadline >= 0) throw new Limit("dependency_time_limit");
    }
}
