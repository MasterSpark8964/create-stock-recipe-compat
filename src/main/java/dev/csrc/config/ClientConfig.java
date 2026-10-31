package dev.csrc.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE, FORCE_CRAFTING, SYNTHETIC, REJECT_NON_ITEM, DEBUG;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NON_ITEM_OUTPUTS, BATCH_UNITS;
    public static final ForgeConfigSpec.BooleanValue RECURSIVE_MATERIALS;
    public static final ForgeConfigSpec.IntValue RECURSIVE_DEPTH;
    public static final ForgeConfigSpec.IntValue MAX_UNITS;
    public static final ForgeConfigSpec.IntValue MAX_STOCK, MAX_RECIPES, DEPENDENCY_BUDGET, DEPENDENCY_FRAMES, PLANNING_MS;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("general");
        ENABLE = b.define("enableUniversalJeiMaterialTransfer", true);
        FORCE_CRAFTING = b.comment("Opt in to material-only orders for crafting recipes; no crafting metadata.")
            .define("forceJeiForCraftingRecipes", false);
        SYNTHETIC = b.define("allowSyntheticRecipes", true);
        ALLOW_NON_ITEM_OUTPUTS = b.comment("Allow item-input recipes with no item output; the order is counted in material batches.")
            .define("allowNonItemOutputs", true);
        BATCH_UNITS = b.comment("Count all handled recipes in material batches instead of displayed output items. Useful for probabilistic or multiple outputs. Does not change default crafting transfers.")
            .define("useBatchUnitsForAllRecipes", false);
        RECURSIVE_MATERIALS = b.comment("Expand missing inputs only through vanilla workstation recipe types: crafting, smithing, smelting, blasting, smoking, stonecutting. Mod machine types are treated as missing raw materials. Mod/data-pack recipes registered under these workstation types are supported.")
            .define("expandMissingMaterials", true);
        RECURSIVE_DEPTH = b.comment("Maximum intermediate recipe depth. Cycles are always rejected.")
            .defineInRange("maxDependencyDepth", 16, 1, 32);
        MAX_UNITS = b.defineInRange("maxExpandedIngredients", 512, 1, 2048);
        MAX_STOCK = b.defineInRange("maxStockVariants", 16384, 256, 32768);
        MAX_RECIPES = b.defineInRange("maxDependencyRecipes", 65536, 1024, 131072);
        DEPENDENCY_BUDGET = b.defineInRange("dependencySearchOperations", 2000000, 80000, 20000000);
        DEPENDENCY_FRAMES = b.comment("Active decision frames, separate from recipe depth. Retained to protect the Java call stack.")
            .defineInRange("maxDependencyDecisionFrames", 256, 64, 384);
        PLANNING_MS = b.comment("Cooperative deadline per recursive plan. Larger values can pause the client longer; mod callback duration cannot be preempted.")
            .defineInRange("dependencyPlanningMillis", 500, 50, 5000);
        REJECT_NON_ITEM = b.comment("Safety setting: non-item inputs are always rejected in v1, even if false.")
            .define("rejectNonItemInputs", true);
        DEBUG = b.define("debugLogging", false);
        b.pop();
        SPEC = b.build();
    }
    private ClientConfig() {}
}
