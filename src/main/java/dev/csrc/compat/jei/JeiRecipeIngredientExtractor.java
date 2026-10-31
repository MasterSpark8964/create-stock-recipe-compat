package dev.csrc.compat.jei;

import java.util.ArrayList;
import java.util.List;
import dev.csrc.CreateStockRecipeCompat;
import dev.csrc.config.ClientConfig;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.crafting.CompoundIngredient;

public final class JeiRecipeIngredientExtractor {
    public static final class InvalidRecipe extends RuntimeException {
        public final String key;
        InvalidRecipe(String key) { super(key); this.key = key; }
    }

    public static RecipeTransferSnapshot extract(Object source, IRecipeSlotsView slots) {
        List<Ingredient> ingredients = new ArrayList<>();
        var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        for (var slot : inputs) {
            if (slot.isEmpty()) continue;
            var candidates = slot.getItemStacks().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
            // A nonempty fluid/custom slot must never silently become a partial order.
            if (candidates.isEmpty()) throw new InvalidRecipe("non_item_input");
            int count = candidates.stream().mapToInt(ItemStack::getCount).max().orElse(1);
            if (ClientConfig.DEBUG.get())
                CreateStockRecipeCompat.LOGGER.info("[CSRC] INPUT candidates={}, required={}", candidates, count);
            if (count > ClientConfig.MAX_UNITS.get() - ingredients.size())
                throw new InvalidRecipe("too_many_units");
            if (ClientConfig.DEBUG.get() && candidates.stream().anyMatch(s -> s.getCount() != count))
                CreateStockRecipeCompat.LOGGER.warn("[CSRC] Alternative counts differ; using {} for {}", count, source);
            // Vanilla Ingredient ignores NBT. Preserve explicit NBT requirements, while plain
            // item alternatives retain vanilla item matching (e.g. an enchanted smithing base).
            List<Ingredient> alternatives = new ArrayList<>();
            for (ItemStack candidate : candidates) {
                candidate.setCount(1);
                alternatives.add(candidate.hasTag() ? StrictNBTIngredient.of(candidate) : Ingredient.of(candidate));
            }
            Ingredient ingredient = alternatives.size() == 1 ? alternatives.get(0)
                : CompoundIngredient.of(alternatives.toArray(Ingredient[]::new));
            for (int i = 0; i < count; i++) ingredients.add(ingredient);
        }
        if (ingredients.isEmpty()) throw new InvalidRecipe("no_item_input");
        ItemStack result = ItemStack.EMPTY;
        for (var slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            result = slot.getDisplayedItemStack().filter(s -> !s.isEmpty())
                .orElseGet(() -> slot.getItemStacks().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY));
            if (!result.isEmpty()) break;
        }
        if (result.isEmpty() && !ClientConfig.ALLOW_NON_ITEM_OUTPUTS.get())
            throw new InvalidRecipe("no_item_output");
        if (result.isEmpty() || ClientConfig.BATCH_UNITS.get()) result = materialBatchIcon();
        if (ClientConfig.DEBUG.get())
            CreateStockRecipeCompat.LOGGER.info("[CSRC] {}: input slots={}, expanded={}, output={}",
                source.getClass().getName(), inputs.size(), ingredients.size(), result);
        return new RecipeTransferSnapshot(source, result, ingredients, result.getCount());
    }

    /** A client-only display marker. Only INPUT ingredients enter the actual package order. */
    private static ItemStack materialBatchIcon() {
        ItemStack icon = new ItemStack(Items.PAPER);
        icon.setHoverName(Component.translatable(CreateStockRecipeCompat.ID + ".material_batch"));
        var lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable(CreateStockRecipeCompat.ID + ".material_batch_hint"))));
        icon.getOrCreateTagElement("display").put("Lore", lore);
        icon.getOrCreateTag().putBoolean(CreateStockRecipeCompat.ID + ":material_batch", true);
        return icon;
    }
    private JeiRecipeIngredientExtractor() {}
}
