package dev.csrc.compat.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.csrc.CreateStockRecipeCompat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;

final class DiagnosticTrace {
   private static final String FILE = "csrc-recipe-diagnostics.txt";
   private final AtomicBoolean saved = new AtomicBoolean();
   private final long started = System.nanoTime();
   private final Instant timestamp = Instant.now();
   private final String rootId;
   private final List<String> phases = new ArrayList<>();
   volatile String phase = "prepare";
   volatile String lastRecipe = "none";
   volatile String slowCallback = "none";
   volatile String slowIndex = "none";
   volatile long maxCallbackNanos;
   volatile long maxIndexNanos;
   volatile long indexOver2ms;
   volatile int inputCount;
   volatile int stockVariants;
   volatile int scannedRecipes;
   volatile int indexedProductions;
   volatile int indexedSmithing;
   volatile int oversizedRecipes;
   volatile int maxDepth;
   volatile int rootStockOptions;
   volatile int rootProductionOptions;
   volatile int rootSmithingOptions;
   volatile int excludedRecoloring;
   volatile long operations;
   volatile long productionBranches;
   volatile long smithingBranches;
   volatile long directAttempts;
   volatile long decoratedBaseRejects;
   volatile long conversionCycleRejects;
   volatile long unreachableRejects;
   volatile long directMemoHits;
   volatile boolean opaqueReachability;
   volatile boolean indexCacheHit;
   volatile int cachedRecipes;
   volatile long terminalPrunes;
   private final List<String> replay = new ArrayList<>();
   private final List<String> replayStock = new ArrayList<>();
   private static final ThreadPoolExecutor REPLAY_IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
      Thread thread = new Thread(task, "CSRC local replay writer");
      thread.setDaemon(true);
      return thread;
   }, new AbortPolicy());
   volatile String resultItem = "unknown";
   volatile int depthLimit;
   volatile int unitLimit;
   volatile int typeLimit;
   volatile int operationLimit;

   static String memoryStatus() {
      return "replayWriterActive=" + REPLAY_IO.getActiveCount() + " replayWriterQueued=" + REPLAY_IO.getQueue().size();
   }

   DiagnosticTrace(Object source) {
      this.rootId = source instanceof Recipe<?> recipe ? recipe.getId().toString() : (source == null ? "test" : source.getClass().getName());
      this.phases.add("prepare@0ms");
   }

   synchronized void stage(String name) {
      this.phase = name;
      if (this.phases.size() < 32) {
         this.phases.add(name + "@" + this.elapsedMs() + "ms");
      }
   }

   synchronized void prepared(RecipeTransferSnapshot root) {
      if (!this.saved.get()) {
         this.inputCount = root.ingredients().size();
         this.resultItem = BuiltInRegistries.ITEM.getKey(root.result().getItem()).toString();
         JsonObject data = new JsonObject();
         data.addProperty("kind", "root");
         data.addProperty("id", this.rootId);
         data.add("output", stack(root.result()));
         data.add("inputs", ingredients(root.ingredients()));
         this.replay.add(data.toString());
      }
   }

   synchronized void planned(List<Ingredient> leaves) {
      if (!this.saved.get()) {
         JsonObject data = new JsonObject();
         data.addProperty("kind", "plan");
         data.addProperty("units", leaves.size());
         data.add("inputs", ingredients(leaves));
         this.replay.add(data.toString());
      }
   }

   synchronized List<String> recipeLines() {
      return this.replay.stream().filter(line -> line.startsWith("{\"kind\":\"recipe\"")).toList();
   }

   synchronized void restoreRecipes(List<String> lines) {
      if (!this.saved.get()) {
         this.replay.addAll(lines);
      }
   }

   private static JsonObject stack(ItemStack item) {
      JsonObject data = new JsonObject();
      data.addProperty("item", item.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
      data.addProperty("count", item.getCount());
      if (item.hasTag()) {
         data.addProperty("nbt", item.getTag().toString());
      }

      return data;
   }

   private static JsonArray ingredients(List<Ingredient> inputs) {
      JsonArray result = new JsonArray();

      for (Ingredient ingredient : inputs) {
         JsonObject data = new JsonObject();
         data.addProperty("class", ingredient.getClass().getName());
         JsonArray options = new JsonArray();

         for (ItemStack item : ingredient.getItems()) {
            options.add(stack(item));
         }

         data.add("alternatives", options);

         try {
            data.add("definition", ingredient.toJson());
         } catch (RuntimeException var10) {
            data.addProperty("definitionUnavailable", true);
         }

         result.add(data);
      }

      return result;
   }

   synchronized void beginStock() {
      this.replayStock.clear();
   }

   synchronized void stock(ItemStack item, int available, boolean existing) {
      if (!this.saved.get()) {
         JsonObject data = stack(item);
         data.addProperty("kind", "stock");
         data.addProperty("available", available);
         data.addProperty("alreadyOrdered", existing);
         this.replayStock.add(data.toString());
      }
   }

   synchronized void recipe(Recipe<?> recipe, ItemStack output, List<Ingredient> inputs, boolean opaque) {
      if (!this.saved.get()) {
         JsonObject data = new JsonObject();
         data.addProperty("kind", "recipe");
         data.addProperty("id", recipe.getId().toString());
         data.addProperty("class", recipe.getClass().getName());
         data.addProperty("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString());
         data.addProperty("opaque", opaque);
         data.add("output", stack(output));
         data.add("inputs", ingredients(inputs));
         this.replay.add(data.toString());
      }
   }

   void indexRecipe(Recipe<?> recipe, long elapsed) {
      this.scannedRecipes++;
      this.lastRecipe = recipe.getId().toString();
      if (elapsed > 2000000L) {
         this.indexOver2ms++;
      }

      if (elapsed > this.maxIndexNanos) {
         this.maxIndexNanos = elapsed;
         this.slowIndex = recipe.getId().toString();
      }
   }

   void callback(long nanos, String label) {
      if (nanos > this.maxCallbackNanos) {
         this.maxCallbackNanos = nanos;
         this.slowCallback = label;
      }
   }

   synchronized void save(String outcome, SearchSession session) {
      if (this.saved.compareAndSet(false, true)) {
         try {
            String record = this.record(outcome, session);
            Path summaryPath = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("csrc-recipe-diagnostics.txt");
            JsonObject header = new JsonObject();
            header.addProperty("kind", "attempt");
            header.addProperty("version", "0.4.6");
            header.addProperty("root", this.rootId);
            header.addProperty("outcome", outcome);
            header.addProperty("depthLimit", this.depthLimit);
            header.addProperty("unitLimit", this.unitLimit);
            header.addProperty("newTypeLimit", this.typeLimit);
            header.addProperty("opaqueReachability", this.opaqueReachability);
            ArrayList<String> snapshot = new ArrayList<>();
            snapshot.add(header.toString());
            snapshot.addAll(this.replay);
            snapshot.addAll(this.replayStock);
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/csrc-replays").resolve("attempt-" + this.timestamp.toEpochMilli() + ".jsonl");
            REPLAY_IO.execute(() -> {
               try {
                  Files.createDirectories(path.getParent());
                  Files.writeString(summaryPath, record, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  Files.write(path, snapshot, StandardCharsets.UTF_8);
               } catch (Exception var5x) {
                  CreateStockRecipeCompat.LOGGER.warn("[CSRC TEST] Could not write replay", var5x);
               }
            });
         } catch (RejectedExecutionException var12) {
            CreateStockRecipeCompat.LOGGER.warn("[CSRC] Diagnostic writer busy; skipped this snapshot");
         } catch (Exception var13) {
            CreateStockRecipeCompat.LOGGER.warn("[CSRC TEST] Could not write local diagnostics", var13);
         } finally {
            this.replay.clear();
            this.replayStock.clear();
         }
      }
   }

   private String record(String outcome, SearchSession session) {
      long total = this.elapsedMs();
      long suspended = TimeUnit.NANOSECONDS.toMillis(session.suspendedNanos());
      long callbacks = session.callbackCount();
      long callbackMs = TimeUnit.NANOSECONDS.toMillis(session.callbackNanos());
      long pumpMs = TimeUnit.NANOSECONDS.toMillis(session.pumpNanos());
      long memoryMiB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L;
      return "\n=== CSRC 0.4.6 "
         + this.timestamp
         + " ===\nresult="
         + outcome
         + " root="
         + this.rootId
         + " output="
         + this.resultItem
         + " inputs="
         + this.inputCount
         + "\nwallMs="
         + total
         + " waitForClientMs="
         + suspended
         + " callbackRuntimeMs="
         + callbackMs
         + " callbackCount="
         + callbacks
         + " maxCallbackMs="
         + ms(this.maxCallbackNanos)
         + " maxCallbackPhase="
         + this.slowCallback
         + " callbacksOver2ms="
         + session.callbacksOver2ms()
         + " callbacksOver10ms="
         + session.callbacksOver10ms()
         + "\npumpTicks="
         + session.pumpTicks()
         + " pumpTotalMs="
         + pumpMs
         + " maxPumpMs="
         + ms(session.maxPumpNanos())
         + "\nstockVariants="
         + this.stockVariants
         + " scannedRecipes="
         + this.scannedRecipes
         + " productionRecipes="
         + this.indexedProductions
         + " smithingRecipes="
         + this.indexedSmithing
         + " oversizedRecipes="
         + this.oversizedRecipes
         + "\nexcludedRecoloringRecipes="
         + this.excludedRecoloring
         + "\ndecoratedSmithingBaseRejects="
         + this.decoratedBaseRejects
         + "\nconversionCycleRejects="
         + this.conversionCycleRejects
         + "\nindexCacheHit="
         + this.indexCacheHit
         + " cachedRecipes="
         + this.cachedRecipes
         + " terminalPrunes="
         + this.terminalPrunes
         + "\nunreachableRejects="
         + this.unreachableRejects
         + " directMemoHits="
         + this.directMemoHits
         + " opaqueReachability="
         + this.opaqueReachability
         + "\nrootStockOptions="
         + this.rootStockOptions
         + " rootProductionOptions="
         + this.rootProductionOptions
         + " rootSmithingOptions="
         + this.rootSmithingOptions
         + "\noperations="
         + this.operations
         + " productionBranches="
         + this.productionBranches
         + " smithingBranches="
         + this.smithingBranches
         + " directAttempts="
         + this.directAttempts
         + " maxDepth="
         + this.maxDepth
         + " lastRecipe="
         + this.lastRecipe
         + "\nlimits: depth="
         + this.depthLimit
         + " units="
         + this.unitLimit
         + " newTypes="
         + this.typeLimit
         + " operations="
         + this.operationLimit
         + "\nslowestIndexedRecipe="
         + this.slowIndex
         + " slowestIndexMs="
         + ms(this.maxIndexNanos)
         + " indexCallbacksOver2ms="
         + this.indexOver2ms
         + " heapUsedMiB="
         + memoryMiB
         + "\nversions="
         + versions()
         + " phases="
         + this.phases
         + " lastPhase="
         + this.phase
         + "\n";
   }

   private long elapsedMs() {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.started);
   }

   private static String ms(long nanos) {
      return String.format(Locale.ROOT, "%.3f", (double)nanos / 1000000.0);
   }

   private static String versions() {
      List<String> values = new ArrayList<>();

      for (String id : List.of("minecraft", "forge", "create", "jei", "nst")) {
         String version = ModList.get().getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse("absent");
         values.add(id + ":" + version);
      }

      return String.join(",", values);
   }
}
