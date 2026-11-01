package nl.ljack2k.anvilous.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import nl.ljack2k.anvilous.recipe.SmithingRepairRecipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets one craft consume more than one material.
 *
 * <p>Vanilla's {@code onTake} shrinks each input slot by exactly 1, which is right for
 * {@code smithing_transform} but wrong for a repair that restores 25% per ingot and was told
 * four ingots were available. {@link SmithingRepairRecipe} already assembles the result from
 * the whole stack (capped by {@code max_per_craft}), so without this the player would get the
 * full repair while paying for a single item.
 *
 * <p>Injected at TAIL rather than HEAD because the pre-take count is still recoverable there
 * — vanilla has removed exactly one — so no state has to be carried between injections.
 *
 * <p>The class extends {@link ItemCombinerMenu} purely so {@code inputSlots} is inherited:
 * {@code @Shadow} only resolves members declared on the target class itself, and that field
 * lives on the superclass. The constructor is never called — Mixin needs it only to satisfy
 * the compiler.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {

    private SmithingMenuMixin(MenuType<?> type, int containerId, Inventory inventory,
                              ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
        throw new AssertionError("mixin constructor");
    }

    @Shadow
    @Nullable
    private RecipeHolder<SmithingRecipe> selectedRecipe;

    @Inject(method = "onTake", at = @At("TAIL"))
    private void anvilous$consumeStackedMaterial(Player player, ItemStack result, CallbackInfo ci) {
        if (this.selectedRecipe == null
                || !(this.selectedRecipe.value() instanceof SmithingRepairRecipe recipe)) {
            return;
        }

        ItemStack addition = this.inputSlots.getItem(SmithingMenu.ADDITIONAL_SLOT);
        if (addition.isEmpty()) {
            return;
        }

        // +1 undoes vanilla's own shrink, giving the count the recipe assembled against.
        int extra = Math.min(addition.getCount() + 1, recipe.maxPerCraft()) - 1;
        if (extra <= 0) {
            return;
        }

        addition.shrink(extra);
        this.inputSlots.setItem(SmithingMenu.ADDITIONAL_SLOT, addition);
    }
}
