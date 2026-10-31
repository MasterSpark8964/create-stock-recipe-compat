package dev.csrc.compat.jei;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import dev.csrc.config.ClientConfig;
import dev.csrc.mixin.LegacyRecipeGuiLayoutsAccessor;
import dev.csrc.mixin.LegacyRecipesGuiAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.client.event.ScreenEvent.Init.Post;
import net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "create_stock_recipe_compat",
   value = {Dist.CLIENT}
)
public final class LegacyJeiButtons {
   private static final PlanningWorker WORKER = new PlanningWorker();
   private static Future<?> workHandle;
   private static SearchSession session;
   private static CompletableFuture<CompatRecipeTransfer.PlanResult> result;
   private static LegacyJeiButtons.View planningView;
   private static StockKeeperRequestScreen planningParent;
   private static RecipeTransferSnapshot planningRoot;
   private static boolean planningMaximum;
   private static Screen owner;
   private static LegacyJeiButtons.View notificationView;
   private static long notificationUntil;
   private static final IdentityHashMap<IRecipeLayoutDrawable<?>, LegacyJeiButtons.View> views = new IdentityHashMap<>();
   private static List<LegacyJeiButtons.View> visible = List.of();
   private static LegacyJeiButtons.View pressed;
   private static boolean captured;
   private static final List<Component> TOOLTIP = List.of(
      Component.translatable("create_stock_recipe_compat.button.dependencies"), Component.translatable("create_stock_recipe_compat.button.dependencies_hint")
   );

   static String memoryStatus() {
      return "searchActive="
         + (session != null)
         + " workerActive="
         + WORKER.active()
         + " searchAgeMs="
         + (session == null ? 0L : session.ageMillis())
         + " workerQueued="
         + WORKER.queued()
         + " callbackPending="
         + (session != null && session.hasPending())
         + " views="
         + views.size()
         + " screenHeld="
         + (owner != null);
   }

   public static boolean enabled() {
      return (Boolean)ClientConfig.ENABLE.get() && (Boolean)ClientConfig.RECURSIVE_MATERIALS.get();
   }

   public static boolean isStockKeeperContext() {
      Minecraft minecraft = Minecraft.getInstance();
      Screen screen = minecraft.screen;
      if (screen instanceof LegacyRecipesGuiAccessor access) {
         screen = access.csrc$parentScreen();
      }

      if (enabled()
         && screen instanceof StockKeeperRequestScreen parent
         && minecraft.player != null
         && minecraft.player.containerMenu == parent.getMenu()
         && ((StockKeeperRequestMenu)parent.getMenu()).screenReference == parent) {
         return true;
      }

      return false;
   }

   private static StockKeeperRequestScreen parent(Screen screen) {
      if (screen instanceof LegacyRecipesGuiAccessor access && enabled()) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.screen == screen && minecraft.player != null) {
            if (!(access.csrc$parentScreen() instanceof StockKeeperRequestScreen parent)) {
               return null;
            }

            return minecraft.player.containerMenu == parent.getMenu() && ((StockKeeperRequestMenu)parent.getMenu()).screenReference == parent ? parent : null;
         }

