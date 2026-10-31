package dev.csrc.compat.jei;

import dev.csrc.config.ClientConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;

public final class NstRecipeCompat {
   static final String RECIPE = "net.mcreator.nst.recipe.NetheriteSmithingTransformRecipe";
   static final String WRAPPER = "net.mcreator.nst.compat.jei.NetheriteSmithingTransformRecipeWrapper";
   static final String[] INGREDIENTS = new String[]{"template", "base", "addition", "base1", "base2", "addition1", "addition2"};
   static final int[] POSITIONS = new int[]{0, 1, 2, 4, 5, 6, 7};
   private static final ClassValue<NstRecipeCompat.Access> ACCESS = new ClassValue<NstRecipeCompat.Access>() {
      protected NstRecipeCompat.Access computeValue(Class<?> type) {
         try {
            Field[] ingredients = new Field[7];
            Field[] counts = new Field[7];

            for (int i = 0; i < 7; i++) {
               ingredients[i] = type.getField(NstRecipeCompat.INGREDIENTS[i]);
               if (ingredients[i].getType() != Ingredient.class) {
                  throw new NoSuchFieldException(NstRecipeCompat.INGREDIENTS[i]);
               }

               if (i >= 3) {
                  counts[i] = type.getField(NstRecipeCompat.INGREDIENTS[i] + "Count");
                  if (counts[i].getType() != int.class) {
                     throw new NoSuchFieldException(NstRecipeCompat.INGREDIENTS[i] + "Count");
                  }
               }
            }

            return new NstRecipeCompat.Access(ingredients, counts);
         } catch (ReflectiveOperationException var5) {
            throw NstRecipeCompat.incompatible(var5);
         }
      }
   };
   private static final ClassValue<Method> UNWRAP = new ClassValue<Method>() {
      protected Method computeValue(Class<?> type) {
         try {
            return type.getMethod("getRecipe");
         } catch (ReflectiveOperationException var3) {
            throw NstRecipeCompat.incompatible(var3);
         }
      }
   };

   public static SmithingRecipe recipe(Object source) {
      if (source == null) {
         return null;
      } else {
         Object recipe = source;
         if (source.getClass().getName().equals("net.mcreator.nst.compat.jei.NetheriteSmithingTransformRecipeWrapper")) {
            try {
               recipe = UNWRAP.get(source.getClass()).invoke(source);
            } catch (ReflectiveOperationException var3) {
               throw incompatible(var3);
            }
         }

         return recipe != null
               && recipe.getClass().getName().equals("net.mcreator.nst.recipe.NetheriteSmithingTransformRecipe")
               && recipe instanceof SmithingRecipe smithing
            ? smithing
            : null;
      }
   }

   public static List<NstRecipeCompat.Input> inputs(SmithingRecipe recipe) {
      NstRecipeCompat.Access access = ACCESS.get(recipe.getClass());
      List<NstRecipeCompat.Input> inputs = new ArrayList<>();
      int total = 0;

      try {
         for (int i = 0; i < 7; i++) {
            Ingredient ingredient = (Ingredient)access.ingredients()[i].get(recipe);
            if (ingredient == null) {
               throw incompatible(null);
            }

            int count = access.counts()[i] == null ? 1 : access.counts()[i].getInt(recipe);
            if (ingredient.isEmpty()) {
               count = 0;
            }

            if (count < 0 || count > (Integer)ClientConfig.MAX_UNITS.get() - total) {
               throw new JeiRecipeIngredientExtractor.InvalidRecipe("too_many_units");
            }

            total += count;
            inputs.add(new NstRecipeCompat.Input(ingredient, POSITIONS[i], count));
         }
      } catch (IllegalAccessException var7) {
         throw incompatible(var7);
      }

      return List.copyOf(inputs);
   }

   public static List<Ingredient> ingredientUnits(SmithingRecipe recipe) {
      List<Ingredient> units = new ArrayList<>();

      for (NstRecipeCompat.Input slot : inputs(recipe)) {
         for (int i = 0; i < slot.count(); i++) {
            units.add(slot.ingredient());
         }
      }

      return units;
   }

   private static JeiRecipeIngredientExtractor.InvalidRecipe incompatible(Throwable failure) {
      if (failure != null) {
         CompatRecipeTransfer.reportFailure(failure);
      }

      return new JeiRecipeIngredientExtractor.InvalidRecipe("nst_contract");
   }

   private NstRecipeCompat() {
   }

   private static record Access(Field[] ingredients, Field[] counts) {
   }

   public static record Input(Ingredient ingredient, int position, int count) {
   }
}
