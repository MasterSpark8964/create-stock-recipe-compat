package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.csrc.config.ClientConfig;
import dev.csrc.mixin.SmithingTransformIngredientsAccessor;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.items.ItemHandlerHelper;

public final class RecursiveMaterials {
   private static final GenerationCache<ClientLevel, RecursiveMaterials.IndexData> INDEX_CACHE = new GenerationCache<>();
   private GenerationCache.Token<ClientLevel> indexToken;
   private final IdentityHashMap<Ingredient, RecursiveMaterials.IngredientInfo> ingredientMetadata = new IdentityHashMap<>();
   private final List<BigItemStack> stock = new ArrayList<>();
   private final CandidateIndex<Item, Integer> stockIndex = new CandidateIndex<>();
   private final CandidateIndex<Item, RecursiveMaterials.Production> productionIndex = new CandidateIndex<>();
   private final CandidateIndex<Item, SmithingRecipe> smithingIndex = new CandidateIndex<>();
   private final IdentityHashMap<SmithingRecipe, ResourceLocation> smithingIds = new IdentityHashMap<>();
   private final List<RecursiveMaterials.Production> recipes = new ArrayList<>();
   private final List<SmithingRecipe> smithingRecipes = new ArrayList<>();
   private final IdentityHashMap<SmithingRecipe, List<MaterialReachability.Requirement<Item>>> smithingDependencies = new IdentityHashMap<>();
   private MaterialReachability<Item> reachability;
   private final IdentityHashMap<RecursiveMaterials.State, IdentityHashMap<List<Ingredient>, Optional<RecursiveMaterials.State>>> directMemo = new IdentityHashMap<>();
   private int directMemoEntries;
   private final List<ItemStack> oversizedOutputs = new ArrayList<>();
   private final IdentityHashMap<SmithingRecipe, List<Predicate<ItemStack>>> smithingPredicates = new IdentityHashMap<>();
   private final IdentityHashMap<SmithingRecipe, int[]> smithingOrders = new IdentityHashMap<>();
   private final boolean[] existing;
   private final int typeLimit;
   private final int unitLimit;
   private final int depthLimit;
   private final int frameLimit;
   private final long deadline;
   private final long suspendedAtStart;
   private final IdentityHashMap<Predicate<ItemStack>, RecursiveMaterials.Candidates> candidates = new IdentityHashMap<>();
   private final IdentityHashMap<Predicate<ItemStack>, IdentityHashMap<ItemStack, Boolean>> surplusMatches = new IdentityHashMap<>();
   private final IdentityHashMap<Predicate<ItemStack>, Boolean> acceptsEmpty = new IdentityHashMap<>();
   private final IdentityHashMap<SmithingRecipe, ItemStack> smithingPreviews = new IdentityHashMap<>();
   private final IdentityHashMap<Predicate<ItemStack>, Integer> sourceOptions = new IdentityHashMap<>();
   private final DiagnosticTrace trace;
   private final int initialBudget;
   private int reportedOperations;
   private int budget;
   private int frames;
   private boolean hitDepthLimit;
   private boolean hitUnitLimit;
   private boolean hitTypeLimit;
   private final boolean allowUpstream;
   private final ClientLevel level;

   static String memoryStatus() {
      return INDEX_CACHE.describe(
         data -> "indexRecipes="
               + (data.recipes().size() + data.smithing().size())
               + " indexReplayLines="
               + data.replay().size()
               + " ingredientMetadata="
               + data.metadata().size()
      );
   }

   static void invalidateIndex() {
      INDEX_CACHE.invalidate();
   }

