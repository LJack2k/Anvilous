package nl.ljack2k.anvilous.recipe;

import net.minecraft.world.item.crafting.SmithingRecipeInput;

/**
 * A smithing recipe that charges more than one material per craft.
 *
 * <p>Vanilla's {@code SmithingMenu} removes exactly one item from each input slot when the
 * result is taken. The mixin on that menu asks this interface instead, so repair and
 * enchanting can price a craft differently while sharing one place that is allowed to decide
 * what it costs — the same method the recipe uses to build the result, so the value and the
 * price can never drift apart.
 */
public interface MaterialCostRecipe {

    /**
     * How many items this craft takes from the addition slot.
     *
     * <p>Called before anything has been consumed, so the input is the full pre-craft state.
     * Never returns less than 1, since vanilla always takes one.
     */
    int unitsConsumed(SmithingRecipeInput input);
}
