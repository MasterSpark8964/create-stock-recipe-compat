package dev.csrc.client;

import dev.csrc.config.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

@OnlyIn(Dist.CLIENT)
public final class ClientBootstrap {
   private ClientBootstrap() {
   }

   public static void initialize() {
      ModLoadingContext.get().registerConfig(Type.CLIENT, ClientConfig.SPEC);
   }
}
