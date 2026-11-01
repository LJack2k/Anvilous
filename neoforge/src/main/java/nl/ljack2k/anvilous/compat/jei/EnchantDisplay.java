package nl.ljack2k.anvilous.compat.jei;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import nl.ljack2k.anvilous.recipe.EnchantMaterial;
import nl.ljack2k.anvilous.recipe.SmithingEnchantRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Applying one enchantment at one level, for a price.
 *
 * <p>One entry per level rather than one per enchantment: the price is per level, so
 * Sharpness I and Sharpness V cost different things and a player asking "what does V cost"
 * deserves an answer rather than a slot that cycles. The tools cycle instead, since the
 * price does not depend on which one it goes on.
 */
record EnchantDisplay(ResourceLocation id, SmithingEnchantRecipe recipe,
                      Holder<Enchantment> enchantment, int level,
                      ItemStack book, List<ItemStack> tools, List<ItemStack> enchanted) {

    static List<EnchantDisplay> explode(SmithingEnchantRecipe recipe,
                                        Holder<Enchantment> enchantment,
                                        ResourceLocation id) {
        List<ItemStack> tools = new ArrayList<>();
        for (ItemStack base : recipe.base().getItems()) {
            ItemStack tool = base.copy();
            tool.setCount(1);
            // Asked of the item, so a modded tool answers for itself.
            if (tool.supportsEnchantment(enchantment)) {
                tools.add(tool);
            }
        }
        if (tools.isEmpty()) {
            return List.of();
        }

        List<EnchantDisplay> displays = new ArrayList<>();
        for (int level = 1; level <= enchantment.value().getMaxLevel(); level++) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            int shown = level;
            // updateEnchantments routes a book to stored_enchantments by itself; writing the
            // component directly here would put it in the wrong one.
            EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(enchantment, shown));

            List<ItemStack> enchanted = new ArrayList<>(tools.size());
            for (ItemStack tool : tools) {
                ItemStack result = tool.copy();
                result.enchant(enchantment, shown);
                enchanted.add(result);
            }

            displays.add(new EnchantDisplay(id, recipe, enchantment, level, book,
                    List.copyOf(tools), enchanted));
        }
        return displays;
    }

    /** Items this material costs at this display's level, which the slot shows as a count. */
    int cost(EnchantMaterial material) {
        return material.costFor(this.level);
    }

    EnchantMaterial materialFor(ItemStack stack) {
        return this.recipe.materials().stream()
                .filter(material -> material.test(stack))
                .findFirst()
                .orElse(this.recipe.materials().getFirst());
    }
}
