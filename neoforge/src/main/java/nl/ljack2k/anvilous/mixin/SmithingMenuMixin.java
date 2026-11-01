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
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import nl.ljack2k.anvilous.recipe.MaterialCostRecipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets one craft consume more than one material.
 *
 * <p>Vanilla's {@code onTake} shrinks each input slot by exactly 1, which is right for
 * {@code smithing_transform} but wrong for a repair that was assembled from the whole stack:
 * without this, the player gets a four-diamond repair for one diamond.
 *
 * <p>How many to take is {@link MaterialCostRecipe#unitsConsumed}, the same method that
 * decides what the result is worth, so the cost and the value cannot drift apart.
 *
 * <p>The count has to be worked out at HEAD rather than TAIL: it depends on how damaged the
 * tool is, and by TAIL vanilla has already emptied the base slot.
 *
 * <p>The class extends {@link ItemCombinerMenu} purely so {@code inputSlots} is inherited —
 * {@code @Shadow} only resolves members declared on the target class itself, and that field
 * lives on the superclass. The constructor is never called; Mixin needs it only to satisfy
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

    /** Materials to remove on top of the single one vanilla takes. */
    @Unique
    private int anvilous$extraToConsume;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void anvilous$measureMaterialCost(Player player, ItemStack result, CallbackInfo ci) {
        this.anvilous$extraToConsume = 0;

        if (this.selectedRecipe == null
                || !(this.selectedRecipe.value() instanceof MaterialCostRecipe recipe)) {
            return;
        }

        SmithingRecipeInput input = new SmithingRecipeInput(
                this.inputSlots.getItem(SmithingMenu.TEMPLATE_SLOT),
                this.inputSlots.getItem(SmithingMenu.BASE_SLOT),
                this.inputSlots.getItem(SmithingMenu.ADDITIONAL_SLOT));

        this.anvilous$extraToConsume = recipe.unitsConsumed(input) - 1;
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void anvilous$consumeStackedMaterial(Player player, ItemStack result, CallbackInfo ci) {
        if (this.anvilous$extraToConsume <= 0) {
            return;
        }

        ItemStack addition = this.inputSlots.getItem(SmithingMenu.ADDITIONAL_SLOT);
        if (addition.isEmpty()) {
            return;
        }

        addition.shrink(this.anvilous$extraToConsume);
        this.inputSlots.setItem(SmithingMenu.ADDITIONAL_SLOT, addition);
        this.anvilous$extraToConsume = 0;
    }
}
