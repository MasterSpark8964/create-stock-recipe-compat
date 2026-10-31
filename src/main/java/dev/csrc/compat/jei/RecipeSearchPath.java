package dev.csrc.compat.jei;

import java.util.HashSet;
import java.util.Set;

final class RecipeSearchPath<R, V> {
   private final Set<R> recipes;
   private final Set<V> outputs;

   private RecipeSearchPath(Set<R> recipes, Set<V> outputs) {
      this.recipes = Set.copyOf(recipes);
      this.outputs = Set.copyOf(outputs);
   }

   static <R, V> RecipeSearchPath<R, V> empty() {
      return new RecipeSearchPath<>(Set.of(), Set.of());
   }

   boolean containsRecipe(R recipe) {
      return this.recipes.contains(recipe);
   }

   boolean containsOutput(V output) {
      return this.outputs.contains(output);
   }

   RecipeSearchPath<R, V> withRecipe(R recipe) {
      HashSet<R> next = new HashSet<>(this.recipes);
      next.add(recipe);
      return new RecipeSearchPath<>(next, this.outputs);
   }

   RecipeSearchPath<R, V> withProduction(R recipe, V output) {
      HashSet<R> nextRecipes = new HashSet<>(this.recipes);
      nextRecipes.add(recipe);
      HashSet<V> nextOutputs = new HashSet<>(this.outputs);
      nextOutputs.add(output);
      return new RecipeSearchPath<>(nextRecipes, nextOutputs);
   }
}
