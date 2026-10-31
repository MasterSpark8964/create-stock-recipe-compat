package dev.csrc.compat.jei;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class WeakRetentionTracker {
   private final ArrayDeque<WeakRetentionTracker.Probe> probes = new ArrayDeque<>();
   private long evicted;

   void watch(Object object, String label, long now, long gc) {
      if (object != null) {
         for (WeakRetentionTracker.Probe p : this.probes) {
            if (p.reference().get() == object) {
               return;
            }
         }

         if (this.probes.size() >= 64) {
            this.probes.removeFirst();
            this.evicted++;
         }

         this.probes
            .addLast(new WeakRetentionTracker.Probe(new WeakReference<>(object), label + "#" + Integer.toHexString(System.identityHashCode(object)), now, gc));
      }
   }

   List<String> sample(long now, long gc) {
      ArrayList<String> records = new ArrayList<>();
      Iterator<WeakRetentionTracker.Probe> iterator = this.probes.iterator();

      while (iterator.hasNext()) {
         WeakRetentionTracker.Probe p = iterator.next();
         if (p.reference().get() == null) {
            records.add("released=" + p.label());
            iterator.remove();
         } else if (now - p.time() >= 120000000000L && gc - p.gc() >= 3L) {
            records.add("retainedCandidate=" + p.label() + " ageSeconds=" + (now - p.time()) / 1000000000L + " gcSinceWatch=" + (gc - p.gc()));
         }
      }

      return records;
   }

   int size() {
      return this.probes.size();
   }

   long evicted() {
      return this.evicted;
   }

   void clear() {
      this.probes.clear();
   }

   private static record Probe(WeakReference<Object> reference, String label, long time, long gc) {
   }
}
