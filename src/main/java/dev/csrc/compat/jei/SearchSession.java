package dev.csrc.compat.jei;

import dev.csrc.config.ClientConfig;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

final class SearchSession {
   private final ConcurrentLinkedQueue<SearchSession.Request<?>> queue = new ConcurrentLinkedQueue<>();
   private final AtomicBoolean reported = new AtomicBoolean();
   private volatile boolean cancelled;
   private volatile CompletableFuture<?> pending;
   private volatile long suspendedNanos;
   private final long started = System.nanoTime();
   final DiagnosticTrace diagnostics;
   private final AtomicLong callbackCount = new AtomicLong();
   private final AtomicLong callbackNanos = new AtomicLong();
   private final AtomicLong callbacksOver2ms = new AtomicLong();
   private final AtomicLong callbacksOver10ms = new AtomicLong();
   private final AtomicLong pumpTicks = new AtomicLong();
   private final AtomicLong pumpNanos = new AtomicLong();
   private volatile long maxPumpNanos;

   SearchSession(Object recipe) {
      this(recipe, (Boolean)ClientConfig.DIAGNOSTICS.get());
   }

   SearchSession(Object recipe, boolean profiling) {
      this.diagnostics = profiling ? new DiagnosticTrace(recipe) : null;
   }

   SearchSession() {
      this(null, false);
   }

   <T> T call(Supplier<T> callback) {
      this.check();
      CompletableFuture<T> future = new CompletableFuture<>();
      long before = System.nanoTime();
      synchronized (this) {
         this.check();
         this.pending = future;
         this.queue.add(new SearchSession.Request<>(callback, future));
      }

      Object var5;
      try {
         var5 = future.join();
      } finally {
         this.suspendedNanos = this.suspendedNanos + (System.nanoTime() - before);
         this.pending = null;
      }

      return (T)var5;
   }

   void pump(long nanos) {
      if (this.diagnostics == null) {
         this.pumpQueued(nanos);
      } else {
         long before = System.nanoTime();

         try {
            this.pumpQueued(nanos);
         } finally {
            long elapsed = System.nanoTime() - before;
            this.pumpTicks.incrementAndGet();
            this.pumpNanos.addAndGet(elapsed);
            if (elapsed > this.maxPumpNanos) {
               this.maxPumpNanos = elapsed;
            }
         }
      }
   }

   private void pumpQueued(long nanos) {
      long until = System.nanoTime() + nanos;
      boolean ran = false;
      long idleUntil = 0L;

      while (!this.cancelled && System.nanoTime() - until < 0L) {
         SearchSession.Request<?> request = this.queue.poll();
         if (request == null) {
            if (!ran) {
               return;
            }

            if (idleUntil == 0L) {
               idleUntil = System.nanoTime() + 200000L;
            }

            if (System.nanoTime() - idleUntil >= 0L) {
               return;
            }

            Thread.onSpinWait();
         } else {
            ran = true;
            idleUntil = 0L;
            boolean memoryRecording = MemoryDiagnostics.recording();
            long callbackStarted = this.diagnostics == null && !memoryRecording ? 0L : System.nanoTime();
            run(request);
            long callbackElapsed = callbackStarted == 0L ? 0L : System.nanoTime() - callbackStarted;
            if (memoryRecording) {
               MemoryDiagnostics.callback(callbackElapsed);
            }

            if (this.diagnostics != null) {
               this.callbackCount.incrementAndGet();
               this.callbackNanos.addAndGet(callbackElapsed);
               if (callbackElapsed > 2000000L) {
                  this.callbacksOver2ms.incrementAndGet();
               }

               if (callbackElapsed > 10000000L) {
                  this.callbacksOver10ms.incrementAndGet();
               }

               this.diagnostics.callback(callbackElapsed, this.diagnostics.phase);
            }
         }
      }
   }

   private static <T> void run(SearchSession.Request<T> request) {
      if (!request.result().isDone()) {
         try {
            request.result().complete(request.callback().get());
         } catch (Throwable var2) {
            request.result().completeExceptionally(var2);
         }
      }
   }

   boolean hasPending() {
      return !this.queue.isEmpty();
   }

   synchronized void cancel() {
      this.cancelled = true;
      CompletableFuture<?> waiting = this.pending;
      if (waiting != null) {
         waiting.cancel(false);
      }

      this.queue.clear();
   }

   void check() {
      if (this.cancelled) {
         throw new CancellationException();
      } else if (System.nanoTime() - this.started > TimeUnit.SECONDS.toNanos(30L)) {
         throw new RecursiveMaterials.Limit("dependency_time_limit");
      }
   }

   long suspendedNanos() {
      return this.suspendedNanos;
   }

   long callbackCount() {
      return this.callbackCount.get();
   }

   long callbackNanos() {
      return this.callbackNanos.get();
   }

   long callbacksOver2ms() {
      return this.callbacksOver2ms.get();
   }

   long callbacksOver10ms() {
      return this.callbacksOver10ms.get();
   }

   long pumpTicks() {
      return this.pumpTicks.get();
   }

   long pumpNanos() {
      return this.pumpNanos.get();
   }

   long maxPumpNanos() {
      return this.maxPumpNanos;
   }

   long ageMillis() {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.started);
   }

   void report(String outcome) {
      if (this.reported.compareAndSet(false, true)) {
         MemoryDiagnostics.searchReport(outcome, this.ageMillis());
         if (this.diagnostics != null) {
            this.diagnostics.save(outcome, this);
         }
      }
   }

   private static record Request<T>(Supplier<T> callback, CompletableFuture<T> result) {
   }
}
