package dev.csrc.compat.jei;

import java.util.List;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public final class JeiMaterialCraftableBigItemStack extends CraftableBigItemStack {
    private final RecipeTransferSnapshot snapshot;
    private final String signature;
    public JeiMaterialCraftableBigItemStack(RecipeTransferSnapshot snapshot, Recipe<?> backing) {
        super(snapshot.result(), backing);
        this.snapshot = snapshot;
        this.signature = CompatRecipeTransfer.signature(snapshot.result(), snapshot.ingredients());
        // BigItemStack starts at 1, but this entry has not reserved any materials yet.
        count = 0;
    }
    @Override public List<Ingredient> getIngredients() { return snapshot.ingredients(); }
    @Override public int getOutputCount(Level level) { return snapshot.outputCount(); }
    public Object getSourceRecipe() { return snapshot.sourceRecipe(); }
    public String signature() { return signature; }
}
