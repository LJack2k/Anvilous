package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import nl.ljack2k.anvilous.Anvilous;
import nl.ljack2k.anvilous.compat.view.CombineDisplay;
import nl.ljack2k.anvilous.compat.view.EnchantDisplay;
import nl.ljack2k.anvilous.compat.view.RepairDisplay;
import nl.ljack2k.anvilous.compat.view.SmithingDisplays;

import java.util.Arrays;

/**
 * Shows Anvilous recipes in REI.
 *
 * <p>REI needs this more than the others did. Its own bridge for JEI plugins, REI Plugin
 * Compatibilities, is published for 1.16.5 Forge and nothing since, so a REI user sees
 * nothing of ours at all — not a wrong layout, nothing.
 *
 * <p>The entries themselves come from the shared {@code compat.view} records, the same ones
 * the JEI and EMI plugins build from, and the categories say their names through the same
 * lang keys. Three viewers, one description of what a recipe does.
 *
 * <p>Nothing outside this package references it: REI finds the class by its annotation, so
 * with REI absent it is never loaded and the mod does not know it exists.
 */
@REIPluginClient
public class AnvilousReiPlugin implements REIClientPlugin {

    static final CategoryIdentifier<RepairReiDisplay> REPAIR =
            CategoryIdentifier.of(Anvilous.MOD_ID, "smithing_repair");
    static final CategoryIdentifier<CombineReiDisplay> COMBINE =
            CategoryIdentifier.of(Anvilous.MOD_ID, "smithing_combine");
    static final CategoryIdentifier<EnchantReiDisplay> ENCHANT =
            CategoryIdentifier.of(Anvilous.MOD_ID, "smithing_enchant");

    /** An ingredient's items as one REI entry, so a tag stays a cycling slot rather than one item. */
    static EntryIngredient ingredientOf(Ingredient ingredient) {
        return EntryIngredient.of(Arrays.stream(ingredient.getItems()).map(EntryStacks::of).toList());
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        // One at a time rather than the varargs overload, which takes a generic array and so
        // cannot be called for three different display types without an unchecked warning.
        registry.add(new SmithingReiCategory<>(REPAIR,
                Component.translatable("gui.anvilous.category.repair"),
                EntryStacks.of(Items.SMITHING_TABLE)));
        registry.add(new SmithingReiCategory<>(COMBINE,
                Component.translatable("gui.anvilous.category.combine"),
                EntryStacks.of(Items.SMITHING_TABLE)));
        registry.add(new SmithingReiCategory<>(ENCHANT,
                Component.translatable("gui.anvilous.category.enchant"),
                EntryStacks.of(Items.ENCHANTED_BOOK)));

        // What a player must stand at to do any of this. All three are the vanilla table.
        registry.addWorkstations(REPAIR, EntryStacks.of(Items.SMITHING_TABLE));
        registry.addWorkstations(COMBINE, EntryStacks.of(Items.SMITHING_TABLE));
        registry.addWorkstations(ENCHANT, EntryStacks.of(Items.SMITHING_TABLE));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        SmithingDisplays displays = SmithingDisplays.collect();

        for (RepairDisplay repair : displays.repairs()) {
            RepairReiDisplay.of(repair).forEach(registry::add);
        }
        for (CombineDisplay combine : displays.combines()) {
            registry.add(new CombineReiDisplay(combine));
        }
        for (EnchantDisplay enchant : displays.enchants()) {
            EnchantReiDisplay.of(enchant).forEach(registry::add);
        }

        Anvilous.LOGGER.info("REI plugin: {} repair, {} combining, {} enchanting",
                displays.repairs().size(), displays.combines().size(), displays.enchants().size());
    }

    /**
     * Opens REI on one of our categories, or on everything an item can be made by. Called by
     * the dev harness over RCON so a category can be screenshotted without taking the mouse.
     */
    public static boolean show(boolean isItem, ResourceLocation what) {
        if (isItem) {
            Item item = BuiltInRegistries.ITEM.getOptional(what).orElse(null);
            return item != null && ViewSearchBuilder.builder()
                    .addRecipesFor(EntryStacks.of(new ItemStack(item)))
                    .open();
        }

        CategoryIdentifier<?> category = switch (what.getPath()) {
            case "smithing_repair" -> REPAIR;
            case "smithing_combine" -> COMBINE;
            case "smithing_enchant" -> ENCHANT;
            default -> null;
        };
        return category != null && ViewSearchBuilder.builder().addCategory(category).open();
    }
}
