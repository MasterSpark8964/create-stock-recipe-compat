package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import dev.csrc.CreateStockRecipeCompat;
import dev.csrc.config.ClientConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferError.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;

public final class CompatRecipeTransfer {
   private static long lastFailureLog;
   private static String lastErrorKey;

   public static boolean shouldHandle(Object source, IRecipeSlotsView slots) {
      if (!(Boolean)ClientConfig.ENABLE.get()) {
         return false;
      } else if (NstRecipeCompat.recipe(source) != null) {
         return true;
      } else {
         return source instanceof CraftingRecipe ? false : source instanceof Recipe || (Boolean)ClientConfig.SYNTHETIC.get();
      }
   }

   public static IRecipeTransferError transfer(
      StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer
   ) {
      return transfer(menu, source, slots, player, maxTransfer, doTransfer, false);
   }

   public static IRecipeTransferError transferWithDependencies(
      StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots, Player player, boolean maxTransfer
   ) {
      return !WorkstationRecipes.supportsRecipe(source) ? error("unsupported_workstation") : transfer(menu, source, slots, player, maxTransfer, true, true);
   }

   public static Component lastErrorMessage() {
      return lastErrorKey == null
         ? Component.translatable("create_stock_recipe_compat.error.internal")
         : Component.translatable("create_stock_recipe_compat.error." + lastErrorKey);
   }

   // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
   private static IRecipeTransferError transfer(
      StockKeeperRequestMenu menu, Object source, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer, boolean expandMissing
   ) {
      lastErrorKey = null;

      try {
         if (!(menu.screenReference instanceof StockKeeperRequestScreen screen)) {
            return internalError();
         } else {
            for (CraftableBigItemStack old : screen.recipesToOrder) {
               if (sameSource(old.recipe, source) || old instanceof JeiMaterialCraftableBigItemStack compat && sameSource(compat.getSourceRecipe(), source)) {
                  return error("already_ordering");
               }
            }

            RecipeTransferSnapshot snapshot = JeiRecipeIngredientExtractor.extract(source, slots);
            String signature = null;

            for (CraftableBigItemStack oldx : screen.recipesToOrder) {
               if (!hasRecipeIdentity(source) && oldx instanceof JeiMaterialCraftableBigItemStack) {
                  JeiMaterialCraftableBigItemStack compat = (JeiMaterialCraftableBigItemStack)oldx;
                  if (!hasRecipeIdentity(compat.getSourceRecipe())) {
                     if (signature == null) {
                        signature = signature(snapshot.result(), snapshot.ingredients());
                     }

                     if (compat.signature().equals(signature)) {
                        return error("already_ordering");
                     }
                  }
               }
            }

            Object var10000;
            label380: {
               if (source instanceof Recipe<?> recipe && !(source instanceof CraftingRecipe)) {
                  var10000 = recipe;
                  break label380;
               }

               var10000 = new SyntheticJeiRecipe(snapshot.result());
            }

            Recipe<?> backing = (Recipe<?>)var10000;
            JeiMaterialCraftableBigItemStack entry = new JeiMaterialCraftableBigItemStack(snapshot, backing);
            InventorySummary summary = ((StockTickerBlockEntity)menu.contentHolder).getLastClientsideStockSnapshotAsSummary();
            if (summary == null) {
               return error("stock_unavailable");
            } else {
               Function<ItemStack, Integer> reserved = PendingReservations.of(screen.itemsToOrder);
               if (screen.itemsToOrder.size() > 9) {
                  return error("too_many_types");
               } else {
                  boolean nst = NstRecipeCompat.recipe(source) != null;
                  boolean fits;
                  if (nst) {
                     RecipeTransferSnapshot direct = directNstOrNull(snapshot, screen, summary, reserved, 9 - screen.itemsToOrder.size());
                     fits = direct != null;
                     if (fits) {
                        entry = new JeiMaterialCraftableBigItemStack(direct, new SyntheticJeiRecipe(direct.result()));
                     }
                  } else {
                     fits = (Integer)MaterialAllocation.calculate(screen, entry, summary, reserved, 9 - screen.itemsToOrder.size(), true).getFirst() != 0;
                  }

                  if (!fits) {
                     boolean fitsWithoutTypeLimit = nst
                        ? directNstOrNull(snapshot, screen, summary, reserved, -1) != null
                        : (Integer)MaterialAllocation.calculate(screen, entry, summary, reserved, -1, true).getFirst() != 0;
                     if (fitsWithoutTypeLimit) {
                        return error("too_many_types");
                     }

                     if (!expandMissing || !(Boolean)ClientConfig.RECURSIVE_MATERIALS.get()) {
                        return error("missing_stock");
                     }

                     RecipeTransferSnapshot expanded = RecursiveMaterials.expand(snapshot, screen, summary, reserved, 9 - screen.itemsToOrder.size());
                     if (expanded == null) {
                        return error("dependency_missing");
                     }

                     entry = new JeiMaterialCraftableBigItemStack(expanded, new SyntheticJeiRecipe(expanded.result()));
                     if ((Integer)MaterialAllocation.calculate(screen, entry, summary, reserved, 9 - screen.itemsToOrder.size(), true).getFirst() == 0) {
                        return error("dependency_missing");
                     }
                  }

                  if (!doTransfer) {
                     return null;
                  } else {
                     if (!hasRecipeIdentity(source)) {
                        entry.setSourceSignature(signature(snapshot.result(), snapshot.ingredients()));
                     }

                     List<BigItemStack> before = BigItemStack.duplicateWrappers(screen.itemsToOrder);
                     List<CraftableBigItemStack> recipesBefore = new ArrayList<>(screen.recipesToOrder);
                     int[] countsBefore = recipesBefore.stream().mapToInt(s -> s.count).toArray();
                     String searchBefore = screen.searchBox.getValue();
                     boolean refreshBefore = screen.refreshSearchNextTick;
                     boolean committed = false;
                     boolean var31 = false /* VF: Semaphore variable */;

                     IRecipeTransferError var48;
                     label406: {
                        try {
                           var31 = true;
                           screen.recipesToOrder.add(entry);
                           screen.requestCraftable(entry, maxTransfer ? entry.stack.getMaxStackSize() : 1);
                           if (entry.count > 0 && screen.itemsToOrder.size() <= 9) {
                              screen.searchBox.setValue("");
                              screen.refreshSearchNextTick = true;
                              if ((Boolean)ClientConfig.DEBUG.get()) {
                                 CreateStockRecipeCompat.LOGGER
                                    .info("[CSRC] material request accepted: source={}, itemsToOrder={}", source, screen.itemsToOrder);
                              }

                              committed = true;
                              var48 = null;
                              var31 = false;
                              break label406;
                           }

                           var48 = error("missing_stock");
                           var31 = false;
                        } finally {
                           if (var31) {
                              if (!committed) {
                                 screen.itemsToOrder = before;
                                 screen.recipesToOrder = recipesBefore;

                                 for (int i = 0; i < countsBefore.length; i++) {
                                    recipesBefore.get(i).count = countsBefore[i];
                                 }

                                 screen.searchBox.setValue(searchBefore);
                                 screen.refreshSearchNextTick = refreshBefore;
                              }
                           }
                        }

                        if (!committed) {
                           screen.itemsToOrder = before;
                           screen.recipesToOrder = recipesBefore;

                           for (int i = 0; i < countsBefore.length; i++) {
                              recipesBefore.get(i).count = countsBefore[i];
                           }

                           screen.searchBox.setValue(searchBefore);
                           screen.refreshSearchNextTick = refreshBefore;
                        }

                        return var48;
                     }

                     if (!committed) {
                        screen.itemsToOrder = before;
                        screen.recipesToOrder = recipesBefore;

                        for (int i = 0; i < countsBefore.length; i++) {
                           recipesBefore.get(i).count = countsBefore[i];
                        }

                        screen.searchBox.setValue(searchBefore);
                        screen.refreshSearchNextTick = refreshBefore;
                     }

                     return var48;
                  }
               }
            }
         }
      } catch (JeiRecipeIngredientExtractor.InvalidRecipe var33) {
         return error(var33.key);
      } catch (CapacityMatcher.SearchLimit var34) {
         return error("search_limit");
      } catch (RecursiveMaterials.Limit var35) {
         return error(var35.key);
      } catch (Throwable var36) {
         reportFailure(var36);
         lastErrorKey = "internal";
         return internalError();
      }
   }

   static RecipeTransferSnapshot prepare(Object source, IRecipeSlotsView slots) {
      if (!WorkstationRecipes.supportsRecipe(source)) {
         throw new JeiRecipeIngredientExtractor.InvalidRecipe("unsupported_workstation");
      } else {
         return JeiRecipeIngredientExtractor.extract(source, slots);
      }
   }

