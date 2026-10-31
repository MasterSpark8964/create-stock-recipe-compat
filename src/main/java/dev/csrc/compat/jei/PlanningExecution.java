package dev.csrc.compat.jei;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.item.crafting.Recipe;

final class PlanningExecution {
   private static final ThreadLocal<SearchSession> CURRENT = new ThreadLocal<>();

   static DiagnosticTrace diagnostics() {
      SearchSession session = CURRENT.get();
      return session == null ? null : session.diagnostics;
   }

   static <T> T client(Supplier<T> callback) {
      SearchSession session = CURRENT.get();
      if (session == null) {
         return callback.get();
      } else {
         try {
            return session.call(callback);
         } catch (CompletionException var5) {
            if (var5.getCause() instanceof RuntimeException runtime) {
               throw runtime;
            } else if (var5.getCause() instanceof Error error) {
               throw error;
            } else {
               throw var5;
            }
         }
      }
   }

   static void client(Runnable callback) {
      client(() -> {
         callback.run();
         return null;
      });
   }

   static <T> T run(SearchSession session, Supplier<T> task) {
      CURRENT.set(session);

      Object var2;
      try {
         var2 = task.get();
      } finally {
         CURRENT.remove();
      }

      return (T)var2;
   }

   static <T> void eachClient(List<T> items, Consumer<T> callback, Runnable checkpoint) {
      int cursor = 0;
      DiagnosticTrace trace = diagnostics();

      while (cursor < items.size()) {
         checkpoint.run();
         int from = cursor;
         cursor = client(() -> {
            long until = System.nanoTime() + 250000L;
            int next = from;

            do {
               T item = items.get(next++);
               long started = item instanceof Recipe && trace != null && "index".equals(trace.phase) ? System.nanoTime() : 0L;

               try {
                  callback.accept(item);
               } finally {
                  if (started != 0L && trace != null) {
                     trace.indexRecipe((Recipe<?>)item, System.nanoTime() - started);
                  }
               }
            } while (next < items.size() && next - from < 128 && System.nanoTime() - until < 0L);

            return next;
         });
      }
   }

   static long suspendedNanos() {
      SearchSession session = CURRENT.get();
      if (session == null) {
         return 0L;
      } else {
         session.check();
         return session.suspendedNanos();
      }
   }

   private PlanningExecution() {
   }
}
