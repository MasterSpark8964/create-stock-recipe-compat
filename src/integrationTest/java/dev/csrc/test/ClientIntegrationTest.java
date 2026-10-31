package dev.csrc.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import dev.csrc.compat.jei.JeiMaterialCraftableBigItemStack;
import dev.csrc.compat.jei.JeiRecipeIngredientExtractor;
import dev.csrc.config.ClientConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/** Development-only integration harness. No classes/resources from this source set enter the release Jar. */
@Mod("csrc_integration_test")
@JeiPlugin
public final class ClientIntegrationTest implements IModPlugin {
    record Fixture(List<List<ItemStack>> inputs, ItemStack output, boolean fluid, boolean fluidOutput) {
        Fixture(List<List<ItemStack>> inputs, ItemStack output, boolean fluid) {
            this(inputs, output, fluid, false);
        }
    }
    static final RecipeType<Fixture> TYPE = RecipeType.create("csrc_integration_test", "materials", Fixture.class);
    private static IJeiHelpers helpers;
    private static IJeiRuntime runtime;
    private static boolean registered, ran;
    private static final List<String> results = new ArrayList<>();

    public ClientIntegrationTest() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(ClientIntegrationTest::tick);
            MinecraftForge.EVENT_BUS.addListener(PackageIntegrationTest::serverTick);
        }
    }
    @Override public ResourceLocation getPluginUid() { return TYPE.getUid(); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new IRecipeCategory<Fixture>() {
            public RecipeType<Fixture> getRecipeType() { return TYPE; }
            public Component getTitle() { return Component.literal("CSRC material tests"); }
            public int getWidth() { return 160; }
            public int getHeight() { return 40; }
            public IDrawable getIcon() { return helpers.getGuiHelper().createDrawableItemStack(new ItemStack(Items.CHEST)); }
            public void setRecipe(IRecipeLayoutBuilder builder, Fixture recipe, IFocusGroup focus) {
                for (int i = 0; i < recipe.inputs.size(); i++)
                    builder.addSlot(RecipeIngredientRole.INPUT, i * 18, 0).addItemStacks(recipe.inputs.get(i));
                if (recipe.fluid) builder.addSlot(RecipeIngredientRole.INPUT, 100, 0).addFluidStack(Fluids.WATER, 1000);
                if (!recipe.output.isEmpty()) builder.addSlot(RecipeIngredientRole.OUTPUT, 140, 20).addItemStack(recipe.output);
                if (recipe.fluidOutput) builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 20).addFluidStack(Fluids.WATER, 250);
                builder.addSlot(RecipeIngredientRole.CATALYST, 100, 20).addItemStack(new ItemStack(Items.COAL));
            }
        });
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, fixtures());
    }
    private static List<Fixture> fixtures() {
        return List.of(
            new Fixture(List.of(List.of(new ItemStack(Items.IRON_INGOT, 4)), List.of(new ItemStack(Items.GOLD_INGOT, 2)),
                List.of(new ItemStack(Items.DIAMOND))), new ItemStack(Items.EMERALD, 4), false),
            new Fixture(List.of(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT)),
                List.of(new ItemStack(Items.IRON_INGOT))), new ItemStack(Items.DIAMOND), false),
            new Fixture(List.of(List.of(new ItemStack(Items.IRON_INGOT))), new ItemStack(Items.DIAMOND), true),
            new Fixture(List.of(List.of(new ItemStack(Items.IRON_INGOT, 2))), ItemStack.EMPTY, false, true),
            new Fixture(List.of(List.of(new ItemStack(Items.IRON_INGOT, 3))), ItemStack.EMPTY, false));
    }
    private static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            mc.getWindow().setTitle("CSRC isolated integration test (E:/mc/run)");
            PackageIntegrationTest.clientTick();
        }
        if (event.phase != TickEvent.Phase.END || ran || runtime == null || mc.player == null || mc.level == null) return;
        ran = true;
        if (Boolean.getBoolean("csrc.portabilityOnly")) {
            try {
                runPortability();
                results.add("PASS portability suite 0.2.0 (real JEI layouts and Create transfer handler; no physical package test)");
            } catch (Throwable error) {
                results.add("FAIL " + error);
                error.printStackTrace();
            }
            try { Files.write(Path.of("csrc-portability-0.2.0-results.txt"), results); }
            catch (Exception error) { error.printStackTrace(); }
            results.forEach(s -> dev.csrc.CreateStockRecipeCompat.LOGGER.info("[CSRC TEST] {}", s));
            mc.stop();
            return;
        }
        try {
            ClientConfig.DEBUG.set(true);
            runVanilla(RecipeTypes.SMITHING);
            runVanilla(RecipeTypes.SMELTING);
            runVanilla(RecipeTypes.BLASTING);
            runVanilla(RecipeTypes.SMOKING);
            runVanilla(RecipeTypes.STONECUTTING);
            runCreateCategories();
            runCraftingRegression();
            for (var crafting : runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get().limit(6).toList()) {
                var view = layout(RecipeTypes.CRAFTING, crafting);
                check(!dev.csrc.compat.jei.CompatRecipeTransfer.shouldHandle(crafting, view), "crafting pass-through");
            }
            var fixture = fixtures().get(0);
            var screen = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 64),
                new BigItemStack(new ItemStack(Items.GOLD_INGOT), 64), new BigItemStack(new ItemStack(Items.DIAMOND), 64)));
            transfer(screen, TYPE, fixture, true);
            check(screen.recipesToOrder.get(0).count == 4, "output x4 initial batch");
            check(amount(screen, new ItemStack(Items.IRON_INGOT)) == 4, "input x4");
            check(amount(screen, new ItemStack(Items.GOLD_INGOT)) == 2, "input x2");
            screen.requestCraftable(screen.recipesToOrder.get(0), 12);
            check(screen.recipesToOrder.get(0).count == 16 && amount(screen, new ItemStack(Items.IRON_INGOT)) == 16,
                "output x16 means four batches");
            var overlap = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 1),
                new BigItemStack(new ItemStack(Items.COPPER_INGOT), 1)));
            transfer(overlap, TYPE, fixtures().get(1), true);
            check(amount(overlap, new ItemStack(Items.COPPER_INGOT)) == 1, "alternative uses copper");
            check(amount(overlap, new ItemStack(Items.IRON_INGOT)) == 1, "exclusive input keeps iron");
            var missing = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 1)));
            transfer(missing, TYPE, fixtures().get(1), false);
            check(missing.itemsToOrder.isEmpty() && missing.recipesToOrder.isEmpty(), "missing stock no mutation");
            transfer(missing, TYPE, fixtures().get(2), false);
            check(missing.itemsToOrder.isEmpty(), "fluid no partial order");
            results.add("PASS integration suite (physical package production is a separate test)");
            PackageIntegrationTest.start(runtime, helpers);
        } catch (Throwable error) {
            results.add("FAIL " + error);
            error.printStackTrace();
        }
        try {
            Files.write(Path.of("csrc-integration-results.txt"), results);
        } catch (Exception error) { error.printStackTrace(); }
        results.forEach(s -> dev.csrc.CreateStockRecipeCompat.LOGGER.info("[CSRC TEST] {}", s));
    }
    private static void runPortability() {
        boolean allow = ClientConfig.ALLOW_NON_ITEM_OUTPUTS.get(), batches = ClientConfig.BATCH_UNITS.get();
        try {
            ClientConfig.ALLOW_NON_ITEM_OUTPUTS.set(true);
            ClientConfig.BATCH_UNITS.set(false);
            runVanilla(RecipeTypes.SMELTING);
            runVanilla(RecipeTypes.SMITHING);
            runVanilla(RecipeTypes.STONECUTTING);
            for (int index : new int[]{3, 4}) {
                var screen = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 30)));
                transfer(screen, TYPE, fixtures().get(index), true);
                var entry = screen.recipesToOrder.get(0);
                check(entry.count == 1, "non-item output uses one batch " + index);
                check(amount(screen, new ItemStack(Items.IRON_INGOT)) == index - 1, "batch input quantity " + index);
                check(screen.itemsToOrder.stream().noneMatch(s -> s.stack.is(Items.PAPER)), "display marker never ordered");
                screen.requestCraftable(entry, 2);
                check(entry.count == 3 && amount(screen, new ItemStack(Items.IRON_INGOT)) == (index - 1) * 3,
                    "three batches scale inputs " + index);
                screen.requestCraftable(entry, -2);
                check(entry.count == 1 && amount(screen, new ItemStack(Items.IRON_INGOT)) == index - 1,
                    "decreasing batches releases stock " + index);
            }
            ClientConfig.ALLOW_NON_ITEM_OUTPUTS.set(false);
            var rejected = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 30)));
            transfer(rejected, TYPE, fixtures().get(3), false);
            check(rejected.itemsToOrder.isEmpty() && rejected.recipesToOrder.isEmpty(), "disabled fallback leaves no order");
            ClientConfig.ALLOW_NON_ITEM_OUTPUTS.set(true);
            ClientConfig.BATCH_UNITS.set(true);
            var counted = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 64),
                new BigItemStack(new ItemStack(Items.GOLD_INGOT), 64), new BigItemStack(new ItemStack(Items.DIAMOND), 64)));
            transfer(counted, TYPE, fixtures().get(0), true);
            check(counted.recipesToOrder.get(0).count == 1, "forced batch mode ignores output x4 for counting");
            check(amount(counted, new ItemStack(Items.IRON_INGOT)) == 4 && amount(counted, new ItemStack(Items.GOLD_INGOT)) == 2,
                "forced batch mode retains exact inputs");
            var fluid = screen(List.of(new BigItemStack(new ItemStack(Items.IRON_INGOT), 64)));
            transfer(fluid, TYPE, fixtures().get(2), false);
            check(fluid.itemsToOrder.isEmpty(), "batch mode still rejects required fluid inputs");
            var recipe = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get().findFirst().orElseThrow();
            check(!dev.csrc.compat.jei.CompatRecipeTransfer.shouldHandle(recipe, layout(RecipeTypes.CRAFTING, recipe)),
                "batch setting preserves default crafting path");
        } finally {
            ClientConfig.ALLOW_NON_ITEM_OUTPUTS.set(allow);
            ClientConfig.BATCH_UNITS.set(batches);
        }
    }
    private static <T> void runVanilla(RecipeType<T> type) {
        T recipe = runtime.getRecipeManager().createRecipeLookup(type).get().findFirst().orElseThrow();
        var view = layout(type, recipe);
        var snap = JeiRecipeIngredientExtractor.extract(recipe, view);
        List<BigItemStack> stock = snap.ingredients().stream()
            .map(i -> new BigItemStack(i.getItems()[0].copy(), 64)).toList();
        var screen = screen(stock);
        transfer(screen, type, recipe, true);
        results.add("PASS " + type.getUid() + " recipe=" + recipe + " order=" + screen.itemsToOrder);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void runCreateCategories() {
        var wanted = java.util.Set.of("pressing", "cutting", "milling", "crushing", "mixing", "deploying",
            "filling", "compacting", "sequenced_assembly");
        for (var category : runtime.getRecipeManager().createRecipeCategoryLookup().get().toList()) {
            RecipeType type = category.getRecipeType();
            if (!type.getUid().getNamespace().equals("create") || !wanted.contains(type.getUid().getPath())) continue;
            var recipe = runtime.getRecipeManager().createRecipeLookup(type).get().findFirst();
            if (recipe.isEmpty()) continue;
            Object value = recipe.get();
            try {
                var snap = JeiRecipeIngredientExtractor.extract(value, layout(type, value));
                var screen = screen(snap.ingredients().stream().map(i -> new BigItemStack(i.getItems()[0].copy(), 256)).toList());
                transfer(screen, type, value, true);
                results.add("PASS Create category " + type.getUid() + " class=" + value.getClass().getName() + " order=" + screen.itemsToOrder);
            } catch (JeiRecipeIngredientExtractor.InvalidRecipe invalid) {
                if (!invalid.key.equals("non_item_input")) throw invalid;
                var screen = screen(List.of());
                transfer(screen, type, value, false);
                check(screen.itemsToOrder.isEmpty(), "Create fluid rejection " + type.getUid());
            }
        }
    }
    private static void runCraftingRegression() {
        Minecraft mc = Minecraft.getInstance();
        for (var output : List.of(Items.OAK_PLANKS, Items.STICK, Items.CRAFTING_TABLE, Items.CHEST, Items.PISTON, Items.HOPPER)) {
            var recipe = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(r -> r.getResultItem(mc.level.registryAccess()).is(output)).findFirst().orElseThrow();
            var slots = layout(RecipeTypes.CRAFTING, recipe);
            var snap = JeiRecipeIngredientExtractor.extract(recipe, slots);
            var screen = screen(snap.ingredients().stream().map(i -> new BigItemStack(i.getItems()[0].copy(), 64)).toList());
            var error = new StockKeeperTransferHandler(helpers).transferRecipe(screen.getMenu(), recipe, slots, mc.player, false, true);
            check(error == null && !screen.itemsToOrder.isEmpty(), "native crafting transfer " + recipe.getId());
            check(screen.recipesToOrder.size() == 1 && !(screen.recipesToOrder.get(0) instanceof JeiMaterialCraftableBigItemStack)
                && screen.recipesToOrder.get(0).recipe == recipe, "original crafting entry and metadata retained");
        }
    }
    private static <T> mezz.jei.api.gui.ingredient.IRecipeSlotsView layout(RecipeType<T> type, T recipe) {
        var manager = runtime.getRecipeManager();
        return manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), recipe,
            helpers.getFocusFactory().getEmptyFocusGroup()).orElseThrow().getRecipeSlotsView();
    }
    private static <T> void transfer(StockKeeperRequestScreen screen, RecipeType<T> type, T recipe, boolean success) {
        var handler = new StockKeeperTransferHandler(helpers);
        var view = layout(type, recipe);
        var preview = handler.transferRecipe(screen.getMenu(), recipe, view, Minecraft.getInstance().player, false, false);
        check((preview == null) == success, "preview " + type.getUid());
        check(screen.itemsToOrder.isEmpty(), "preview is read-only");
        var actual = handler.transferRecipe(screen.getMenu(), recipe, view, Minecraft.getInstance().player, false, true);
        check((actual == null) == success, "transfer " + type.getUid());
        if (success) {
            check(screen.recipesToOrder.size() == 1 && screen.recipesToOrder.get(0) instanceof JeiMaterialCraftableBigItemStack,
                "public handler Mixin active");
            check(!(screen.recipesToOrder.get(0).recipe instanceof CraftingRecipe), "no crafting metadata");
            check(!screen.itemsToOrder.isEmpty(), "material order is populated");
        }
    }
    private static StockKeeperRequestScreen screen(List<BigItemStack> stock) {
        Minecraft mc = Minecraft.getInstance();
        var ticker = new StockTickerBlockEntity(AllBlockEntityTypes.STOCK_TICKER.get(), BlockPos.ZERO,
            AllBlocks.STOCK_TICKER.getDefaultState());
        ticker.setLevel(mc.level);
        var menu = new StockKeeperRequestMenu(AllMenuTypes.STOCK_KEEPER_REQUEST.get(), 0, mc.player.getInventory(), ticker);
        var screen = new StockKeeperRequestScreen(menu, mc.player.getInventory(), Component.literal("CSRC Test"));
        screen.searchBox = new EditBox(mc.font, 0, 0, 100, 20, Component.empty());
        ticker.receiveStockPacket(new ArrayList<>(stock), true);
        return screen;
    }
    private static int amount(StockKeeperRequestScreen screen, ItemStack item) {
        return screen.itemsToOrder.stream().filter(s -> ItemHandlerHelper.canItemStacksStack(s.stack, item)).mapToInt(s -> s.count).sum();
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        results.add("PASS " + name);
    }
}
