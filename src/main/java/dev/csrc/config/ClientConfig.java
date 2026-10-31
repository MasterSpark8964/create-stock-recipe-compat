package dev.csrc.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class ClientConfig {
   public static final ForgeConfigSpec SPEC;
   public static final BooleanValue ENABLE;
   public static final BooleanValue FORCE_CRAFTING;
   public static final BooleanValue SYNTHETIC;
   public static final BooleanValue REJECT_NON_ITEM;
   public static final BooleanValue DEBUG;
   public static final BooleanValue ALLOW_NON_ITEM_OUTPUTS;
   public static final BooleanValue BATCH_UNITS;
   public static final BooleanValue RECURSIVE_MATERIALS;
   public static final BooleanValue DIAGNOSTICS;
   public static final BooleanValue MEMORY_DIAGNOSTICS;
   public static final IntValue RECURSIVE_DEPTH;
   public static final IntValue MAX_UNITS;
   public static final IntValue MAX_STOCK;
   public static final IntValue MAX_RECIPES;
   public static final IntValue DEPENDENCY_BUDGET;
   public static final IntValue DEPENDENCY_FRAMES;
   public static final IntValue PLANNING_MS;

   private ClientConfig() {
   }

   static {
      Builder b = new Builder();
      b.push("general");
      ENABLE = b.define("enableUniversalJeiMaterialTransfer", true);
      FORCE_CRAFTING = b.comment("Legacy setting retained for config migration; native crafting transfer is always preserved.")
         .define("forceJeiForCraftingRecipes", false);
      SYNTHETIC = b.define("allowSyntheticRecipes", true);
      ALLOW_NON_ITEM_OUTPUTS = b.comment("Allow item-input recipes with no item output; the order is counted in material batches.")
         .define("allowNonItemOutputs", true);
      BATCH_UNITS = b.comment(
            "Count all handled recipes in material batches instead of displayed output items. Useful for probabilistic or multiple outputs. Does not change default crafting transfers."
         )
         .define("useBatchUnitsForAllRecipes", false);
      RECURSIVE_MATERIALS = b.comment(
            "Expand missing inputs only through vanilla workstation recipe types: crafting, smithing, smelting, blasting, smoking, campfire, stonecutting. Searches only after clicking the dependency button. Mod machine types are treated as missing raw materials. Mod/data-pack recipes registered under these workstation types are supported."
         )
         .define("expandMissingMaterials", true);
      RECURSIVE_DEPTH = b.comment("Maximum intermediate recipe depth. Cycles are always rejected.").defineInRange("maxDependencyDepth", 16, 1, 32);
      MAX_UNITS = b.defineInRange("maxExpandedIngredients", 512, 1, 2048);
      MAX_STOCK = b.defineInRange("maxStockVariants", 16384, 256, 32768);
      MAX_RECIPES = b.defineInRange("maxDependencyRecipes", 65536, 1024, 131072);
      DEPENDENCY_BUDGET = b.defineInRange("dependencySearchOperations", 2000000, 80000, 20000000);
      DEPENDENCY_FRAMES = b.comment("Active decision frames, separate from recipe depth. Retained to protect the Java call stack.")
         .defineInRange("maxDependencyDecisionFrames", 256, 64, 384);
      PLANNING_MS = b.comment(
            "Active worker computation budget per plan, excluding waits for client callbacks. Search wall-clock limit is 30 seconds. Client callbacks are scheduled across ticks; a mod callback cannot be preempted."
         )
         .defineInRange("dependencyPlanningMillis", 500, 50, 5000);
      REJECT_NON_ITEM = b.comment("Safety setting: non-item inputs are always rejected in v1, even if false.").define("rejectNonItemInputs", true);
      DEBUG = b.define("debugLogging", false);
      DIAGNOSTICS = b.comment(
            "Opt-in local recipe diagnostics and replay files. Includes ingredient/stock item NBT; never uploads data. Disabled for normal play."
         )
         .define("enableRecipeDiagnostics", false);
      MEMORY_DIAGNOSTICS = b.comment(
            "Local lightweight memory/lifecycle diagnostics every 30 seconds. No item NBT, network upload or forced GC. Test release default enabled."
         )
         .define("enableMemoryDiagnostics", false);
      b.pop();
      SPEC = b.build();
   }
}
