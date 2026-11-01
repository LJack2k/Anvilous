package nl.ljack2k.anvilous.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Merging one enchantment into an item, by vanilla's anvil rules.
 *
 * <p>Shared by {@link SmithingEnchantRecipe} (a book) and {@link SmithingCombineRecipe} (the
 * sacrifice's enchantments), so the two can never disagree about what combining means.
 */
public final class EnchantMerge {
    private EnchantMerge() {
    }

    /**
     * Applies {@code incomingLevel} of {@code holder} to {@code result}, returning whether
     * anything changed.
     *
     * <p>Refuses when the item does not accept the enchantment, when it conflicts with one
     * already on the item, or when the merge would not raise the level — a craft that changes
     * nothing should not be chargeable.
     */
    public static boolean apply(ItemStack result, Holder<Enchantment> holder, int incomingLevel) {
        // Asked of the item rather than decided here, so modded tools answer for themselves.
        if (!result.supportsEnchantment(holder)) {
            return false;
        }

        ItemEnchantments existing = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> present : existing.keySet()) {
            if (!present.equals(holder) && !Enchantment.areCompatible(holder, present)) {
                return false;
            }
        }

        int current = existing.getLevel(holder);
        // Vanilla's rule: two equal levels make the next one up, otherwise the higher wins.
        int merged = Math.min(current == incomingLevel ? incomingLevel + 1 : Math.max(current, incomingLevel),
                holder.value().getMaxLevel());
        if (merged <= current) {
            return false;
        }

        // Set the component directly: EnchantmentHelper.updateEnchantments returns without
        // doing anything when the component is absent, which would be a silent no-op on an
        // item that does not already carry an empty one.
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        mutable.set(holder, merged);
        result.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return true;
    }
}
