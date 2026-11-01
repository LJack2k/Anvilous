package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.anvilous.compat.view.RepairDisplay;
import nl.ljack2k.anvilous.recipe.RepairMaterial;

import java.util.ArrayList;
import java.util.List;

/** Repairing a tool with material, at the smithing table. */
class RepairReiDisplay extends SmithingReiDisplay {

    private final RepairDisplay display;
    private final RepairMaterial material;

    /**
     * One display per material a repair accepts, rather than one slot cycling through them.
     * REI builds a display's widgets once, so a cycling slot would leave "25% per item"
     * standing under an iron ingot that restores twelve.
     */
    static List<RepairReiDisplay> of(RepairDisplay display) {
        List<RepairReiDisplay> displays = new ArrayList<>();
        for (RepairMaterial material : display.recipe().materials()) {
            displays.add(new RepairReiDisplay(display, material));
        }
        return displays;
    }

    private RepairReiDisplay(RepairDisplay display, RepairMaterial material) {
        super(display.id());
        this.display = display;
        this.material = material;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return AnvilousReiPlugin.REPAIR;
    }

    @Override
    EntryIngredient template() {
        return this.display.recipe().template()
                .map(AnvilousReiPlugin::ingredientOf)
                .orElse(EntryIngredient.empty());
    }

    @Override
    EntryIngredient base() {
        return EntryIngredient.of(EntryStacks.of(this.display.damaged()));
    }

    @Override
    EntryIngredient addition() {
        // The count on the slot says the price without needing a sentence for it.
        int perCraft = this.display.perCraft(this.material);
        List<ItemStack> priced = new ArrayList<>();
        for (ItemStack stack : this.material.ingredient().getItems()) {
            ItemStack copy = stack.copy();
            copy.setCount(perCraft);
            priced.add(copy);
        }
        return EntryIngredient.of(priced.stream().map(EntryStacks::of).toList());
    }

    @Override
    EntryIngredient result() {
        return EntryIngredient.of(EntryStacks.of(this.display.repaired()));
    }

    @Override
    List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.anvilous.repair.per_item", this.material.percent()));
        if (this.display.takesSeveralCrafts(this.material)) {
            lines.add(Component.translatable("gui.anvilous.repair.several_crafts",
                    this.display.perCraft(this.material), this.display.forFullRepair(this.material)));
        } else {
            lines.add(Component.translatable("gui.anvilous.repair.full",
                    this.display.forFullRepair(this.material)));
        }
        if (this.display.recipe().keepEnchantments()) {
            lines.add(Component.translatable("gui.anvilous.keeps_enchantments"));
        }
        return lines;
    }
}
