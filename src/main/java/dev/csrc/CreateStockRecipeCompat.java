package dev.csrc;

import com.mojang.logging.LogUtils;
import dev.csrc.config.ClientConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;

@Mod(CreateStockRecipeCompat.ID)
public final class CreateStockRecipeCompat {
    public static final String ID = "create_stock_recipe_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateStockRecipeCompat() {
        if (FMLEnvironment.dist == Dist.CLIENT)
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
