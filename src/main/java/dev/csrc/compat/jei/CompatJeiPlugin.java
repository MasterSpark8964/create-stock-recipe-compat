package dev.csrc.compat.jei;

import dev.csrc.CreateStockRecipeCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class CompatJeiPlugin implements IModPlugin {
    static IRecipeTransferHandlerHelper helper;
    @Override public ResourceLocation getPluginUid() {
        return new ResourceLocation(CreateStockRecipeCompat.ID, "jei");
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        helper = registration.getTransferHelper();
    }
}
