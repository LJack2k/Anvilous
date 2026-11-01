package nl.ljack2k.anvilous.compat.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.ljack2k.anvilous.Anvilous;
import nl.ljack2k.anvilous.compat.view.CombineDisplay;
import nl.ljack2k.anvilous.compat.view.EnchantDisplay;
import nl.ljack2k.anvilous.compat.view.RepairDisplay;
import nl.ljack2k.anvilous.compat.view.SmithingDisplays;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows Anvilous recipes in EMI.
 *
 * <p>A plugin of its own rather than a lean on EMI's JEI compatibility layer, which is a
 * mixin into JEI and so does nothing in the packs that ship EMI alone — the common case.
 * Running EMI without JEI, the JEI plugin is never called and EMI logs a failed mixin on
 * JEI's plugin loader; with both installed EMI loads our JEI plugin through jemi. The
 * categories here are registered by EMI itself, so with both viewers present each one shows
 * its own and nothing is listed twice.
 *
 * <p>Nothing outside this package references it: EMI finds the class by its annotation, so
 * with EMI absent it is never loaded and the mod does not know it exists.
 */
@EmiEntrypoint
public class AnvilousEmiPlugin implements EmiPlugin {

    static final EmiRecipeCategory REPAIR =
            category("smithing_repair", Items.SMITHING_TABLE, "gui.anvilous.category.repair");
    static final EmiRecipeCategory COMBINE =
            category("smithing_combine", Items.SMITHING_TABLE, "gui.anvilous.category.combine");
    static final EmiRecipeCategory ENCHANT =
            category("smithing_enchant", Items.ENCHANTED_BOOK, "gui.anvilous.category.enchant");

    /**
     * EMI names a category from {@code emi.category.<namespace>.<path>} by default. These say
     * their names through the keys the JEI categories already use, so the two viewers cannot
     * drift apart and a translator has one set of strings to fill in.
     */
    private static EmiRecipeCategory category(String path, Item icon, String titleKey) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Anvilous.MOD_ID, path);
        return new EmiRecipeCategory(id, EmiStack.of(icon)) {
            @Override
            public Component getName() {
                return Component.translatable(titleKey);
            }
        };
    }

    /**
     * Opens the recipe browser on one of our categories, or on everything an item is part of.
     * Called by the dev harness over RCON so a category can be screenshotted without anyone
     * taking the mouse. Returns false when the name is unknown.
     */
    public static boolean show(boolean isItem, ResourceLocation what) {
        if (isItem) {
            Item item = BuiltInRegistries.ITEM.getOptional(what).orElse(null);
            if (item == null) {
                return false;
            }
            EmiApi.displayRecipes(EmiStack.of(item));
            return true;
        }

        EmiRecipeCategory category = switch (what.getPath()) {
            case "smithing_repair" -> REPAIR;
            case "smithing_combine" -> COMBINE;
            case "smithing_enchant" -> ENCHANT;
            default -> null;
        };
        if (category == null) {
            return false;
        }
        EmiApi.displayRecipeCategory(category);
        return true;
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(REPAIR);
        registry.addCategory(COMBINE);
        registry.addCategory(ENCHANT);

        EmiStack table = EmiStack.of(Items.SMITHING_TABLE);
        registry.addWorkstation(REPAIR, table);
        registry.addWorkstation(COMBINE, table);
        registry.addWorkstation(ENCHANT, table);

        SmithingDisplays displays = SmithingDisplays.collect();

        List<EmiRecipe> recipes = new ArrayList<>();
        for (RepairDisplay repair : displays.repairs()) {
            recipes.addAll(RepairEmiRecipe.of(REPAIR, repair));
        }
        for (CombineDisplay combine : displays.combines()) {
            recipes.add(new CombineEmiRecipe(COMBINE, combine));
        }
        for (EnchantDisplay enchant : displays.enchants()) {
            recipes.addAll(EnchantEmiRecipe.of(ENCHANT, enchant));
        }
        recipes.forEach(registry::addRecipe);

        Anvilous.LOGGER.info("EMI plugin: {} repair, {} combining, {} enchanting",
                displays.repairs().size(), displays.combines().size(), displays.enchants().size());
    }
}
