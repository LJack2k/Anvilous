package nl.ljack2k.anvilous.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.anvilous.Anvilous;
import nl.ljack2k.anvilous.compat.view.SmithingDisplays;

import java.util.List;

/**
 * Shows Anvilous recipes in JEI.
 *
 * <p>This exists because JEI cannot read our recipes on its own, and not for want of trying
 * on either side: its smithing category gets a recipe's three ingredients by casting to
 * {@code SmithingTransformRecipe} or {@code SmithingTrimRecipe} and has no way to ask any
 * other implementation what it accepts, so everything here is filtered out before it reaches
 * the screen. That is the price of running inside the vanilla table instead of shipping a
 * block of our own, and it is worth paying.
 *
 * <p>So these are categories of our own rather than extensions of the vanilla smithing one.
 * The vanilla layout is four fixed slots with nowhere to say "25% per diamond", which is the
 * single thing a player most needs to read.
 *
 * <p>Nothing outside this package references it: JEI finds the class by its annotation, so
 * with JEI absent it is never loaded and the mod does not know it exists.
 */
@JeiPlugin
public class AnvilousJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Anvilous.MOD_ID, "jei");

    /** Set once JEI is up; read only by the dev harness, to open the browser without a mouse. */
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    /**
     * Opens the recipe browser on one of our categories, or on everything an item is part
     * of. Called by the dev harness over RCON so a category can be screenshotted without
     * anyone taking the mouse. Returns false when JEI is not loaded or the name is unknown.
     */
    public static boolean show(boolean isItem, ResourceLocation what) {
        if (runtime == null) {
            return false;
        }
        if (isItem) {
            Item item = BuiltInRegistries.ITEM.getOptional(what).orElse(null);
            if (item == null) {
                return false;
            }
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                    .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(item)));
            return true;
        }

        mezz.jei.api.recipe.RecipeType<?> type = switch (what.getPath()) {
            case "smithing_repair" -> RepairCategory.TYPE;
            case "smithing_combine" -> CombineCategory.TYPE;
            case "smithing_enchant" -> EnchantCategory.TYPE;
            default -> null;
        };
        if (type == null) {
            return false;
        }
        runtime.getRecipesGui().showTypes(List.of(type));
        return true;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new RepairCategory(gui),
                new CombineCategory(gui),
                new EnchantCategory(gui));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack table = new ItemStack(Items.SMITHING_TABLE);
        registration.addRecipeCatalyst(table,
                RepairCategory.TYPE, CombineCategory.TYPE, EnchantCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        SmithingDisplays displays = SmithingDisplays.collect();

        registration.addRecipes(RepairCategory.TYPE, displays.repairs());
        registration.addRecipes(CombineCategory.TYPE, displays.combines());
        registration.addRecipes(EnchantCategory.TYPE, displays.enchants());

        // Also the only way to tell whether something OTHER than JEI loaded this plugin:
        // a viewer with its own JEI compatibility layer calls the same method.
        Anvilous.LOGGER.info("JEI plugin: {} repair, {} combining, {} enchanting",
                displays.repairs().size(), displays.combines().size(), displays.enchants().size());
    }
}
