package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import nl.ljack2k.anvilous.compat.view.EnchantDisplay;
import nl.ljack2k.anvilous.recipe.EnchantMaterial;

import java.util.ArrayList;
import java.util.List;

/** Applying an enchantment from a book, paid for in materials instead of experience. */
class EnchantReiDisplay extends SmithingReiDisplay {

    private final EnchantDisplay display;
    private final EnchantMaterial material;

    /** One per material, for the same reason repairs are: the drawn price must be the real one. */
    static List<EnchantReiDisplay> of(EnchantDisplay display) {
        List<EnchantReiDisplay> displays = new ArrayList<>();
        for (EnchantMaterial material : display.recipe().materials()) {
            displays.add(new EnchantReiDisplay(display, material));
        }
        return displays;
    }

    private EnchantReiDisplay(EnchantDisplay display, EnchantMaterial material) {
        super(display.id());
        this.display = display;
        this.material = material;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return AnvilousReiPlugin.ENCHANT;
    }

    @Override
    EntryIngredient template() {
        return EntryIngredient.of(EntryStacks.of(this.display.book()));
    }

    @Override
    EntryIngredient base() {
        return EntryIngredient.of(this.display.tools().stream().map(EntryStacks::of).toList());
    }

    @Override
    EntryIngredient addition() {
        int cost = this.display.cost(this.material);
        List<ItemStack> priced = new ArrayList<>();
        for (ItemStack stack : this.material.ingredient().getItems()) {
            ItemStack copy = stack.copy();
            copy.setCount(cost);
            priced.add(copy);
        }
        return EntryIngredient.of(priced.stream().map(EntryStacks::of).toList());
    }

    @Override
    EntryIngredient result() {
        return EntryIngredient.of(this.display.enchanted().stream().map(EntryStacks::of).toList());
    }

    @Override
    List<Component> lines() {
        int cost = this.display.cost(this.material);
        return List.of(
                // Stripped of its own style: vanilla renders enchantment names in grey, which
                // on a grey recipe panel is almost not there.
                Component.literal(Enchantment.getFullname(
                        this.display.enchantment(), this.display.level()).getString()),
                cost == 1
                        ? Component.translatable("gui.anvilous.enchant.cost_one")
                        : Component.translatable("gui.anvilous.enchant.cost", cost, this.material.perLevel()),
                Component.translatable("gui.anvilous.enchant.no_experience"));
    }
}
