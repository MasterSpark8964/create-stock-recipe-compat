package dev.csrc.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record RecipeTransferSnapshot(Object sourceRecipe, ItemStack result,
                                     List<Ingredient> ingredients, int outputCount) {
    public RecipeTransferSnapshot {
        result = result.copy();
        ingredients = List.copyOf(ingredients);
        if (result.isEmpty() || outputCount <= 0 || ingredients.isEmpty())
            throw new IllegalArgumentException("Invalid material snapshot");
    }
    @Override public ItemStack result() { return result.copy(); }
}
