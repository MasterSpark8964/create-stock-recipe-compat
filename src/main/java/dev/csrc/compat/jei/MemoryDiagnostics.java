package dev.csrc.compat.jei;

import dev.csrc.CreateStockRecipeCompat;
import dev.csrc.config.ClientConfig;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.level.LevelEvent.Unload;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "create_stock_recipe_compat",
   value = {Dist.CLIENT}
)
public final class MemoryDiagnostics {
   private static final WeakRetentionTracker RETENTION = new WeakRetentionTracker();
   private static final AtomicLong DROPPED = new AtomicLong();
   private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
      Thread thread = new Thread(task, "CSRC memory diagnostics");
      thread.setDaemon(true);
      return thread;
   }, new AbortPolicy());
   private static long nextSample;
   private static long previousGc = -1L;
   private static long previousHeap = -1L;
   private static long lastTick;
   private static long maxTickGapMs;
   private static long tickGaps;
   private static final AtomicLong SEARCHES = new AtomicLong();
   private static final AtomicLong FAILURES = new AtomicLong();
   private static final AtomicLong CANCELLED = new AtomicLong();
   private static final AtomicLong SLOW_CALLBACKS = new AtomicLong();
   private static final AtomicLong MAX_CALLBACK_NS = new AtomicLong();
   private static final AtomicLong WRITE_FAILURES = new AtomicLong();
   private static volatile long lastSearchMs;
   private static volatile String lastOutcome = "none";
   private static long sampleRuntimeNs;
   private static boolean started;

   static boolean recording() {
      return enabled();
   }

   static void searchReport(String outcome, long millis) {
      if (enabled()) {
         SEARCHES.incrementAndGet();
         lastOutcome = outcome;
         lastSearchMs = millis;
         if (outcome.startsWith("error:") || outcome.startsWith("prepare-error:")) {
            FAILURES.incrementAndGet();
         }

         if (outcome.equals("cancelled")) {
            CANCELLED.incrementAndGet();
         }

         sample("search-finished");
      }
   }

   static void callback(long nanos) {
      if (nanos > 10000000L) {
         SLOW_CALLBACKS.incrementAndGet();
      }

      MAX_CALLBACK_NS.accumulateAndGet(nanos, Math::max);
   }

   private static long gcCount() {
      return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0L, b.getCollectionCount())).sum();
   }

   private static boolean enabled() {
      return ClientConfig.SPEC.isLoaded() && (Boolean)ClientConfig.MEMORY_DIAGNOSTICS.get();
   }

   private static void watch(Object object, String label) {
      if (enabled()) {
         RETENTION.watch(object, label, System.nanoTime(), gcCount());
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void opening(Opening event) {
      Screen old = Minecraft.getInstance().screen;
      if (old != null && old != event.getNewScreen()) {
         watch(old, "screen:" + old.getClass().getSimpleName());
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void logout(LoggingOut event) {
      watch(Minecraft.getInstance().level, "world:logout");
      if (enabled()) {
         sample("logout");
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void unload(Unload event) {
      if (event.getLevel().isClientSide()) {
         watch(event.getLevel(), "world:unload");
         if (enabled()) {
            sample("client-unload");
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         if (!enabled()) {
            RETENTION.clear();
            started = false;
            nextSample = 0L;
            previousGc = -1L;
            previousHeap = -1L;
            lastTick = 0L;
         } else {
            long now = System.nanoTime();
            if (lastTick != 0L && !Minecraft.getInstance().isPaused()) {
               long gapMs = (now - lastTick) / 1000000L;
               maxTickGapMs = Math.max(maxTickGapMs, gapMs);
               if (gapMs >= 250L) {
                  tickGaps++;
               }
            }

            lastTick = now;
            if (now - nextSample >= 0L) {
               nextSample = now + 30000000000L;
               sample(started ? "periodic" : "start");
               started = true;
            }
         }
      }
   }

   private static void sample(String reason) {
      try {
         long now = System.nanoTime();
         long gc = gcCount();
         MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
         MemoryUsage nonheap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
         long gcMs = ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0L, b.getCollectionTime())).sum();
         long direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream().mapToLong(b -> Math.max(0L, b.getMemoryUsed())).sum();
         String record = Instant.now()
            + " version=0.4.6 event="
            + reason
            + " heapUsedBytes="
            + heap.getUsed()
            + " heapCommittedBytes="
            + heap.getCommitted()
            + " heapMaxBytes="
            + heap.getMax()
            + " nonHeapUsedBytes="
            + nonheap.getUsed()
            + " bufferPoolBytes="
            + direct
            + " gcCount="
            + gc
            + " gcTimeMs="
            + gcMs
            + " threads="
            + ManagementFactory.getThreadMXBean().getThreadCount()
            + " gcSinceSample="
            + (previousGc < 0L ? 0L : gc - previousGc)
            + " heapDeltaBytes="
            + (previousHeap < 0L ? 0L : heap.getUsed() - previousHeap)
            + " "
            + RecursiveMaterials.memoryStatus()
            + " "
            + LegacyJeiButtons.memoryStatus()
            + " previewEntries="
            + PreviewCache.size()
            + " "
            + DiagnosticTrace.memoryStatus()
            + " memoryWriterQueued="
            + IO.getQueue().size()
            + " droppedSamples="
            + DROPPED.get()
            + " weakProbes="
            + RETENTION.size()
            + " evictedProbes="
            + RETENTION.evicted()
            + " uptimeMs="
            + ManagementFactory.getRuntimeMXBean().getUptime()
            + " searches="
            + SEARCHES.get()
            + " failedSearches="
            + FAILURES.get()
            + " cancelledSearches="
            + CANCELLED.get()
            + " lastOutcome="
            + lastOutcome
            + " lastSearchMs="
            + lastSearchMs
            + " slowCallbacksOver10ms="
            + SLOW_CALLBACKS.get()
            + " maxCallbackMs="
            + (double)MAX_CALLBACK_NS.get() / 1000000.0
            + " maxClientTickGapMs="
            + maxTickGapMs
            + " clientTickGapsOver250ms="
            + tickGaps
            + " previousSampleRuntimeMs="
            + (double)sampleRuntimeNs / 1000000.0
            + " memoryWriteFailures="
            + WRITE_FAILURES.get()
            + " observations="
            + RETENTION.sample(now, gc)
            + System.lineSeparator();
         previousGc = gc;
         previousHeap = heap.getUsed();
         sampleRuntimeNs = System.nanoTime() - now;
         Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/csrc-memory-diagnostics.txt");

         try {
            IO.execute(() -> write(path, record));
         } catch (RejectedExecutionException var14) {
            DROPPED.incrementAndGet();
         }
      } catch (RuntimeException var15) {
         CreateStockRecipeCompat.LOGGER.warn("[CSRC] Memory diagnostics sample failed", var15);
      }
   }

   private static void write(Path path, String record) {
      try {
         Files.createDirectories(path.getParent());
         if (Files.exists(path) && Files.size(path) >= 8388608L) {
            Files.move(path, path.resolveSibling("csrc-memory-diagnostics.previous.txt"), StandardCopyOption.REPLACE_EXISTING);
         }

         Files.writeString(path, record, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception var3) {
         WRITE_FAILURES.incrementAndGet();
         CreateStockRecipeCompat.LOGGER.warn("[CSRC] Could not write memory diagnostics", var3);
      }
   }

   private MemoryDiagnostics() {
   }
}