         return null;
      }

      return null;
   }

   private static void cancelSearch() {
      if (session != null) {
         session.cancel();
         session.report("cancelled");
      }

      if (workHandle != null) {
         WORKER.cancel(workHandle);
         workHandle = null;
      }

      session = null;
      result = null;
      planningView = null;
      planningParent = null;
      planningRoot = null;
   }

   static void clear() {
      cancelSearch();
      PreviewCache.clear();
      owner = null;
      notificationView = null;
      notificationUntil = 0L;
      views.clear();
      visible = List.of();
      pressed = null;
      captured = false;
   }

   private static boolean sync(Screen screen) {
      if (parent(screen) == null) {
         clear();
         return false;
      } else {
         if (owner != screen) {
            clear();
            owner = screen;
         }

         List<RecipeLayoutWithButtons<?>> layouts = ((LegacyRecipeGuiLayoutsAccessor)((LegacyRecipesGuiAccessor)screen).csrc$layouts()).csrc$visibleLayouts();
         int index = 0;
         boolean unchanged = true;

         for (RecipeLayoutWithButtons<?> entry : layouts) {
            IRecipeLayoutDrawable<?> layout = entry.recipeLayout();
            Object recipe = layout.getRecipe();
            if (WorkstationRecipes.supportsLayout(layout)) {
               if (index >= visible.size() || visible.get(index).layout != layout || visible.get(index).recipe != recipe) {
                  unchanged = false;
                  break;
               }

               index++;
            }
         }

         if (unchanged && index == visible.size()) {
            for (LegacyJeiButtons.View view : visible) {
               view.updateBounds();
            }

            return true;
         } else {
            IdentityHashMap<IRecipeLayoutDrawable<?>, LegacyJeiButtons.View> current = new IdentityHashMap<>();
            List<LegacyJeiButtons.View> ordered = new ArrayList<>();

            for (RecipeLayoutWithButtons<?> entryx : layouts) {
               IRecipeLayoutDrawable<?> layout = entryx.recipeLayout();
               Object recipe = layout.getRecipe();
               if (WorkstationRecipes.supportsLayout(layout)) {
                  LegacyJeiButtons.View view = views.get(layout);
                  if (view == null || view.recipe != recipe) {
                     view = new LegacyJeiButtons.View(layout);
                  }

                  view.updateBounds();
                  current.put(layout, view);
                  ordered.add(view);
               }
            }

            views.clear();
            views.putAll(current);
            visible = ordered;
            if (planningView != null && views.get(planningView.layout) != planningView) {
               cancelSearch();
            }

            return true;
         }
      }
   }

   private static LegacyJeiButtons.View hit(double x, double y) {
      for (LegacyJeiButtons.View view : visible) {
         if (view.contains(x, y)) {
            return view;
         }
      }

      return null;
   }

   @SubscribeEvent
   public static void onOpening(Opening event) {
      clear();
   }

   @SubscribeEvent
   public static void onInit(Post event) {
      clear();
   }

   @SubscribeEvent
   public static void onRender(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
      if (sync(event.getScreen())) {
         GuiGraphics graphics = event.getGuiGraphics();
         graphics.pose().pushPose();

         try {
            graphics.pose().translate(0.0F, 0.0F, 500.0F);

            for (LegacyJeiButtons.View view : visible) {
               if (!view.contains((double)event.getMouseX(), (double)event.getMouseY())) {
                  view.suppressTooltipUntilExit = false;
               }

               view.button.setForcePressed(view == pressed);
               view.button.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
            }

            LegacyJeiButtons.View hover = hit((double)event.getMouseX(), (double)event.getMouseY());
            if (hover != null && hover.suppressTooltipUntilExit) {
               hover = null;
            }

            if (notificationView != null && (views.get(notificationView.layout) != notificationView || System.nanoTime() - notificationUntil >= 0L)) {
               notificationView = null;
            }

            LegacyJeiButtons.View tooltipView = notificationView != null && !notificationView.error.isEmpty()
               ? notificationView
               : (hover != null ? hover : notificationView);
            if (tooltipView != null) {
               graphics.pose().translate(0.0F, 0.0F, 500.0F);
               graphics.renderComponentTooltip(
                  Minecraft.getInstance().font,
                  tooltipView == planningView
                     ? List.of(TOOLTIP.get(0), Component.translatable("create_stock_recipe_compat.button.searching"))
                     : (tooltipView.error.isEmpty() ? TOOLTIP : tooltipView.error),
                  tooltipView == hover ? event.getMouseX() : tooltipView.button.getX(),
                  tooltipView == hover ? event.getMouseY() : tooltipView.button.getY()
               );
            }
         } finally {
            graphics.pose().popPose();
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onPress(Pre event) {
      if (event.getButton() == 0 && sync(event.getScreen())) {
         LegacyJeiButtons.View target = hit(event.getMouseX(), event.getMouseY());
         if (target != null) {
            pressed = target;
            captured = true;
            target.button.playDownSound(Minecraft.getInstance().getSoundManager());
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onRelease(net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre event) {
      if (event.getButton() == 0 && captured) {
         LegacyJeiButtons.View target = pressed;
         Screen pressedOwner = owner;
         captured = false;
         pressed = null;
         event.setCanceled(true);
         if (event.getScreen() == pressedOwner && sync(event.getScreen())) {
            if (target != null
               && views.get(target.layout) == target
               && target.layout.getRecipe() == target.recipe
               && target.contains(event.getMouseX(), event.getMouseY())) {
               StockKeeperRequestScreen parent = parent(event.getScreen());
               if (parent != null) {
                  if (!target.error.isEmpty()) {
                     target.error = List.of();
                     target.suppressTooltipUntilExit = true;
                     if (notificationView == target) {
                        notificationView = null;
                     }
                  } else {
                     if (session != null) {
                        boolean same = target == planningView;
                        cancelSearch();
                        if (same) {
                           return;
                        }
                     }

                     try {
                        session = new SearchSession(target.recipe);
                        if (session.diagnostics != null) {
                           session.diagnostics.stage("prepare");
                        }

                        RecipeTransferSnapshot root = CompatRecipeTransfer.prepare(target.recipe, target.layout.getRecipeSlotsView());
                        if (session.diagnostics != null) {
                           session.diagnostics.prepared(root);
                        }

                        notificationView = null;
                        target.suppressTooltipUntilExit = false;
                        SearchSession active = session;
                        planningView = target;
                        planningParent = parent;
                        planningRoot = root;
                        notificationView = target;
                        notificationUntil = System.nanoTime() + 30000000000L;
                        planningMaximum = Screen.hasShiftDown();
                        CompletableFuture<CompatRecipeTransfer.PlanResult> completion = new CompletableFuture<>();
                        result = completion;
                        workHandle = WORKER.submit(() -> PlanningExecution.run(active, () -> CompatRecipeTransfer.plan(parent, root)), completion);
                     } catch (LinkageError | RuntimeException var7) {
                        if (session != null) {
                           session.report("prepare-error:" + (var7 instanceof JeiRecipeIngredientExtractor.InvalidRecipe invalid ? invalid.key : "internal"));
                        }

                        cancelSearch();
                        if (!(var7 instanceof JeiRecipeIngredientExtractor.InvalidRecipe)) {
                           CompatRecipeTransfer.reportFailure(var7);
                        }

                        String key = var7 instanceof JeiRecipeIngredientExtractor.InvalidRecipe invalid ? invalid.key : "internal";
                        showError(target, key);
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Screen current = Minecraft.getInstance().screen;
         if (owner != null && (current != owner || parent(current) == null)) {
            clear();
         }

         if (session != null) {
            if (current == owner && sync(current) && session != null && parent(current) == planningParent) {
               SearchSession active = session;
               CompletableFuture<CompatRecipeTransfer.PlanResult> future = result;
               active.pump(2000000L);
               if (session == active && result == future && future != null && future.isDone()) {
                  LegacyJeiButtons.View target = planningView;

                  try {
                     CompatRecipeTransfer.PlanResult planned = result.join();
                     String error = planned.error();
                     if (error == null) {
                        if (active.diagnostics != null) {
                           active.diagnostics.stage("commit");
                        }

                        error = CompatRecipeTransfer.commit(planningParent, planningRoot, planned.snapshot(), planningMaximum);
                     }

                     if (error != null) {
                        active.report("error:" + error);
                        showError(target, error);
                        return;
                     }

                     active.report("success");
                     cancelSearch();
                     current.onClose();
                  } catch (CancellationException var11) {
                     return;
                  } catch (LinkageError | RuntimeException var12) {
                     CompatRecipeTransfer.reportFailure(var12);
                     active.report("error:internal");
                     showError(target, "internal");
                     return;
                  } finally {
                     cancelSearch();
                  }
               }
            } else {
               cancelSearch();
            }
         }
      }
   }

   private static void showError(LegacyJeiButtons.View target, String key) {
      target.error = List.of(TOOLTIP.get(0), Component.translatable("create_stock_recipe_compat.error." + key));
      notificationView = target;
      notificationUntil = System.nanoTime() + 10000000000L;
   }

   private LegacyJeiButtons() {
   }

   private static final class View {
      final IRecipeLayoutDrawable<?> layout;
      final Object recipe;
      List<Component> error = List.of();
      boolean suppressTooltipUntilExit;
      final GuiIconButton button = new GuiIconButton(new IDrawable() {
         public int getWidth() {
            return 7;
         }

         public int getHeight() {
            return 8;
         }

         public void draw(GuiGraphics graphics, int x, int y) {
            int color = -2039584;
            graphics.fill(x + 3, y + 1, x + 4, y + 8, color);
            graphics.fill(x, y + 2, x + 7, y + 3, color);
            graphics.fill(x, y, x + 1, y + 3, color);
            graphics.fill(x + 6, y, x + 7, y + 3, color);
         }
      }, ignored -> {
      });

      View(IRecipeLayoutDrawable<?> layout) {
         this.layout = layout;
         this.recipe = layout.getRecipe();
      }

      void updateBounds() {
         Rect2i rect = this.layout.getRect();
         Rect2i bookmark = this.layout.getRecipeBookmarkButtonArea();
         this.button
            .updateBounds(
               new Rect2i(
                  rect.getX() + bookmark.getX(),
                  rect.getY() + bookmark.getY() - bookmark.getHeight() - 2,
                  bookmark.getWidth(),
                  bookmark.getHeight()
               )
            );
      }

      boolean contains(double x, double y) {
         return x >= (double)this.button.getX()
            && x < (double)(this.button.getX() + this.button.getWidth())
            && y >= (double)this.button.getY()
            && y < (double)(this.button.getY() + this.button.getHeight());
      }
   }
}
