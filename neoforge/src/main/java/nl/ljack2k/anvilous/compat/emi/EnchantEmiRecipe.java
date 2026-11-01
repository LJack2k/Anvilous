package nl.ljack2k.anvilous.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import nl.ljack2k.anvilous.compat.view.EnchantDisplay;
import nl.ljack2k.anvilous.recipe.EnchantMaterial;

import java.util.ArrayList;
import java.util.List;

/** Applying an enchantment from a book, paid for in materials instead of experience. */
class EnchantEmiRecipe extends SmithingEmiRecipe {

    private final EnchantDisplay display;
    private final EnchantMaterial material;
    private final EmiStack book;
    private final EmiIngredient tools;
    private final EmiIngredient cost;
    private final List<EmiStack> enchanted;

    /** One per material the recipe accepts, for the same reason as a repair. */
    static List<EnchantEmiRecipe> of(EmiRecipeCategory category, EnchantDisplay display) {
        List<EnchantEmiRecipe> recipes = new ArrayList<>();
        List<EnchantMaterial> materials = display.recipe().materials();
        for (int i = 0; i < materials.size(); i++) {
            recipes.add(new EnchantEmiRecipe(category, display, materials.get(i), i));
        }
        return recipes;
    }

    private EnchantEmiRecipe(EmiRecipeCategory category, EnchantDisplay display,
                             EnchantMaterial material, int index) {
        // One entry per level, so the level belongs in the id as much as the material does.
        super(category, synthetic(display.id(), display.level() + "/" + index));
        this.display = display;
        this.material = material;
        this.book = EmiStack.of(display.book());
        this.tools = toIngredient(display.tools());
        this.cost = EmiIngredient.of(material.ingredient(), display.cost(material));
        this.enchanted = display.enchanted().stream().map(EmiStack::of).toList();
    }

    /** The tools cycle in one slot, since the price does not depend on which one it goes on. */
    private static EmiIngredient toIngredient(List<ItemStack> stacks) {
        return EmiIngredient.of(stacks.stream().map(EmiStack::of).toList());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(this.book, this.tools, this.cost);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return this.enchanted;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.book, TEMPLATE_X, SLOT_Y);
        widgets.addSlot(this.tools, BASE_X, SLOT_Y);
        widgets.addSlot(this.cost, ADDITION_X, SLOT_Y);
        this.addArrow(widgets);
        widgets.addSlot(EmiIngredient.of(this.enchanted), RESULT_X, SLOT_Y).recipeContext(this);

        int cost = this.display.cost(this.material);
        this.addLines(widgets, List.of(
                // Stripped of its own style: vanilla renders enchantment names in grey,
                // which on EMI's grey panel is almost not there.
                Component.literal(Enchantment.getFullname(this.display.enchantment(),
                        this.display.level()).getString()),
                cost == 1
                        ? Component.translatable("gui.anvilous.enchant.cost_one")
                        : Component.translatable("gui.anvilous.enchant.cost", cost, this.material.perLevel()),
                Component.translatable("gui.anvilous.enchant.no_experience")));
    }
}
