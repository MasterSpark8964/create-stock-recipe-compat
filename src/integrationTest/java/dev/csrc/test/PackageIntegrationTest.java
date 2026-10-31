package dev.csrc.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.NetworkHooks;

/** Real isolated-world logistics test, using the original private sendIt method and original network packet. */
final class PackageIntegrationTest {
    private static volatile BlockPos tickerPos, packagerPos;
    private static volatile String outcome;
    private static volatile boolean sent;
    private static volatile boolean serverReady;
    private static UUID testPlayerId;
    private static IJeiRuntime runtime;
    private static IJeiHelpers helpers;
    private static StockKeeperRequestScreen screen;
    private static int clientTicks, serverTicks, setupTicks;
    private static boolean active, saved;
    private static final List<String> evidence = new java.util.concurrent.CopyOnWriteArrayList<>();

    static void start(IJeiRuntime jei, IJeiHelpers jeiHelpers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) throw new IllegalStateException("Use an isolated singleplayer test world");
        runtime = jei; helpers = jeiHelpers; active = true;
        UUID playerId = mc.player.getUUID();
        testPlayerId = playerId;
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                var chestPos = player.blockPosition().offset(3, 1, 0);
                var pp = chestPos.east();
                var tp = chestPos.south(2);
                level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(pp, AllBlocks.PACKAGER.getDefaultState().setValue(PackagerBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(pp.above(), AllBlocks.STOCK_LINK.getDefaultState()
                    .setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR));
                level.setBlockAndUpdate(tp.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(tp, AllBlocks.STOCK_TICKER.getDefaultState());
                level.setBlockAndUpdate(tp.east(), AllBlocks.BLAZE_BURNER.getDefaultState()
                    .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
                var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 10));
                for (int i = 1; i <= 10; i++) chest.setItem(i, new ItemStack(Items.DIAMOND_SWORD));
                chest.setItem(11, new ItemStack(Items.NETHERITE_INGOT, 10));
                chest.setChanged();
                var link = (PackagerLinkBlockEntity) level.getBlockEntity(pp.above());
                var ticker = (StockTickerBlockEntity) level.getBlockEntity(tp);
                ticker.behaviour.freqId = link.behaviour.freqId;
                link.placedBy = playerId;
                link.initialize(); ticker.initialize();
                LogisticallyLinkedBehaviour.keepAlive(link.behaviour);
                ticker.sendData(); link.sendData();
                if (!(level.getBlockEntity(tp.east()) instanceof BlazeBurnerBlockEntity) || !ticker.isKeeperPresent())
                    throw new AssertionError("A filled blaze burner must be present before opening the keeper");
                packagerPos = pp;
                tickerPos = tp; // publish only after setup is complete
            } catch (Throwable error) { outcome = "FAIL setup: " + error; error.printStackTrace(); }
        });
    }

    static void clientTick() {
        if (!active || saved) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (outcome != null) {
                evidence.add(outcome);
                Files.write(Path.of("csrc-package-results.txt"), evidence);
                evidence.forEach(s -> dev.csrc.CreateStockRecipeCompat.LOGGER.info("[CSRC PACKAGE TEST] {}", s));
                saved = true;
                return;
            }
            if (++clientTicks > 1200) { outcome = "FAIL client timeout"; return; }
            if (!serverReady || tickerPos == null || mc.level == null || mc.player == null) return;
            if (!(mc.level.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity ticker)) return;
            if (screen == null) {
                if (!(mc.screen instanceof StockKeeperRequestScreen opened)) return;
                if (!(mc.level.getBlockEntity(tickerPos.east()) instanceof BlazeBurnerBlockEntity))
                    throw new AssertionError("Client has no filled blaze burner");
                screen = opened;
                if (mc.player.containerMenu != screen.getMenu()) throw new AssertionError("Menu did not open through server");
                ticker.refreshClientStockSnapshot();
                return;
            }
            if (sent || clientTicks < 80 || ticker.getLastClientsideStockSnapshotAsSummary() == null) return;
            var summary = ticker.getLastClientsideStockSnapshotAsSummary();
            if (summary.getCountOf(new ItemStack(Items.DIAMOND_SWORD)) < 1) {
                if (clientTicks % 20 == 0) ticker.refreshClientStockSnapshot();
                return;
            }
            if (mc.screen != screen) throw new AssertionError("Keeper screen closed before sending");
            if (summary.getCountOf(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) != 10
                || summary.getCountOf(new ItemStack(Items.NETHERITE_INGOT)) != 10
                || summary.getCountOf(new ItemStack(Items.DIAMOND_SWORD)) != 10)
                throw new AssertionError("Client network snapshot does not match the stocked chest");
            evidence.add("Client live inventory: " + summary.getStacksByCount());
            var manager = runtime.getRecipeManager();
            var recipe = manager.createRecipeLookup(RecipeTypes.SMITHING).get()
                .filter(r -> r.getResultItem(mc.level.registryAccess()).is(Items.NETHERITE_SWORD)).findFirst().orElseThrow();
            var slots = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(RecipeTypes.SMITHING), recipe,
                helpers.getFocusFactory().getEmptyFocusGroup()).orElseThrow().getRecipeSlotsView();
            var snapshot = dev.csrc.compat.jei.JeiRecipeIngredientExtractor.extract(recipe, slots);
            evidence.add("Recipe: " + recipe.getId());
            evidence.add("JEI INPUT: " + snapshot.ingredients().stream().map(i -> java.util.Arrays.toString(i.getItems())).toList());
            var error = new StockKeeperTransferHandler(helpers).transferRecipe(screen.getMenu(), recipe, slots, mc.player, false, true);
            if (error != null) throw new AssertionError("Real-network transfer rejected: " + error.getType());
            evidence.add("Stock order: " + screen.itemsToOrder);
            var send = StockKeeperRequestScreen.class.getDeclaredMethod("sendIt");
            send.setAccessible(true);
            send.invoke(screen);
            if (!screen.itemsToOrder.isEmpty()) throw new AssertionError("sendIt did not clear dispatched order");
            sent = true;
        } catch (Throwable error) { outcome = "FAIL client: " + error; error.printStackTrace(); }
    }

    static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tickerPos == null || outcome != null) return;
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            var level = server.overworld();
            if (!(level.getBlockEntity(packagerPos) instanceof PackagerBlockEntity packager)) return;
            if (!serverReady) {
                if (++setupTicks < 40) return;
                var link = (PackagerLinkBlockEntity) level.getBlockEntity(packagerPos.above());
                var ticker = (StockTickerBlockEntity) level.getBlockEntity(tickerPos);
                if (!ticker.behaviour.freqId.equals(link.behaviour.freqId)) throw new AssertionError("Network UUID mismatch");
                if (link.getPackager() != packager) throw new AssertionError("Stock link does not target this packager");
                if (!LogisticallyLinkedBehaviour.getAllPresent(ticker.behaviour.freqId, true).contains(link.behaviour))
                    throw new AssertionError("Bound stock link is not online");
                if (!ticker.isKeeperPresent()) throw new AssertionError("Missing real keeper");
                var stock = ticker.getAccurateSummary();
                if (stock.getCountOf(new ItemStack(Items.DIAMOND_SWORD)) != 10
                    || stock.getCountOf(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) != 10
                    || stock.getCountOf(new ItemStack(Items.NETHERITE_INGOT)) != 10)
                    throw new AssertionError("Server stock link does not expose expected 10/10/10 inventory: " + stock.getStacksByCount());
                evidence.add("Server: filled blaze keeper present; stock link online and attached; UUID=" + ticker.behaviour.freqId);
                evidence.add("Server accurate network inventory: " + stock.getStacksByCount());
                var player = server.getPlayerList().getPlayer(testPlayerId);
                NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) ->
                    new StockKeeperRequestMenu(AllMenuTypes.STOCK_KEEPER_REQUEST.get(), id, inv, ticker),
                    Component.literal("CSRC real package test")), buf -> {
                        buf.writeBoolean(true); buf.writeBoolean(false); buf.writeBlockPos(tickerPos);
                    });
                serverReady = true;
                return;
            }
            if (!sent) return;
            if (++serverTicks > 200) { outcome = "FAIL no physical package after 200 server ticks"; return; }
            if (packager.heldBox.isEmpty()) return;
            var inventory = PackageItem.getContents(packager.heldBox.copy());
            int template = 0, sword = 0, ingot = 0, total = 0;
            List<String> actual = new ArrayList<>();
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                total += stack.getCount(); actual.add(stack.toString());
                if (stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) template += stack.getCount();
                if (stack.is(Items.DIAMOND_SWORD)) sword += stack.getCount();
                if (stack.is(Items.NETHERITE_INGOT)) ingot += stack.getCount();
            }
            if (template != 1 || sword != 1 || ingot != 1 || total != 3)
                throw new AssertionError("Wrong physical package contents: " + actual);
            outcome = "PASS actual Packager heldBox contents=" + actual + "; original sendIt + PackageOrderRequestPacket";
        } catch (Throwable error) { outcome = "FAIL server: " + error; error.printStackTrace(); }
    }
}
