package nl.ljack2k.anvilous.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.anvilous.recipe.SmithingCombineRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Two of the same worn item becoming one better one.
 *
 * <p>The example pair is drawn at half worn each, because that is the case where the
 * arithmetic is visible: two halves and the bonus come out better than new ever was, which
 * is the whole reason the anvil has this trick.
 */
record CombineDisplay(ResourceLocation id, SmithingCombineRecipe recipe,
                      ItemStack tool, ItemStack sacrifice, ItemStack result) {

    private static final int WORN_PERCENT = 60;

    static List<CombineDisplay> explode(SmithingCombineRecipe recipe, ResourceLocation id) {
        List<CombineDisplay> displays = new ArrayList<>();
        for (ItemStack base : recipe.base().getItems()) {
            ItemStack tool = base.copy();
            if (!tool.isDamageableItem()) {
                continue;
            }
            tool.setCount(1);
            tool.setDamageValue(tool.getMaxDamage() * WORN_PERCENT / 100);

            ItemStack sacrifice = tool.copy();

            // The recipe's own sum, so the picture cannot drift from what the table does.
            int max = tool.getMaxDamage();
            int left = (max - tool.getDamageValue()) * 2 + max * recipe.bonusPercent() / 100;
            ItemStack result = tool.copy();
            result.setDamageValue(Math.max(0, max - left));

            displays.add(new CombineDisplay(id, recipe, tool, sacrifice, result));
        }
        return displays;
    }
}
