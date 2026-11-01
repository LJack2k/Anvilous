package nl.ljack2k.anvilous.compat.view;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import nl.ljack2k.anvilous.recipe.SmithingCombineRecipe;
import nl.ljack2k.anvilous.recipe.SmithingEnchantRecipe;
import nl.ljack2k.anvilous.recipe.SmithingRepairRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Every Anvilous recipe the client knows about, turned into the entries a viewer should show.
 *
 * <p>Both plugins ask for the same thing at the same moment — once a world is joined, since
 * the recipes are datapack-driven and synced — so the walk over the recipe manager lives here
 * rather than once per viewer.
 *
 * @param repairs one entry per repairable item the recipes name
 * @param combines one entry per combinable item
 * @param enchants one entry per enchantment level
 */
public record SmithingDisplays(List<RepairDisplay> repairs,
                               List<CombineDisplay> combines,
                               List<EnchantDisplay> enchants) {

    /** Empty when no world is joined, which is when a viewer asks too early. */
    public static SmithingDisplays collect() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return new SmithingDisplays(List.of(), List.of(), List.of());
        }

        List<RepairDisplay> repairs = new ArrayList<>();
        List<CombineDisplay> combines = new ArrayList<>();
        List<EnchantDisplay> enchants = new ArrayList<>();

        for (RecipeHolder<SmithingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            ResourceLocation id = holder.id();
            switch (holder.value()) {
                case SmithingRepairRecipe repair -> repairs.addAll(RepairDisplay.explode(repair, id));
                case SmithingCombineRecipe combine -> combines.addAll(CombineDisplay.explode(combine, id));
                case SmithingEnchantRecipe enchant -> {
                    Holder<Enchantment> enchantment = level.registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .listElements()
                            .filter(candidate -> candidate.key().location().equals(enchant.enchantment()))
                            .findFirst()
                            .map(reference -> (Holder<Enchantment>) reference)
                            .orElse(null);
                    // A recipe naming an enchantment this pack does not have is inert in
                    // game, so it has nothing to show here either.
                    if (enchantment != null) {
                        enchants.addAll(EnchantDisplay.explode(enchant, enchantment, id));
                    }
                }
                default -> {
                    // Vanilla's own smithing recipes, which every viewer already shows itself.
                }
            }
        }

        return new SmithingDisplays(repairs, combines, enchants);
    }
}
