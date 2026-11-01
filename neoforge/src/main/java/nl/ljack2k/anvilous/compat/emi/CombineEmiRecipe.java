package nl.ljack2k.anvilous.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import nl.ljack2k.anvilous.compat.view.CombineDisplay;

import java.util.List;

/** Combining two of the same worn item into one. */
class CombineEmiRecipe extends SmithingEmiRecipe {

    private final CombineDisplay display;
    private final EmiIngredient template;
    private final EmiStack tool;
    private final EmiStack sacrifice;
    private final EmiStack result;

    CombineEmiRecipe(EmiRecipeCategory category, CombineDisplay display) {
        super(category, per(display.id(), display.tool(), 0));
        this.display = display;
        this.template = display.recipe().template()
                .map(EmiIngredient::of)
                .orElse(EmiStack.EMPTY);
        this.tool = EmiStack.of(display.tool());
        this.sacrifice = EmiStack.of(display.sacrifice());
        this.result = EmiStack.of(display.result());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return this.template.isEmpty()
                ? List.of(this.tool, this.sacrifice)
                : List.of(this.template, this.tool, this.sacrifice);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(this.result);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.template, TEMPLATE_X, SLOT_Y);
        widgets.addSlot(this.tool, BASE_X, SLOT_Y);
        widgets.addSlot(this.sacrifice, ADDITION_X, SLOT_Y);
        this.addArrow(widgets);
        widgets.addSlot(this.result, RESULT_X, SLOT_Y).recipeContext(this);

        this.addLines(widgets, List.of(
                Component.translatable("gui.anvilous.combine.durability"),
                Component.translatable("gui.anvilous.combine.bonus", this.display.recipe().bonusPercent()),
                Component.translatable("gui.anvilous.combine.enchantments")));
    }
}