   static CompatRecipeTransfer.PlanResult plan(StockKeeperRequestScreen screen, RecipeTransferSnapshot root) {
      try {
         DiagnosticTrace trace = PlanningExecution.diagnostics();
         if (trace != null) {
            trace.stage("stock-snapshot");
         }

         InventorySummary summary = PlanningExecution.client(
            () -> ((StockTickerBlockEntity)((StockKeeperRequestMenu)screen.getMenu()).contentHolder).getLastClientsideStockSnapshotAsSummary()
         );
         if (summary == null) {
            return CompatRecipeTransfer.PlanResult.failed("stock_unavailable");
         } else {
            List<BigItemStack> reservations = PlanningExecution.client(() -> BigItemStack.duplicateWrappers(screen.itemsToOrder));
            Function<ItemStack, Integer> reserved = PendingReservations.of(reservations);
            int room = 9 - reservations.size();
            if (room < 0) {
               return CompatRecipeTransfer.PlanResult.failed("too_many_types");
            } else {
               try {
                  if (trace != null) {
                     trace.stage("direct-plan");
                  }

                  RecipeTransferSnapshot direct = RecursiveMaterials.direct(root, screen, summary, reserved, room);
                  if (direct != null) {
                     return new CompatRecipeTransfer.PlanResult(direct, null);
                  }
               } catch (RecursiveMaterials.Limit var8) {
                  if (!var8.key.equals("too_many_types")) {
                     throw var8;
                  }
               }

               if (trace != null) {
                  trace.stage("upstream-plan");
               }

               RecipeTransferSnapshot expanded = RecursiveMaterials.expand(root, screen, summary, reserved, room);
               return expanded == null ? CompatRecipeTransfer.PlanResult.failed("dependency_missing") : new CompatRecipeTransfer.PlanResult(expanded, null);
            }
         }
      } catch (JeiRecipeIngredientExtractor.InvalidRecipe var9) {
         return CompatRecipeTransfer.PlanResult.failed(var9.key);
      } catch (RecursiveMaterials.Limit var10) {
         return CompatRecipeTransfer.PlanResult.failed(var10.key);
      } catch (CapacityMatcher.SearchLimit var11) {
         return CompatRecipeTransfer.PlanResult.failed("search_limit");
      }
   }

   // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
   static String commit(StockKeeperRequestScreen screen, RecipeTransferSnapshot root, RecipeTransferSnapshot planned, boolean maximum) {
      try {
         Object source = root.sourceRecipe();
         SmithingRecipe nstRecipe = NstRecipeCompat.recipe(source);
         if (nstRecipe != null) {
            source = nstRecipe;
         }

         ClientLevel level = Minecraft.getInstance().level;
         if (level == null) {
            return "stock_changed";
         } else {
            if (source instanceof Recipe<?> recipe && level.getRecipeManager().byKey(recipe.getId()).orElse(null) != recipe) {
               return "recipe_changed";
            }

            for (CraftableBigItemStack old : screen.recipesToOrder) {
               if (sameSource(old.recipe, root.sourceRecipe())
                  || old instanceof JeiMaterialCraftableBigItemStack compat && sameSource(compat.getSourceRecipe(), root.sourceRecipe())) {
                  return "already_ordering";
               }
            }

            InventorySummary summary = ((StockTickerBlockEntity)((StockKeeperRequestMenu)screen.getMenu()).contentHolder)
               .getLastClientsideStockSnapshotAsSummary();
            if (summary == null) {
               return "stock_unavailable";
            } else {
               JeiMaterialCraftableBigItemStack entry = new JeiMaterialCraftableBigItemStack(planned, new SyntheticJeiRecipe(planned.result()));
               if (screen.itemsToOrder.size() <= 9
                  && (Integer)MaterialAllocation.calculate(
                           screen, entry, summary, PendingReservations.of(screen.itemsToOrder), 9 - screen.itemsToOrder.size(), true
                        )
                        .getFirst()
                     != 0) {
                  List<BigItemStack> itemsBefore = BigItemStack.duplicateWrappers(screen.itemsToOrder);
                  ArrayList<CraftableBigItemStack> recipesBefore = new ArrayList<>(screen.recipesToOrder);
                  int[] countsBefore = recipesBefore.stream().mapToInt(item -> item.count).toArray();
                  String searchBefore = screen.searchBox.getValue();
                  boolean refreshBefore = screen.refreshSearchNextTick;
                  boolean accepted = false;
                  boolean var21 = false /* VF: Semaphore variable */;

                  String var28;
                  label209: {
                     try {
                        var21 = true;
                        screen.recipesToOrder.add(entry);
                        screen.requestCraftable(entry, maximum ? entry.stack.getMaxStackSize() : 1);
                        if (entry.count > 0 && screen.itemsToOrder.size() <= 9) {
                           screen.searchBox.setValue("");
                           screen.refreshSearchNextTick = true;
                           accepted = true;
                           var28 = null;
                           var21 = false;
                           break label209;
                        }

                        var28 = "stock_changed";
                        var21 = false;
                     } finally {
                        if (var21) {
                           if (!accepted) {
                              screen.itemsToOrder = itemsBefore;
                              screen.recipesToOrder = recipesBefore;

                              for (int i = 0; i < countsBefore.length; i++) {
                                 recipesBefore.get(i).count = countsBefore[i];
                              }

                              screen.searchBox.setValue(searchBefore);
                              screen.refreshSearchNextTick = refreshBefore;
                           }
                        }
                     }

                     if (!accepted) {
                        screen.itemsToOrder = itemsBefore;
                        screen.recipesToOrder = recipesBefore;

                        for (int i = 0; i < countsBefore.length; i++) {
                           recipesBefore.get(i).count = countsBefore[i];
                        }

                        screen.searchBox.setValue(searchBefore);
                        screen.refreshSearchNextTick = refreshBefore;
                     }

                     return var28;
                  }

                  if (!accepted) {
                     screen.itemsToOrder = itemsBefore;
                     screen.recipesToOrder = recipesBefore;

                     for (int i = 0; i < countsBefore.length; i++) {
                        recipesBefore.get(i).count = countsBefore[i];
                     }

                     screen.searchBox.setValue(searchBefore);
                     screen.refreshSearchNextTick = refreshBefore;
                  }

                  return var28;
               } else {
                  return "stock_changed";
               }
            }
         }
      } catch (CapacityMatcher.SearchLimit var23) {
         return "search_limit";
      }
   }

