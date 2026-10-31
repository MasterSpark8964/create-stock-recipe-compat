package dev.csrc.compat.jei;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Function;

final class GenerationCache<K, V> {
   private long generation;
   private WeakReference<K> owner;
   private Object settings;
   private V value;

   synchronized GenerationCache.Token<K> token(K key, Object settings) {
      return new GenerationCache.Token<>(key, settings, this.generation);
   }

   synchronized boolean current(GenerationCache.Token<K> token) {
      return token.generation() == this.generation;
   }

   synchronized V get(GenerationCache.Token<K> token) {
      if (this.owner != null && this.owner.get() == null) {
         this.owner = null;
         this.settings = null;
         this.value = null;
      }

      return this.current(token) && this.owner != null && this.owner.get() == token.key() && Objects.equals(this.settings, token.settings())
         ? this.value
         : null;
   }

   synchronized void put(GenerationCache.Token<K> token, V value) {
      if (this.current(token)) {
         this.owner = new WeakReference<>(token.key());
         this.settings = token.settings();
         this.value = value;
      }
   }

   synchronized String describe(Function<V, String> summary) {
      return this.value == null ? "indexRecipes=0 indexReplayLines=0" : summary.apply(this.value);
   }

   synchronized void invalidate() {
      this.generation++;
      this.owner = null;
      this.settings = null;
      this.value = null;
   }

   static record Token<K>(K key, Object settings, long generation) {
   }
}
