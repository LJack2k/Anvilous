package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * What the three Anvilous categories have in common: the smithing table's three input slots,
 * one result, and a line or two saying what the craft costs.
 *
 * <p>The data comes from the shared {@code compat.view} records, which know nothing of any
 * viewer — so this is a translation layer and nothing more. Nothing here decides what a
 * recipe does; that question is answered once, in the recipe.
 */
abstract class SmithingReiDisplay implements Display {

    private final ResourceLocation id;

    protected SmithingReiDisplay(ResourceLocation id) {
        this.id = id;
    }

    /** Drawn under the slots, in order. */
    abstract List<Component> lines();

    abstract EntryIngredient template();

    abstract EntryIngredient base();

    abstract EntryIngredient addition();

    abstract EntryIngredient result();

    @Override
    public List<EntryIngredient> getInputEntries() {
        // An empty template is not an input, and listing it as one would put a blank in
        // REI's own "what is this used in" answers.
        EntryIngredient template = this.template();
        return template.isEmpty()
                ? List.of(this.base(), this.addition())
                : List.of(template, this.base(), this.addition());
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(this.result());
    }

    /**
     * The recipe this came from, which REI shows under "advanced tooltips" and uses for
     * recipe-id search. Several displays share one id where a recipe names several tools or
     * materials, which is honest: they really are that one recipe.
     */
    @Override
    public Optional<ResourceLocation> getDisplayLocation() {
        return Optional.of(this.id);
    }
}
