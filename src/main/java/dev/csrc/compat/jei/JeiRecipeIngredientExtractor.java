package dev.csrc.compat.jei;

import dev.csrc.CreateStockRecipeCompat;
import dev.csrc.config.ClientConfig;
import dev.csrc.mixin.SmithingTransformIngredientsAccessor;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

public final class JeiRecipeIngredientExtractor {
   public static RecipeTransferSnapshot extract(Object source, IRecipeSlotsView slots) {
      List<Ingredient> ingredients = new ArrayList<>();
      List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
      List<Ingredient> nativeInputs = nativeInputs(source);
      if ((long)nativeInputs.size() != inputs.stream().filter(slotx -> !slotx.isEmpty()).count()) {
         nativeInputs = List.of();
      }

      int inputIndex = 0;
      SmithingRecipe nst = NstRecipeCompat.recipe(source);
      if (nst != null) {
         ingredients.addAll(NstRecipeCompat.ingredientUnits(nst));
      } else {
         for (IRecipeSlotView slot : inputs) {
            if (!slot.isEmpty()) {
               List<ItemStack> candidates = slot.getItemStacks().filter(s -> !s.isEmpty()).<ItemStack>map(ItemStack::copy).toList();
               if (candidates.isEmpty()) {
                  throw new JeiRecipeIngredientExtractor.InvalidRecipe("non_item_input");
               }

               int count = candidates.stream().mapToInt(ItemStack::getCount).max().orElse(1);
               if ((Boolean)ClientConfig.DEBUG.get()) {
                  CreateStockRecipeCompat.LOGGER.info("[CSRC] INPUT candidates={}, required={}", candidates, count);
               }

               if (count > (Integer)ClientConfig.MAX_UNITS.get() - ingredients.size()) {
                  throw new JeiRecipeIngredientExtractor.InvalidRecipe("too_many_units");
               }

               if ((Boolean)ClientConfig.DEBUG.get() && candidates.stream().anyMatch(s -> s.getCount() != count)) {
                  CreateStockRecipeCompat.LOGGER.warn("[CSRC] Alternative counts differ; using {} for {}", count, source);
               }

               List<Ingredient> alternatives = new ArrayList<>();
               List<ItemStack> plain = new ArrayList<>();

               for (ItemStack candidate : candidates) {
                  candidate.setCount(1);
                  if (candidate.hasTag()) {
                     alternatives.add(StrictNBTIngredient.of(candidate));
                  } else {
                     plain.add(candidate);
                  }
               }

               if (!plain.isEmpty()) {
                  alternatives.add(Ingredient.of(plain.toArray(ItemStack[]::new)));
               }

               Ingredient ingredient = alternatives.size() == 1 ? alternatives.get(0) : CompoundIngredient.of(alternatives.toArray(Ingredient[]::new));
               if (!nativeInputs.isEmpty()) {
                  ingredient = nativeInputs.get(inputIndex);
               }

               inputIndex++;

               for (int i = 0; i < count; i++) {
                  ingredients.add(ingredient);
               }
            }
         }
      }

      if (ingredients.isEmpty()) {
         throw new JeiRecipeIngredientExtractor.InvalidRecipe("no_item_input");
      } else {
         ItemStack result = ItemStack.EMPTY;

         for (IRecipeSlotView slotx : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            result = slotx.getDisplayedItemStack()
               .filter(s -> !s.isEmpty())
               .orElseGet(() -> slotx.getItemStacks().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY));
            if (!result.isEmpty()) {
               break;
            }
         }

         if (result.isEmpty() && !(Boolean)ClientConfig.ALLOW_NON_ITEM_OUTPUTS.get()) {
            throw new JeiRecipeIngredientExtractor.InvalidRecipe("no_item_output");
         } else {
            if (result.isEmpty() || (Boolean)ClientConfig.BATCH_UNITS.get()) {
               result = materialBatchIcon();
            }

            if ((Boolean)ClientConfig.DEBUG.get()) {
               CreateStockRecipeCompat.LOGGER
                  .info(
                     "[CSRC] {}: input slots={}, expanded={}, output={}", new Object[]{source.getClass().getName(), inputs.size(), ingredients.size(), result}
                  );
            }

            return new RecipeTransferSnapshot(source, result, ingredients, result.getCount());
         }
      }
   }

   private static List<Ingredient> nativeInputs(Object source) {
      if (source.getClass() == ShapedRecipe.class || source.getClass() == ShapelessRecipe.class) {
         return ((Recipe<?>)source).getIngredients().stream().filter(i -> !i.isEmpty()).toList();
      } else if (source.getClass() == SmithingTransformRecipe.class) {
         SmithingTransformIngredientsAccessor access = (SmithingTransformIngredientsAccessor)source;
         return List.of(access.csrc$template(), access.csrc$base(), access.csrc$addition()).stream().filter(i -> !i.isEmpty()).toList();
      } else {
         return List.of();
      }
   }

   private static ItemStack materialBatchIcon() {
      ItemStack icon = new ItemStack(Items.PAPER);
      icon.setHoverName(Component.translatable("create_stock_recipe_compat.material_batch"));
      ListTag lore = new ListTag();
      lore.add(StringTag.valueOf(Serializer.toJson(Component.translatable("create_stock_recipe_compat.material_batch_hint"))));
      icon.getOrCreateTagElement("display").put("Lore", lore);
      icon.getOrCreateTag().putBoolean("create_stock_recipe_compat:material_batch", true);
      return icon;
   }

   private JeiRecipeIngredientExtractor() {
   }

   public static final class InvalidRecipe extends RuntimeException {
      public final String key;

      InvalidRecipe(String key) {
         super(key);
         this.key = key;
      }
   }
}
