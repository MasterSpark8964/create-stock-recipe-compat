package dev.csrc.compat.jei;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.function.Supplier;

final class PlanningWorker implements AutoCloseable {
   private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), r -> {
      Thread thread = new Thread(r, "CSRC recipe planning");
      thread.setDaemon(true);
      return thread;
   }, new AbortPolicy());

   <T> Future<?> submit(Supplier<T> task, CompletableFuture<T> completion) {
      return this.executor.submit(() -> {
         try {
            completion.complete(task.get());
         } catch (Throwable var3) {
            completion.completeExceptionally(var3);
         }
      });
   }

   void cancel(Future<?> task) {
      task.cancel(false);
      this.executor.purge();
   }

   int active() {
      return this.executor.getActiveCount();
   }

   int queued() {
      return this.executor.getQueue().size();
   }

   @Override
   public void close() {
      this.executor.shutdownNow();
   }
}
