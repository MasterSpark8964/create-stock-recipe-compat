package dev.csrc;

import com.mojang.logging.LogUtils;
import dev.csrc.client.ClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("create_stock_recipe_compat")
public final class CreateStockRecipeCompat {
   public static final String ID = "create_stock_recipe_compat";
   public static final Logger LOGGER = LogUtils.getLogger();

   public CreateStockRecipeCompat() {
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ClientBootstrap.initialize();
      }
   }
}
