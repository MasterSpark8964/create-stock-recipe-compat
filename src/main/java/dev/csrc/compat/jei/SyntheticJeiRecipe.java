package dev.csrc.compat.jei;

import java.util.concurrent.atomic.AtomicLong;
import dev.csrc.CreateStockRecipeCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Client-local adapter. Never registered, serialized or sent to the server. */
public final class SyntheticJeiRecipe implements Recipe<Container> {
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final ResourceLocation id = new ResourceLocation(CreateStockRecipeCompat.ID, "synthetic/" + NEXT_ID.incrementAndGet());
    private final ItemStack result;
    public SyntheticJeiRecipe(ItemStack result) { this.result = result.copy(); }
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
    @Override public RecipeType<?> getType() { return RecipeType.CRAFTING; }
}
