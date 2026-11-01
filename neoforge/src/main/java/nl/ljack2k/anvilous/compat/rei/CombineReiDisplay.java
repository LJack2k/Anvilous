package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import nl.ljack2k.anvilous.compat.view.CombineDisplay;

import java.util.List;

/** Combining two of the same worn item into one. */
class CombineReiDisplay extends SmithingReiDisplay {

    private final CombineDisplay display;

    CombineReiDisplay(CombineDisplay display) {
        super(display.id());
        this.display = display;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return AnvilousReiPlugin.COMBINE;
    }

    @Override
    EntryIngredient template() {
        return this.display.recipe().template()
                .map(AnvilousReiPlugin::ingredientOf)
                .orElse(EntryIngredient.empty());
    }

    @Override
    EntryIngredient base() {
        return EntryIngredient.of(EntryStacks.of(this.display.tool()));
    }

    @Override
    EntryIngredient addition() {
        return EntryIngredient.of(EntryStacks.of(this.display.sacrifice()));
    }

    @Override
    EntryIngredient result() {
        return EntryIngredient.of(EntryStacks.of(this.display.result()));
    }

    @Override
    List<Component> lines() {
        return List.of(
                Component.translatable("gui.anvilous.combine.durability"),
                Component.translatable("gui.anvilous.combine.bonus", this.display.recipe().bonusPercent()),
                Component.translatable("gui.anvilous.combine.enchantments"));
    }
}
