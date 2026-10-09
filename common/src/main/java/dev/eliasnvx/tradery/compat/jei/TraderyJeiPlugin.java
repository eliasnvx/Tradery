package dev.eliasnvx.tradery.compat.jei;

import dev.eliasnvx.tradery.Tradery;
import dev.eliasnvx.tradery.client.screen.DisplayScreen;
import dev.eliasnvx.tradery.client.screen.GhostTargets;
import dev.eliasnvx.tradery.client.screen.VendingOwnerScreen;
import dev.eliasnvx.tradery.compat.CompatInfo;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** JEI: drag items onto sample slots (goods, price, facade, display) and information pages. */
@JeiPlugin
public final class TraderyJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Tradery.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(VendingOwnerScreen.class, new SampleHandler<>());
        registration.addGhostIngredientHandler(DisplayScreen.class, new SampleHandler<>());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (CompatInfo.Page page : CompatInfo.pages()) {
            registration.addItemStackInfo(page.items(), page.text());
        }
    }

    private static final class SampleHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
            ItemStack stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                return List.of();
            }
            List<Target<I>> targets = new ArrayList<>();
            for (GhostTargets.Target target : GhostTargets.of(screen)) {
                if (!GhostTargets.accepts(target, stack)) {
                    continue;
                }
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(target.x(), target.y(), 16, 16);
                    }

                    @Override
                    public void accept(I ignored) {
                        GhostTargets.drop(screen, target, stack);
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