   private static RecipeTransferSnapshot directNstOrNull(
      RecipeTransferSnapshot root, StockKeeperRequestScreen screen, InventorySummary summary, Function<ItemStack, Integer> reserved, int limit
   ) {
      try {
         return RecursiveMaterials.directNst(root, screen, summary, reserved, limit);
      } catch (RecursiveMaterials.Limit var6) {
         if (var6.key.equals("too_many_types")) {
            return null;
         } else {
            throw var6;
         }
      }
   }

   private static boolean hasRecipeIdentity(Object source) {
      return source instanceof Recipe || NstRecipeCompat.recipe(source) != null;
   }

   private static boolean sameSource(Object left, Object right) {
      if (left == right) {
         return true;
      } else {
         SmithingRecipe a = NstRecipeCompat.recipe(left);
         SmithingRecipe b = NstRecipeCompat.recipe(right);
         if (a != null) {
            left = a;
         }

         if (b != null) {
            right = b;
         }

         if (left == right) {
            return true;
         } else {
            if (left instanceof Recipe<?> first
               && right instanceof Recipe<?> second
               && first.getType() == second.getType()
               && first.getId().equals(second.getId())) {
               return true;
            }

            return false;
         }
      }
   }

   static String signature(ItemStack output, List<Ingredient> ingredients) {
      List<String> units = ingredients.stream()
         .map(i -> Arrays.stream(i.getItems()).map(s -> s.copyWithCount(1).serializeNBT().toString()).sorted().toList().toString())
         .sorted()
         .toList();
      return output.serializeNBT() + "|" + units;
   }

   public static IRecipeTransferError internalError() {
      return CompatJeiPlugin.helper == null ? () -> Type.INTERNAL : CompatJeiPlugin.helper.createInternalError();
   }

   public static IRecipeTransferError searchLimitError() {
      return error("search_limit");
   }

   private static IRecipeTransferError error(String key) {
      lastErrorKey = key;
      if ((Boolean)ClientConfig.DEBUG.get()) {
         CreateStockRecipeCompat.LOGGER.debug("[CSRC] Transfer rejected: {}", key);
      }

      return CompatJeiPlugin.helper == null
         ? internalError()
         : CompatJeiPlugin.helper.createUserErrorWithTooltip(Component.translatable("create_stock_recipe_compat.error." + key));
   }

   public static void reportFailure(Throwable failure) {
      long now = System.nanoTime();
      if (now - lastFailureLog > 30000000000L || lastFailureLog == 0L) {
         lastFailureLog = now;
         CreateStockRecipeCompat.LOGGER.error("[CSRC] Failed to transfer JEI recipe to Create stock keeper", failure);
      }
   }

   private CompatRecipeTransfer() {
   }

   public static record PlanResult(RecipeTransferSnapshot snapshot, String error) {
      static CompatRecipeTransfer.PlanResult failed(String key) {
         return new CompatRecipeTransfer.PlanResult(null, key);
      }
   }
}
