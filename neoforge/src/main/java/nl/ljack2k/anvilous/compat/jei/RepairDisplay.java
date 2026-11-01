package nl.ljack2k.anvilous.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.anvilous.recipe.RepairMaterial;
import nl.ljack2k.anvilous.recipe.SmithingRepairRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * One repair, as a player would see it: this tool, worn out, and what puts it right.
 *
 * <p>A recipe can name a whole tag of tools, but how many items a repair takes is a
 * property of the tool, so each one gets its own entry. Looking up a diamond pickaxe then
 * finds the diamond pickaxe's repair rather than "pickaxes, somewhere in here".
 */
record RepairDisplay(ResourceLocation id, SmithingRepairRecipe recipe,
                     ItemStack damaged, ItemStack repaired) {

    /** How worn the example tool is drawn. Nearly spent, so the repair is the obvious part. */
    private static final int WORN_PERCENT = 90;

    static List<RepairDisplay> explode(SmithingRepairRecipe recipe, ResourceLocation id) {
        List<RepairDisplay> displays = new ArrayList<>();
        for (ItemStack base : recipe.base().getItems()) {
            ItemStack damaged = base.copy();
            if (!damaged.isDamageableItem()) {
                // A recipe can name anything; only damageable items can be repaired, and the
                // recipe itself refuses the rest at the table.
                continue;
            }
            damaged.setCount(1);
            damaged.setDamageValue(damaged.getMaxDamage() * WORN_PERCENT / 100);

            ItemStack repaired = damaged.copy();
            repaired.setDamageValue(0);

            displays.add(new RepairDisplay(id, recipe, damaged, repaired));
        }
        return displays;
    }

    /** Items of this material one craft may take, which is what the slot shows as a count. */
    int perCraft(RepairMaterial material) {
        return Math.min(this.forFullRepair(material),
                this.recipe.maxPerCraft().orElse(Integer.MAX_VALUE));
    }

    /** Items of this material a repair from empty to full takes. */
    int forFullRepair(RepairMaterial material) {
        // Ceiling division on whole percent, matching the recipe's own rounding.
        return Math.max(1, (100 + material.percent() - 1) / material.percent());
    }

    /** Whether a full repair needs more than one craft, which only max_per_craft can cause. */
    boolean takesSeveralCrafts(RepairMaterial material) {
        return this.perCraft(material) < this.forFullRepair(material);
    }

    /** The material entry a displayed stack belongs to, for the live cost line. */
    RepairMaterial materialFor(ItemStack stack) {
        return this.recipe.materialFor(stack).orElse(this.recipe.materials().getFirst());
    }
}
