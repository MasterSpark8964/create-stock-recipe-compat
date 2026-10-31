package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public final class JeiMaterialCraftableBigItemStack extends CraftableBigItemStack {
   private final RecipeTransferSnapshot snapshot;
   private String signature;

   public JeiMaterialCraftableBigItemStack(RecipeTransferSnapshot snapshot, Recipe<?> backing) {
      super(snapshot.result(), backing);
      this.snapshot = snapshot;
      this.count = 0;
   }

   public List<Ingredient> getIngredients() {
      return this.snapshot.ingredients();
   }

   public int getOutputCount(Level level) {
      return this.snapshot.outputCount();
   }

   public Object getSourceRecipe() {
      return this.snapshot.sourceRecipe();
   }

   public void setSourceSignature(String signature) {
      this.signature = signature;
   }

   public String signature() {
      if (this.signature == null) {
         this.signature = CompatRecipeTransfer.signature(this.snapshot.result(), this.snapshot.ingredients());
      }

      return this.signature;
   }
}