   private RecursiveMaterials(
      StockKeeperRequestScreen screen,
      InventorySummary summary,
      Function<ItemStack, Integer> modifier,
      int typeLimit,
      boolean allowUpstream,
      RecipeTransferSnapshot directRoot
   ) {
      this.allowUpstream = allowUpstream;
      this.trace = PlanningExecution.diagnostics();
      this.typeLimit = typeLimit;
      this.unitLimit = (Integer)ClientConfig.MAX_UNITS.get();
      this.depthLimit = (Integer)ClientConfig.RECURSIVE_DEPTH.get();
      this.budget = (Integer)ClientConfig.DEPENDENCY_BUDGET.get();
      this.initialBudget = this.budget;
      if (this.trace != null) {
         this.trace.depthLimit = this.depthLimit;
         this.trace.unitLimit = this.unitLimit;
         this.trace.typeLimit = typeLimit;
         this.trace.operationLimit = this.initialBudget;
      }

      this.frameLimit = (Integer)ClientConfig.DEPENDENCY_FRAMES.get();
      this.suspendedAtStart = PlanningExecution.suspendedNanos();
      this.deadline = System.nanoTime() + (long)((Integer)ClientConfig.PLANNING_MS.get()).intValue() * 1000000L;
      RecursiveMaterials.StockSource source = PlanningExecution.client(
         () -> new RecursiveMaterials.StockSource(
               Minecraft.getInstance().level,
               new ArrayList<>(directRoot == null ? summary.getStacksByCount() : MaterialAllocation.relevantStock(summary, directRoot.ingredients()))
            )
      );
      this.level = source.level();
      List<BigItemStack> availableStock = source.items();
      List<Boolean> occupied = new ArrayList<>();
      if (this.trace != null) {
         this.trace.beginStock();
      }

      PlanningExecution.eachClient(availableStock, item -> {
         long count = (long)summary.getCountOf(item.stack) + (long)modifier.apply(item.stack).intValue();
         if (count > 0L) {
            if (this.stock.size() >= (Integer)ClientConfig.MAX_STOCK.get()) {
               throw new RecursiveMaterials.Limit("dependency_stock_limit");
            } else {
               this.stockIndex.add(item.stack.getItem(), this.stock.size());
               this.stock.add(new BigItemStack(item.stack.copyWithCount(1), (int)Math.min(count, 1000000000L)));
               boolean ordered = screen.itemsToOrder.stream().anyMatch(s -> ItemHandlerHelper.canItemStacksStack(s.stack, item.stack));
               if (this.trace != null) {
                  this.trace.stock(item.stack, (int)Math.min(count, 1000000000L), ordered);
               }

               occupied.add(ordered);
            }
         }
      }, this::spend);
      this.existing = new boolean[this.stock.size()];
      if (this.trace != null) {
         this.trace.stockVariants = this.stock.size();
      }

      for (int i = 0; i < this.existing.length; i++) {
         this.existing[i] = occupied.get(i);
      }

      if (this.level != null && allowUpstream) {
         this.indexToken = INDEX_CACHE.token(this.level, List.of(this.unitLimit, (Serializable)ClientConfig.MAX_RECIPES.get(), this.trace != null));
         RecursiveMaterials.IndexData cached = INDEX_CACHE.get(this.indexToken);
         if (cached != null) {
            this.recipes.addAll(cached.recipes());
            this.smithingRecipes.addAll(cached.smithing());
            this.smithingIds.putAll(cached.ids());
            this.smithingPreviews.putAll(cached.previews());
            this.smithingDependencies.putAll(cached.dependencies());
            this.ingredientMetadata.putAll(cached.metadata());
            this.oversizedOutputs.addAll(cached.oversized());
            cached.predicates()
               .forEach(
                  (recipex, predicates) -> this.smithingPredicates
                        .put(recipex, List.of(predicates.get(0), new RecursiveMaterials.PlainSmithingBase(predicates.get(1), this.trace), predicates.get(2)))
               );
            if (this.trace != null) {
               this.trace.indexCacheHit = true;
               this.trace.excludedRecoloring = cached.recoloring();
               this.trace.cachedRecipes = cached.recipes().size() + cached.smithing().size();
               this.trace.restoreRecipes(cached.replay());
               this.trace.stage("index-cache");
            }
         } else {
            if (this.trace != null) {
               this.trace.stage("index");
            }

            List<Recipe<?>> workstationRecipes = PlanningExecution.client(() -> {
               RecipeManager manager = this.level.getRecipeManager();
               List<Recipe<?>> capturedRecipes = new ArrayList<>();
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.CRAFTING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMITHING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMELTING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.BLASTING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.SMOKING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.CAMPFIRE_COOKING));
               capturedRecipes.addAll(manager.getAllRecipesFor(RecipeType.STONECUTTING));
               return capturedRecipes;
            });
            PlanningExecution.eachClient(
               workstationRecipes,
               recipex -> {
                  if (WorkstationRecipes.supportsRecipe(recipex)) {
                     if (!(recipex instanceof SmithingTrimRecipe) && recipex.getSerializer() != RecipeSerializer.SMITHING_TRIM) {
                        if (recipex.getType() == RecipeType.SMITHING && recipex instanceof SmithingRecipe smithing) {
                           ItemStack previewx = recipex.getResultItem(this.level.registryAccess()).copy();
                           if (!ignoredOutput(previewx)) {
                              this.smithingPreviews.put(smithing, previewx);
                              this.smithingIds.put(smithing, recipex.getId());
                              if (recipex.getClass() == SmithingTransformRecipe.class) {
                                 SmithingTransformIngredientsAccessor access = (SmithingTransformIngredientsAccessor)recipex;
                                 this.smithingPredicates
                                    .put(
                                       smithing,
                                       List.of(
                                          access.csrc$template(),
                                          new RecursiveMaterials.PlainSmithingBase(access.csrc$base(), this.trace),
                                          access.csrc$addition()
                                       )
                                    );
                                 List<Ingredient> inputs = List.of(access.csrc$template(), access.csrc$base(), access.csrc$addition());
                                 this.smithingDependencies.put(smithing, inputs.stream().map(this::requirement).toList());
                                 if (this.trace != null) {
                                    this.trace.recipe(recipex, previewx, inputs, false);
                                 }
                              }

                              if (this.recipes.size() + this.smithingRecipes.size() >= (Integer)ClientConfig.MAX_RECIPES.get()) {
                                 throw new RecursiveMaterials.Limit("dependency_recipe_limit");
                              } else {
                                 if (this.trace != null && recipex.getClass() != SmithingTransformRecipe.class) {
                                    this.trace.recipe(recipex, previewx, List.of(), true);
                                 }

                                 this.smithingRecipes.add(smithing);
                              }
                           }
                        } else {
                           boolean supported = recipex.getType() == RecipeType.CRAFTING && recipex instanceof CraftingRecipe
                              || (
                                    recipex.getType() == RecipeType.SMELTING
                                       || recipex.getType() == RecipeType.BLASTING
                                       || recipex.getType() == RecipeType.SMOKING
                                       || recipex.getType() == RecipeType.CAMPFIRE_COOKING
                                 )
                                 && recipex instanceof AbstractCookingRecipe
                              || recipex.getType() == RecipeType.STONECUTTING && recipex instanceof StonecutterRecipe;
                           if (supported && !recipex.isSpecial()) {
                              ItemStack output = recipex.getResultItem(this.level.registryAccess());
                              if (!output.isEmpty() && !ignoredOutput(output)) {
                                 if (RecoloringPolicy.excluded(recipex, output)) {
                                    if (this.trace != null) {
                                       this.trace.excludedRecoloring++;
                                    }
                                 } else {
                                    List<Ingredient> inputs = recipex.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
                                    if (!inputs.isEmpty()) {
                                       if (inputs.size() > this.unitLimit) {
                                          this.oversizedOutputs.add(output.copy());
                                       } else if (this.recipes.size() + this.smithingRecipes.size() >= (Integer)ClientConfig.MAX_RECIPES.get()) {
                                          throw new RecursiveMaterials.Limit("dependency_recipe_limit");
                                       } else {
                                          this.recipes
                                             .add(
                                                new RecursiveMaterials.Production(
                                                   recipex.getId(),
                                                   output.copy(),
                                                   output.copyWithCount(1),
                                                   inputs,
                                                   new RecursiveMaterials.MaterialVariant(
                                                      output.getItem(), output.getTag() == null ? null : output.getTag().copy()
                                                   ),
                                                   inputs.stream().map(this::requirement).toList()
                                                )
                                             );
                                          if (this.trace != null) {
                                             this.trace.recipe(recipex, output, inputs, false);
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               },
               this::spend
            );
            IdentityHashMap<SmithingRecipe, List<Predicate<ItemStack>>> rawPredicates = new IdentityHashMap<>();
            this.smithingPredicates
               .forEach(
                  (recipex, predicates) -> rawPredicates.put(
                        recipex,
                        predicates.stream()
                           .map(predicate -> predicate instanceof RecursiveMaterials.PlainSmithingBase base ? base.delegate() : predicate)
                           .toList()
                     )
               );
            INDEX_CACHE.put(
               this.indexToken,
               new RecursiveMaterials.IndexData(
                  List.copyOf(this.recipes),
                  List.copyOf(this.smithingRecipes),
                  new IdentityHashMap<>(this.smithingIds),
                  new IdentityHashMap<>(this.smithingPreviews),
                  new IdentityHashMap<>(this.smithingDependencies),
                  rawPredicates,
                  new IdentityHashMap<>(this.ingredientMetadata),
                  List.copyOf(this.oversizedOutputs),
                  this.trace == null ? List.of() : this.trace.recipeLines(),
                  this.trace == null ? 0 : this.trace.excludedRecoloring
               )
            );
         }

         if (this.trace != null) {
            this.trace.indexedProductions = this.recipes.size();
            this.trace.indexedSmithing = this.smithingRecipes.size();
            this.trace.oversizedRecipes = this.oversizedOutputs.size();
            this.trace.stage("index-sort");
         }

         if (this.trace != null) {
            this.trace.stage("reachability");
         }

         List<MaterialReachability.Edge<Item>> edges = new ArrayList<>();

         for (RecursiveMaterials.Production recipe : this.recipes) {
            edges.add(new MaterialReachability.Edge<>(recipe.variant().item(), recipe.dependencies()));
         }

         boolean opaque = false;

         for (SmithingRecipe recipe : this.smithingRecipes) {
            List<MaterialReachability.Requirement<Item>> inputs = this.smithingDependencies.get(recipe);
            ItemStack preview = this.smithingPreviews.get(recipe);
            if (inputs != null && !preview.isEmpty()) {
               edges.add(new MaterialReachability.Edge<>(preview.getItem(), inputs));
            } else {
               opaque = true;
            }
         }

         this.reachability = new MaterialReachability<>(
            this.stock.stream().map(item -> item.stack.getItem()).toList(), edges, this.depthLimit, opaque, this::spend
         );
         if (this.trace != null) {
            this.trace.opaqueReachability = opaque;
            this.trace.stage("index-sort");
         }

         this.recipes
            .sort(
               Comparator.<RecursiveMaterials.Production>comparingInt(p -> this.reachability.recipeDepth(p.dependencies()))
                  .thenComparing(p -> p.id().toString())
            );

         for (RecursiveMaterials.Production recipex : this.recipes) {
            this.productionIndex.add(recipex.output().getItem(), recipex);
         }

         this.smithingRecipes
            .sort(
               Comparator.<SmithingRecipe>comparingInt(
                     recipex -> this.smithingDependencies.containsKey(recipex) ? this.reachability.recipeDepth(this.smithingDependencies.get(recipex)) : 1
                  )
                  .thenComparing(recipex -> this.smithingIds.get(recipex).toString())
            );

         for (SmithingRecipe recipex : this.smithingRecipes) {
            ItemStack preview = this.smithingPreviews.get(recipex);
            if (!preview.isEmpty() && recipex.getClass() == SmithingTransformRecipe.class) {
               this.smithingIndex.add(preview.getItem(), recipex);
            } else {
               this.smithingIndex.addFallback(recipex);
            }
         }
      }
   }

   public static RecipeTransferSnapshot expand(
      RecipeTransferSnapshot root, StockKeeperRequestScreen screen, InventorySummary summary, Function<ItemStack, Integer> modifier, int typeLimit
   ) {
      List<Ingredient> books = root.ingredients().stream().filter(RecursiveMaterials::onlyIgnoredOutputs).toList();
      if (!books.isEmpty()) {
         JeiMaterialCraftableBigItemStack entry = PlanningExecution.client(() -> {
            RecipeTransferSnapshot requiredx = new RecipeTransferSnapshot(root.sourceRecipe(), root.result(), books, 1);
            return new JeiMaterialCraftableBigItemStack(requiredx, new SyntheticJeiRecipe(root.result()));
         });
         if (PlanningExecution.client(() -> (Integer)MaterialAllocation.calculate(screen, entry, summary, modifier, -1, true).getFirst()) == 0) {
            return null;
         }
      }

      RecursiveMaterials planner = new RecursiveMaterials(screen, summary, modifier, typeLimit, true, null);
      if (planner.trace != null) {
         planner.trace.stage("root-candidates");
      }

      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path = RecipeSearchPath.empty();
      if (root.sourceRecipe() instanceof Recipe<?> recipe) {
         path = path.withRecipe(PlanningExecution.client(recipe::getId));
      }

      if (PlanningExecution.client(() -> NstRecipeCompat.recipe(root.sourceRecipe())) != null) {
         throw new JeiRecipeIngredientExtractor.InvalidRecipe("unsupported_workstation");
      } else {
         List<Ingredient> required = root.ingredients();
         int[] forced = new int[required.size()];
         int[] units = new int[required.size()];
         Arrays.fill(forced, -1);
         Arrays.fill(units, 1);

         for (int i = 0; i < required.size(); i++) {
            Ingredient ingredient = required.get(i);
            if (planner.sourceOptions(ingredient) == 0) {
               return null;
            }

            if (planner.trace != null) {
               planner.trace.rootStockOptions = planner.trace.rootStockOptions + planner.stockCandidates(ingredient).length;
               planner.trace.rootProductionOptions = planner.trace.rootProductionOptions + planner.productionCandidates(ingredient).size();
               planner.trace.rootSmithingOptions = planner.trace.rootSmithingOptions + planner.smithingCandidates(ingredient).size();
            }

            if (planner.productionCandidates(ingredient).isEmpty() && planner.smithingCandidates(ingredient).isEmpty()) {
               int[] candidates = planner.stockCandidates(ingredient);
               if (candidates.length == 1) {
                  forced[i] = candidates[0];
               }
            }
         }

         ForcedLeaves.Result requiredOrder = ForcedLeaves.evaluate(
            forced, units, planner.stock.stream().mapToInt(item -> item.count).toArray(), planner.existing, typeLimit
         );
         if (requiredOrder == ForcedLeaves.Result.MISSING) {
            return null;
         } else if (requiredOrder == ForcedLeaves.Result.TOO_MANY_TYPES) {
            throw new RecursiveMaterials.Limit("too_many_types");
         } else {
            List<Ingredient> orderedRoot = new ArrayList<>(root.ingredients());
            orderedRoot.sort(Comparator.comparingInt(planner::sourceOptions));
            if (planner.trace != null) {
               planner.trace.stage("recursive-search");
            }

            RecursiveMaterials.State solved = planner.resolveAll(orderedRoot, 0, 0, path, planner.new State(), state -> state);
            return planner.finish(root, solved);
         }
      }
   }

   static RecipeTransferSnapshot direct(
      RecipeTransferSnapshot root, StockKeeperRequestScreen screen, InventorySummary summary, Function<ItemStack, Integer> modifier, int typeLimit
   ) {
      if (PlanningExecution.client(() -> NstRecipeCompat.recipe(root.sourceRecipe())) != null) {
         return directNst(root, screen, summary, modifier, typeLimit);
      } else {
         RecursiveMaterials planner = new RecursiveMaterials(screen, summary, modifier, typeLimit, false, root);
         if (planner.trace != null) {
            planner.trace.stage("direct-stock");
         }

         RecursiveMaterials.State solved = planner.resolveAll(root.ingredients(), 0, 0, RecipeSearchPath.empty(), planner.new State(), state -> state);
         return planner.finish(root, solved);
      }
   }

   public static RecipeTransferSnapshot directNst(
      RecipeTransferSnapshot root, StockKeeperRequestScreen screen, InventorySummary summary, Function<ItemStack, Integer> modifier, int typeLimit
   ) {
      SmithingRecipe recipe = PlanningExecution.client(() -> NstRecipeCompat.recipe(root.sourceRecipe()));
      if (recipe == null) {
         throw new IllegalArgumentException("Not an NST recipe");
      } else {
         RecursiveMaterials planner = new RecursiveMaterials(screen, summary, modifier, typeLimit, false, root);
         if (planner.trace != null) {
            planner.trace.stage("direct-nst");
         }

         RecursiveMaterials.State solved = planner.resolveNst(
            recipe,
            0,
            RecipeSearchPath.<ResourceLocation, RecursiveMaterials.MaterialVariant>empty().withRecipe(PlanningExecution.client(recipe::getId)),
            planner.new State(),
            item -> true,
            (state, actual) -> state
         );
         return planner.finish(root, solved);
      }
   }

   private RecipeTransferSnapshot finish(RecipeTransferSnapshot root, RecursiveMaterials.State solved) {
      this.flushOperations();
      if (this.trace != null) {
         this.trace.stage("finish");
      }

      if (solved == null) {
         if (this.hitDepthLimit) {
            throw new RecursiveMaterials.Limit("dependency_depth_limit");
         } else if (this.hitUnitLimit) {
            throw new RecursiveMaterials.Limit("too_many_units");
         } else if (this.hitTypeLimit) {
            throw new RecursiveMaterials.Limit("too_many_types");
         } else {
            return null;
         }
      } else {
         return solved.units == 0 ? null : PlanningExecution.client(() -> {
            List<Ingredient> leaves = new ArrayList<>();

            for (int j = 0; j < solved.used.length; j++) {
               if (solved.used[j] != 0) {
                  Ingredient exact = StrictNBTIngredient.of(this.stock.get(j).stack);

                  for (int count = 0; count < solved.used[j]; count++) {
                     leaves.add(exact);
                  }
               }
            }

            if (this.trace != null) {
               this.trace.planned(leaves);
            }

            return new RecipeTransferSnapshot(root.sourceRecipe(), root.result(), leaves, root.outputCount());
         });
      }
   }

   private RecursiveMaterials.State resolveNst(
      SmithingRecipe recipe,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      Predicate<ItemStack> output,
      BiFunction<RecursiveMaterials.State, ItemStack, RecursiveMaterials.State> next
   ) {
      List<NstRecipeCompat.Input> orderedInputs = PlanningExecution.client(() -> NstRecipeCompat.inputs(recipe));
      return this.resolveNstSlots(orderedInputs, 0, new SimpleContainer(8), depth, path, state, (selected, inventory) -> {
         this.spend();
         if (this.level != null && PlanningExecution.client(() -> recipe.matches(inventory, this.level))) {
            ItemStack actual = PlanningExecution.client(() -> recipe.assemble(inventory, this.level.registryAccess()));
            if (!actual.isEmpty() && PlanningExecution.client(() -> output.test(actual))) {
               RecursiveMaterials.State branch = new RecursiveMaterials.State(selected);
               if (actual.getCount() > 1) {
                  branch.surplus.add(new RecursiveMaterials.Surplus(PlanningExecution.client(() -> actual.copyWithCount(1)), actual.getCount() - 1));
               }

               return next.apply(branch, PlanningExecution.client(() -> actual.copyWithCount(1)));
            } else {
               return null;
            }
         } else {
            return null;
         }
      });
   }

   private RecursiveMaterials.State resolveNstSlots(
      List<NstRecipeCompat.Input> inputs,
      int index,
      SimpleContainer inventory,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      BiFunction<RecursiveMaterials.State, SimpleContainer, RecursiveMaterials.State> next
   ) {
      this.spend();
      if (index == inputs.size()) {
         return next.apply(state, inventory);
      } else {
         NstRecipeCompat.Input input = inputs.get(index);
         return input.count() == 0
            ? this.resolveNstSlots(inputs, index + 1, inventory, depth, path, state, next)
            : this.resolve(input.ingredient(), depth, path, state, (first, item) -> {
               if (input.count() > PlanningExecution.<Integer>client(item::getMaxStackSize)) {
                  return null;
               } else {
                  Ingredient exact = (Ingredient)PlanningExecution.client(() -> StrictNBTIngredient.of(item.copyWithCount(1)));
                  List<Ingredient> remaining = Collections.nCopies(input.count() - 1, exact);
                  return this.resolveAll(remaining, 0, depth, path, first, withQuantity -> {
                     SimpleContainer branch = PlanningExecution.client(() -> {
                        SimpleContainer selected = new SimpleContainer(8);

                        for (int slot = 0; slot < 8; slot++) {
                           selected.setItem(slot, inventory.getItem(slot).copy());
                        }

                        selected.setItem(input.position(), item.copyWithCount(input.count()));
                        return selected;
                     });
                     return this.resolveNstSlots(inputs, index + 1, branch, depth, path, withQuantity, next);
                  });
               }
            });
      }
   }

   private RecursiveMaterials.State resolveAll(
      List<Ingredient> inputs,
      int index,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      Function<RecursiveMaterials.State, RecursiveMaterials.State> next
   ) {
      this.spend();
      if (++this.frames > this.frameLimit) {
         this.frames--;
         throw new RecursiveMaterials.Limit("dependency_frame_limit");
      } else {
         try {
            if (index == inputs.size()) {
               return next.apply(state);
            } else {
               if (index == 0 && state.units + inputs.size() <= this.unitLimit) {
                  RecursiveMaterials.State direct = this.reserveDirect(inputs, state);
                  if (direct != null) {
                     RecursiveMaterials.State completed = next.apply(direct);
                     if (completed != null) {
                        return completed;
                     }
                  }
               }

               return index != 0 || this.terminalRequirements(inputs, state)
                  ? this.resolve(
                     (Predicate<ItemStack>)inputs.get(index),
                     depth,
                     path,
                     state,
                     (selected, item) -> this.resolveAll(inputs, index + 1, depth, path, selected, next)
                  )
                  : null;
            }
         } finally {
            this.frames--;
         }
      }
   }

   private boolean terminalRequirements(List<Ingredient> inputs, RecursiveMaterials.State state) {
      if (this.allowUpstream && !state.surplus.stream().anyMatch(extra -> extra.count() > 0)) {
         List<int[]> rows = new ArrayList<>();

         for (Ingredient input : inputs) {
            RecursiveMaterials.Candidates options = this.candidateBundle(input);
            if (options.production().isEmpty() && options.smithing().isEmpty()) {
               rows.add(options.stock());
            }
         }

         if (rows.isEmpty()) {
            return true;
         } else if (state.units + rows.size() > this.unitLimit) {
            this.hitUnitLimit = true;
            return false;
         } else {
            boolean[] used = new boolean[this.stock.size()];

            for (int i = 0; i < used.length; i++) {
               used[i] = this.existing[i] || state.used[i] > 0;
            }

            this.spend();
            TerminalRequirements.Result result = TerminalRequirements.check(
               rows.toArray(int[][]::new), state.available, used, this.typeLimit < 0 ? -1 : this.typeLimit - state.newTypes
            );
            if (result == TerminalRequirements.Result.TYPES) {
               this.hitTypeLimit = true;
            }

            if (result != TerminalRequirements.Result.MISSING && result != TerminalRequirements.Result.TYPES) {
               return true;
            } else {
               if (this.trace != null) {
                  this.trace.terminalPrunes++;
               }

               return false;
            }
         }
      } else {
         return true;
      }
   }

   private RecursiveMaterials.State resolve(
      Predicate<ItemStack> ingredient,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      BiFunction<RecursiveMaterials.State, ItemStack, RecursiveMaterials.State> next
   ) {
      if (this.trace != null && depth > this.trace.maxDepth) {
         this.trace.maxDepth = depth;
      }

      for (int j = 0; j < state.surplus.size(); j++) {
         this.spend();
         RecursiveMaterials.Surplus extra = state.surplus.get(j);
         ItemStack item = extra.item();
         if (extra.count() > 0 && !item.isEmpty() && this.matchesSurplus(ingredient, item)) {
            RecursiveMaterials.State branch = new RecursiveMaterials.State(state);
            branch.surplus.set(j, new RecursiveMaterials.Surplus(item, extra.count() - 1));
            RecursiveMaterials.State result = next.apply(branch, item);
            if (result != null) {
               return result;
            }
         }
      }

      for (int jx : this.stockCandidates(ingredient)) {
         this.spend();
         if (state.available[jx] != 0) {
            if (state.units == this.unitLimit) {
               this.hitUnitLimit = true;
            } else {
               boolean newType = state.used[jx] == 0 && !this.existing[jx];
               if (newType && this.typeLimit >= 0 && state.newTypes >= this.typeLimit) {
                  this.hitTypeLimit = true;
               } else {
                  RecursiveMaterials.State branch = new RecursiveMaterials.State(state);
                  branch.available[jx]--;
                  branch.used[jx]++;
                  branch.units++;
                  if (newType) {
                     branch.newTypes++;
                  }

                  RecursiveMaterials.State result = next.apply(branch, this.stock.get(jx).stack);
                  if (result != null) {
                     return result;
                  }
               }
            }
         }
      }

      if (!this.allowUpstream) {
         return null;
      } else if (this.candidateBundle(ingredient).terminal()) {
         return null;
      } else {
         for (ItemStack output : this.oversizedOutputs) {
            this.spend();
            if (PlanningExecution.client(() -> ingredient.test(output))) {
               this.hitUnitLimit = true;
            }
         }

         List<RecursiveMaterials.Production> productions = this.productionCandidates(ingredient);
         if (depth >= this.depthLimit) {
            this.hitDepthLimit = this.hitDepthLimit
               | (
                  productions.stream().anyMatch(r -> !path.containsRecipe(r.id()) && !path.containsOutput(r.variant()))
                     || this.smithingCandidates(ingredient).stream().anyMatch(r -> !path.containsRecipe(this.smithingIds.get(r)))
               );
            return null;
         } else {
            List<RecursiveMaterials.UpstreamBranch> eligible = new ArrayList<>();

            for (RecursiveMaterials.Production recipe : productions) {
               this.spend();
               if (!path.containsRecipe(recipe.id())) {
                  if (path.containsOutput(recipe.variant())) {
                     if (this.trace != null) {
                        this.trace.conversionCycleRejects++;
                     }
                  } else {
                     eligible.add(new RecursiveMaterials.UpstreamBranch(recipe, null, this.reachability.recipeDepth(recipe.dependencies())));
                  }
               }
            }

            if (this.level != null) {
               for (SmithingRecipe recipex : this.smithingCandidates(ingredient)) {
                  if (!path.containsRecipe(this.smithingIds.get(recipex))) {
                     List<MaterialReachability.Requirement<Item>> dependencies = this.smithingDependencies.get(recipex);
                     eligible.add(new RecursiveMaterials.UpstreamBranch(null, recipex, dependencies == null ? 1 : this.reachability.recipeDepth(dependencies)));
                  }
               }
            }

            eligible.sort(Comparator.comparingInt(RecursiveMaterials.UpstreamBranch::depth));
            return ProductionSearch.directFirst(
               eligible,
               choice -> {
                  RecursiveMaterials.Production recipexx = choice.production();
                  if (recipexx == null) {
                     return null;
                  } else {
                     this.spend();
                     if (state.units + recipexx.inputs().size() > this.unitLimit) {
                        return null;
                     } else {
                        RecursiveMaterials.State direct = this.reserveDirect(recipexx.inputs(), state);
                        return direct == null ? null : this.completeProduction(recipexx, direct, next);
                     }
                  }
               },
               choice -> {
                  this.spend();
                  if (choice.production() == null) {
                     SmithingRecipe recipexx = choice.smithing();
                     if (this.trace != null) {
                        this.trace.smithingBranches++;
                        this.trace.lastRecipe = this.smithingIds.get(recipexx).toString();
                     }

                     List<Predicate<ItemStack>> predicates = this.smithingPredicates
                        .computeIfAbsent(recipexx, r -> List.of(r::isTemplateIngredient, new RecursiveMaterials.PlainSmithingBase(r::isBaseIngredient, this.trace), r::isAdditionIngredient));
                     return this.resolveSmithingSlots(
                        recipexx,
                        predicates,
                        this.smithingOrder(recipexx, predicates),
                        0,
                        new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY},
                        depth + 1,
                        path.withRecipe(this.smithingIds.get(recipexx)),
                        state,
                        ingredient,
                        this.level,
                        next
                     );
                  } else {
                     RecursiveMaterials.Production recipexx = choice.production();
                     if (this.trace != null) {
                        this.trace.productionBranches++;
                        this.trace.lastRecipe = recipexx.id().toString();
                     }

                     RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> childPath = path.withProduction(recipexx.id(), recipexx.variant());
                     return this.resolveAll(recipexx.inputs(), 0, depth + 1, childPath, state, produced -> this.completeProduction(recipexx, produced, next));
                  }
               }
            );
         }
      }
   }

   private RecursiveMaterials.State completeProduction(
      RecursiveMaterials.Production recipe, RecursiveMaterials.State produced, BiFunction<RecursiveMaterials.State, ItemStack, RecursiveMaterials.State> next
   ) {
      RecursiveMaterials.State branch = new RecursiveMaterials.State(produced);
      if (recipe.output().getCount() > 1) {
         branch.surplus.add(new RecursiveMaterials.Surplus(recipe.unitOutput(), recipe.output().getCount() - 1));
      }

      return next.apply(branch, recipe.unitOutput());
   }

   private int[] smithingOrder(SmithingRecipe recipe, List<Predicate<ItemStack>> predicates) {
      return this.smithingOrders.computeIfAbsent(recipe, ignored -> {
         int[] order = new int[]{0, 1, 2};
         if (recipe.getClass() == SmithingTransformRecipe.class) {
            int[] options = new int[3];

            for (int i = 0; i < 3; i++) {
               options[i] = this.sourceOptions(predicates.get(i));
            }

            for (int i = 1; i < 3; i++) {
               int slot = order[i];

               int j;
               for (j = i; j > 0 && options[order[j - 1]] > options[slot]; j--) {
                  order[j] = order[j - 1];
               }

               order[j] = slot;
            }
         }

         return order;
      });
   }

   private RecursiveMaterials.State resolveSmithingSlots(
      SmithingRecipe recipe,
      List<Predicate<ItemStack>> predicates,
      int[] order,
      int index,
      ItemStack[] selected,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      Predicate<ItemStack> requested,
      Level level,
      BiFunction<RecursiveMaterials.State, ItemStack, RecursiveMaterials.State> next
   ) {
      if (index == order.length) {
         this.spend();
         if (selected[0].isEmpty() && selected[1].isEmpty() && selected[2].isEmpty()) {
            return null;
         } else {
            RecursiveMaterials.Assembled actual = PlanningExecution.client(
               () -> {
                  SimpleContainer inventory = new SimpleContainer(new ItemStack[]{selected[0].copy(), selected[1].copy(), selected[2].copy()});
                  if (!recipe.matches(inventory, level)) {
                     return null;
                  } else {
                     ItemStack output = recipe.assemble(inventory, level.registryAccess());
                     return !output.isEmpty() && !UpstreamPolicy.excluded(output) && requested.test(output)
                        ? new RecursiveMaterials.Assembled(output.copyWithCount(1), output.getCount())
                        : null;
                  }
               }
            );
            if (actual == null) {
               return null;
            } else {
               RecursiveMaterials.State branch = new RecursiveMaterials.State(state);
               if (actual.count() > 1) {
                  branch.surplus.add(new RecursiveMaterials.Surplus(actual.unit(), actual.count() - 1));
               }

               return next.apply(branch, actual.unit());
            }
         }
      } else {
         int slot = order[index];
         return this.smithingSlot(predicates.get(slot), depth, path, state, (withItem, item) -> {
            ItemStack[] branchx = (ItemStack[])selected.clone();
            branchx[slot] = item;
            return this.resolveSmithingSlots(recipe, predicates, order, index + 1, branchx, depth, path, withItem, requested, level, next);
         });
      }
   }

   private RecursiveMaterials.State smithingSlot(
      Predicate<ItemStack> accepts,
      int depth,
      RecipeSearchPath<ResourceLocation, RecursiveMaterials.MaterialVariant> path,
      RecursiveMaterials.State state,
      BiFunction<RecursiveMaterials.State, ItemStack, RecursiveMaterials.State> next
   ) {
      this.spend();
      if (this.acceptsEmpty.computeIfAbsent(accepts, key -> PlanningExecution.client(() -> key.test(ItemStack.EMPTY)))) {
         RecursiveMaterials.State result = next.apply(state, ItemStack.EMPTY);
         if (result != null) {
            return result;
         }
      }

      return this.resolve(accepts, depth, path, state, next);
   }

   private int sourceOptions(Predicate<ItemStack> ingredient) {
      return this.sourceOptions
         .computeIfAbsent(
            ingredient,
            ignored -> this.stockCandidates(ingredient).length + this.productionCandidates(ingredient).size() + this.smithingCandidates(ingredient).size()
         );
   }

   private int[] stockCandidates(Predicate<ItemStack> ingredient) {
      return this.candidateBundle(ingredient).stock();
   }

   private List<RecursiveMaterials.Production> productionCandidates(Predicate<ItemStack> ingredient) {
      return this.candidateBundle(ingredient).production();
   }

   private List<SmithingRecipe> smithingCandidates(Predicate<ItemStack> ingredient) {
      return this.candidateBundle(ingredient).smithing();
   }

   private RecursiveMaterials.Candidates candidateBundle(Predicate<ItemStack> predicate) {
      return this.candidates
         .computeIfAbsent(
            predicate,
            key -> {
               RecursiveMaterials.IngredientInfo info = (key instanceof RecursiveMaterials.PlainSmithingBase base ? base.delegate() : key) instanceof Ingredient ingredient
                  ? this.ingredientMetadata.computeIfAbsent(ingredient, i -> PlanningExecution.client(() -> describe(i)))
                  : new RecursiveMaterials.IngredientInfo(null, false);
               List<Item> keys = info.items();
               Builder stockFound = IntStream.builder();
               List<RecursiveMaterials.Production> productionFound = new ArrayList<>();
               List<SmithingRecipe> preferred = new ArrayList<>();
               List<SmithingRecipe> other = new ArrayList<>();
               List<Runnable> checks = new ArrayList<>();

               for (int j : keys == null ? IntStream.range(0, this.stock.size()).boxed().toList() : this.stockIndex.select(keys)) {
                  checks.add(() -> {
                     if (key.test(this.stock.get(j).stack)) {
                        stockFound.add(j);
                     }
                  });
               }

               if (this.allowUpstream && !info.terminal()) {
                  for (RecursiveMaterials.Production recipe : keys == null ? this.recipes : this.productionIndex.select(keys)) {
                     checks.add(() -> {
                        if (this.reachability.recipeDepth(recipe.dependencies()) == 1000000) {
                           if (this.trace != null) {
                              this.trace.unreachableRejects++;
                           }
                        } else if (key.test(recipe.output())) {
                           productionFound.add(recipe);
                        }
                     });
                  }

                  for (SmithingRecipe recipe : keys == null ? this.smithingRecipes : this.smithingIndex.select(keys)) {
                     checks.add(() -> {
                        List<MaterialReachability.Requirement<Item>> dependencies = this.smithingDependencies.get(recipe);
                        if (dependencies != null && this.reachability.recipeDepth(dependencies) == 1000000) {
                           if (this.trace != null) {
                              this.trace.unreachableRejects++;
                           }
                        } else {
                           ItemStack preview = this.smithingPreviews.get(recipe);
                           boolean sameItem = !preview.isEmpty() && keys != null && keys.contains(preview.getItem());
                           if (!sameItem && (preview.isEmpty() || !key.test(preview))) {
                              other.add(recipe);
                           } else {
                              preferred.add(recipe);
                           }
                        }
                     });
                  }
               }

               PlanningExecution.eachClient(checks, Runnable::run, this::spend);
               preferred.addAll(other);
               return new RecursiveMaterials.Candidates(stockFound.build().toArray(), List.copyOf(productionFound), List.copyOf(preferred), info.terminal());
            }
         );
   }

   private boolean matchesSurplus(Predicate<ItemStack> ingredient, ItemStack item) {
      return this.surplusMatches
         .computeIfAbsent(ingredient, ignored -> new IdentityHashMap<>())
         .computeIfAbsent(item, ignored -> PlanningExecution.client(() -> ingredient.test(item)));
   }

   private MaterialReachability.Requirement<Item> requirement(Ingredient input) {
      RecursiveMaterials.IngredientInfo info = this.ingredientMetadata.computeIfAbsent(input, RecursiveMaterials::describe);
      return new MaterialReachability.Requirement<>(info.items() == null ? List.of() : info.items(), info.items() == null || input.test(ItemStack.EMPTY));
   }

   private static RecursiveMaterials.IngredientInfo describe(Ingredient ingredient) {
      if (!hasKnownItemSet(ingredient, 0)) {
         return new RecursiveMaterials.IngredientInfo(null, false);
      } else {
         Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
         List<Item> items = new ArrayList<>();
         ItemStack[] alternatives = ingredient.getItems();
         boolean terminal = alternatives.length > 0;

         for (ItemStack stack : alternatives) {
            if (seen.add(stack.getItem())) {
               items.add(stack.getItem());
            }

            terminal &= UpstreamPolicy.excluded(stack);
         }

         return new RecursiveMaterials.IngredientInfo(List.copyOf(items), terminal);
      }
   }

   private static boolean hasKnownItemSet(Ingredient ingredient, int depth) {
      if (depth > 16) {
         return false;
      } else {
         return ingredient.getClass() != Ingredient.class && ingredient.getClass() != StrictNBTIngredient.class
            ? ingredient.getClass() == CompoundIngredient.class
               && ((CompoundIngredient)ingredient).getChildren().stream().allMatch(i -> hasKnownItemSet(i, depth + 1))
            : true;
      }
   }

   private static boolean ignoredOutput(ItemStack stack) {
      return PlanningExecution.client(() -> UpstreamPolicy.excluded(stack));
   }

   private static boolean onlyIgnoredOutputs(Predicate<ItemStack> predicate) {
      return predicate instanceof Ingredient ingredient ? PlanningExecution.client(() -> {
         if (!hasKnownItemSet(ingredient, 0)) {
            return false;
         } else {
            ItemStack[] alternatives = ingredient.getItems();
            return alternatives.length > 0 && Arrays.stream(alternatives).allMatch(UpstreamPolicy::excluded);
         }
      }) : false;
   }

   private RecursiveMaterials.State reserveDirect(List<Ingredient> inputs, RecursiveMaterials.State state) {
      IdentityHashMap<List<Ingredient>, Optional<RecursiveMaterials.State>> entries = this.directMemo.get(state);
      if (entries != null && entries.containsKey(inputs)) {
         if (this.trace != null) {
            this.trace.directMemoHits++;
         }

         return entries.get(inputs).orElse(null);
      } else {
         RecursiveMaterials.State result = this.reserveDirectUncached(inputs, state);
         if (this.directMemoEntries >= 4096) {
            this.directMemo.clear();
            this.directMemoEntries = 0;
         }

         this.directMemo.computeIfAbsent(state, ignored -> new IdentityHashMap<>()).put(inputs, Optional.ofNullable(result));
         this.directMemoEntries++;
         return result;
      }
   }

   private RecursiveMaterials.State reserveDirectUncached(List<Ingredient> inputs, RecursiveMaterials.State state) {
      if (this.trace != null) {
         this.trace.directAttempts++;
      }

      int[][] candidates = inputs.stream().map(this::stockCandidates).toArray(int[][]::new);

      for (int[] row : candidates) {
         if (row.length == 0) {
            return null;
         }
      }

      BitSet relevant = new BitSet(this.stock.size());
      IdentityHashMap<int[], Boolean> distinctRows = new IdentityHashMap<>();

      for (int[] rowx : candidates) {
         if (distinctRows.put(rowx, Boolean.TRUE) == null) {
            boolean available = false;

            for (int j : rowx) {
               this.spend();
               if (state.available[j] > 0) {
                  relevant.set(j);
                  available = true;
               }
            }

            if (!available) {
               return null;
            }
         }
      }

      int[] toGlobal = relevant.stream().toArray();
      int[] toLocal = new int[this.stock.size()];
      Arrays.fill(toLocal, -1);
      int[] capacities = new int[toGlobal.length];
      boolean[] usedTypes = new boolean[toGlobal.length];
      long total = 0L;

      for (int jx = 0; jx < toGlobal.length; jx++) {
         int global = toGlobal[jx];
         toLocal[global] = jx;
         capacities[jx] = state.available[global];
         total += (long)capacities[jx];
         usedTypes[jx] = this.existing[global] || state.used[global] > 0;
      }

      if (total < (long)inputs.size()) {
         return null;
      } else {
         IdentityHashMap<int[], int[]> remapped = new IdentityHashMap<>();
         int[][] compact = new int[candidates.length][];

         for (int i = 0; i < candidates.length; i++) {
            compact[i] = remapped.computeIfAbsent(candidates[i], rowxx -> {
               Builder indices = IntStream.builder();

               for (int globalx : rowxx) {
                  this.spend();
                  if (toLocal[globalx] >= 0) {
                     indices.add(toLocal[globalx]);
                  }
               }

               return indices.build().toArray();
            });
         }

         int[][] plan;
         try {
            plan = new CapacityMatcher(compact, capacities, usedTypes, this.typeLimit < 0 ? -1 : this.typeLimit - state.newTypes).match(1);
         } catch (CapacityMatcher.SearchLimit var22) {
            return null;
         }

         this.spend();
         if (plan == null) {
            return null;
         } else {
            RecursiveMaterials.State branch = new RecursiveMaterials.State(state);

            for (int[] rowxx : plan) {
               for (int jx = 0; jx < rowxx.length; jx++) {
                  if (rowxx[jx] != 0) {
                     int global = toGlobal[jx];
                     if (branch.used[global] == 0 && !this.existing[global]) {
                        branch.newTypes++;
                     }

                     branch.available[global] = branch.available[global] - rowxx[jx];
                     branch.used[global] = branch.used[global] + rowxx[jx];
                     branch.units = branch.units + rowxx[jx];
                  }
               }
            }

            return branch;
         }
      }
   }

   private void spend() {
      if (this.indexToken != null && !INDEX_CACHE.current(this.indexToken)) {
         throw new CancellationException("Recipe index reloaded");
      } else {
         this.budget--;
         if ((this.budget & 1023) == 0 || this.budget < 0) {
            this.flushOperations();
         }

         if (this.budget < 0) {
            throw new RecursiveMaterials.Limit("dependency_limit");
         } else if (System.nanoTime() - this.deadline - (PlanningExecution.suspendedNanos() - this.suspendedAtStart) >= 0L) {
            this.flushOperations();
            throw new RecursiveMaterials.Limit("dependency_time_limit");
         }
      }
   }

   private void flushOperations() {
      int total = this.initialBudget - this.budget;
      if (this.trace != null) {
         this.trace.operations = this.trace.operations + (long)(total - this.reportedOperations);
      }

      this.reportedOperations = total;
   }

   private static record Assembled(ItemStack unit, int count) {
   }

   private static record Candidates(int[] stock, List<RecursiveMaterials.Production> production, List<SmithingRecipe> smithing, boolean terminal) {
   }

   private static record IndexData(
      List<RecursiveMaterials.Production> recipes,
      List<SmithingRecipe> smithing,
      IdentityHashMap<SmithingRecipe, ResourceLocation> ids,
      IdentityHashMap<SmithingRecipe, ItemStack> previews,
      IdentityHashMap<SmithingRecipe, List<MaterialReachability.Requirement<Item>>> dependencies,
      IdentityHashMap<SmithingRecipe, List<Predicate<ItemStack>>> predicates,
      IdentityHashMap<Ingredient, RecursiveMaterials.IngredientInfo> metadata,
      List<ItemStack> oversized,
      List<String> replay,
      int recoloring
   ) {
   }

   private static record IngredientInfo(List<Item> items, boolean terminal) {
   }

   public static final class Limit extends RuntimeException {
      final String key;

      Limit(String key) {
         super(key);
         this.key = key;
      }
   }

   private static record MaterialVariant(Item item, CompoundTag tag) {
   }

   private static record PlainSmithingBase(Predicate<ItemStack> delegate, DiagnosticTrace trace) implements Predicate<ItemStack> {
      public boolean test(ItemStack item) {
         if (!this.delegate.test(item)) {
            return false;
         } else if (!UpstreamPolicy.excluded(item)) {
            return true;
         } else {
            if (this.trace != null) {
               this.trace.decoratedBaseRejects++;
            }

            return false;
         }
      }
   }

   private static record Production(
      ResourceLocation id,
      ItemStack output,
      ItemStack unitOutput,
      List<Ingredient> inputs,
      RecursiveMaterials.MaterialVariant variant,
      List<MaterialReachability.Requirement<Item>> dependencies
   ) {
   }

   private final class State {
      final int[] available;
      final int[] used;
      final List<RecursiveMaterials.Surplus> surplus;
      int units;
      int newTypes;

      State() {
         this.available = RecursiveMaterials.this.stock.stream().mapToInt(s -> s.count).toArray();
         this.used = new int[RecursiveMaterials.this.stock.size()];
         this.surplus = new ArrayList<>();
      }

      State(RecursiveMaterials.State previous) {
         this.available = (int[])previous.available.clone();
         this.used = (int[])previous.used.clone();
         this.surplus = new ArrayList<>();
         this.surplus.addAll(previous.surplus);
         this.units = previous.units;
         this.newTypes = previous.newTypes;
      }
   }

   private static record StockSource(ClientLevel level, List<BigItemStack> items) {
   }

   private static record Surplus(ItemStack item, int count) {
   }

   private static record UpstreamBranch(RecursiveMaterials.Production production, SmithingRecipe smithing, int depth) {
   }
}
