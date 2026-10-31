package dev.csrc.compat.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class CandidateIndex<K, V> {
   private final IdentityHashMap<K, List<V>> buckets = new IdentityHashMap<>();
   private final IdentityHashMap<V, Integer> order = new IdentityHashMap<>();
   private final List<V> fallback = new ArrayList<>();

   void add(K key, V value) {
      this.order.computeIfAbsent(value, ignored -> this.order.size());
      this.buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
   }

   void addFallback(V value) {
      this.order.computeIfAbsent(value, ignored -> this.order.size());
      this.fallback.add(value);
   }

   List<V> select(List<K> keys) {
      Set<V> selected = Collections.newSetFromMap(new IdentityHashMap<>());
      selected.addAll(this.fallback);

      for (K key : keys) {
         selected.addAll(this.buckets.getOrDefault(key, List.of()));
      }

      List<V> result = new ArrayList<>(selected);
      result.sort(Comparator.comparingInt(this.order::get));
      return result;
   }
}
