package nl.ljack2k.anvilous.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The shape all three EMI categories share: the smithing table's three input slots, an arrow,
 * and the result, with room underneath for a line or two about what the craft costs.
 *
 * <p>Same proportions as the JEI categories, but drawn with EMI's own slot and arrow
 * textures, so each viewer looks like itself.
 */
abstract class SmithingEmiRecipe implements EmiRecipe {

    static final int TEMPLATE_X = 0;
    static final int BASE_X = 24;
    static final int ADDITION_X = 48;
    static final int ARROW_X = 74;
    static final int RESULT_X = 102;
    static final int SLOT_Y = 0;
    /** The arrow is 17 tall against an 18 slot, so it sits one pixel lower to line up. */
    static final int ARROW_Y = SLOT_Y + 1;

    static final int WIDTH = 160;
    static final int HEIGHT = 54;

    private static final int TEXT_X = 1;
    private static final int TEXT_Y = 23;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOUR = 0xFF404040;

    private final EmiRecipeCategory category;
    private final ResourceLocation id;

    SmithingEmiRecipe(EmiRecipeCategory category, ResourceLocation id) {
        this.category = category;
        this.id = id;
    }

    /**
     * A recipe over a tag becomes one entry per item, so the recipe's own id would be worn by
     * a dozen entries. EMI keys recipes by id, so each entry gets the item it is about.
     */
    static ResourceLocation per(ResourceLocation recipe, ItemStack item, int variant) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item.getItem());
        return synthetic(recipe,
                key.getNamespace() + "/" + key.getPath() + "/" + variant);
    }

    /**
     * None of these ids name a recipe the recipe manager holds — one JSON becomes several
     * entries — and EMI asks for that to be said out loud with a leading slash, or it reports
     * every one of them as a missing recipe.
     */
    static ResourceLocation synthetic(ResourceLocation recipe, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(recipe.getNamespace(),
                "/" + recipe.getPath() + "/" + suffix);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return this.category;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    /**
     * Kept out of the recipe tree: every one of these hands back the item it was given, so
     * treating it as a way to obtain that item would have the tree chase itself.
     */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    void addArrow(WidgetHolder widgets) {
        widgets.addTexture(EmiTexture.FULL_ARROW, ARROW_X, ARROW_Y);
    }

    /** Draws the cost lines under the slots, in order. */
    void addLines(WidgetHolder widgets, List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            widgets.addText(lines.get(i), TEXT_X, TEXT_Y + i * LINE_HEIGHT, TEXT_COLOUR, false);
        }
    }
}
