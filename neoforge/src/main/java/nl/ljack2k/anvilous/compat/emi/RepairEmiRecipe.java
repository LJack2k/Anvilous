package nl.ljack2k.anvilous.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import nl.ljack2k.anvilous.compat.view.RepairDisplay;
import nl.ljack2k.anvilous.recipe.RepairMaterial;

import java.util.ArrayList;
import java.util.List;

/** Repairing a tool with material, at the smithing table. */
class RepairEmiRecipe extends SmithingEmiRecipe {

    private final RepairDisplay display;
    private final RepairMaterial material;
    private final EmiIngredient template;
    private final EmiStack damaged;
    private final EmiIngredient cost;
    private final EmiStack repaired;

    /**
     * One recipe per material a repair accepts, rather than one slot cycling through them.
     * The price differs per material and EMI's text is fixed once the recipe is built, so a
     * cycling slot would leave "25% per item" standing under an iron ingot that restores 12.
     */
    static List<RepairEmiRecipe> of(EmiRecipeCategory category, RepairDisplay display) {
        List<RepairEmiRecipe> recipes = new ArrayList<>();
        List<RepairMaterial> materials = display.recipe().materials();
        for (int i = 0; i < materials.size(); i++) {
            recipes.add(new RepairEmiRecipe(category, display, materials.get(i), i));
        }
        return recipes;
    }

    private RepairEmiRecipe(EmiRecipeCategory category, RepairDisplay display,
                            RepairMaterial material, int index) {
        super(category, per(display.id(), display.damaged(), index));
        this.display = display;
        this.material = material;
        this.template = display.recipe().template()
                .map(EmiIngredient::of)
                .orElse(EmiStack.EMPTY);
        this.damaged = EmiStack.of(display.damaged());
        // The count on the slot says the price without needing a sentence for it.
        this.cost = EmiIngredient.of(material.ingredient(), display.perCraft(material));
        this.repaired = EmiStack.of(display.repaired());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        // The empty template slot is still drawn, because the table has one - but an empty
        // ingredient has no business in what EMI searches and resolves.
        return this.template.isEmpty()
                ? List.of(this.damaged, this.cost)
                : List.of(this.template, this.damaged, this.cost);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(this.repaired);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.template, TEMPLATE_X, SLOT_Y);
        widgets.addSlot(this.damaged, BASE_X, SLOT_Y);
        widgets.addSlot(this.cost, ADDITION_X, SLOT_Y);
        this.addArrow(widgets);
        widgets.addSlot(this.repaired, RESULT_X, SLOT_Y).recipeContext(this);

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
        this.addLines(widgets, lines);
    }
}
