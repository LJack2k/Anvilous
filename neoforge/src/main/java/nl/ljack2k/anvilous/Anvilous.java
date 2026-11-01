package nl.ljack2k.anvilous;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import nl.ljack2k.anvilous.recipe.AnvilousRecipes;

/**
 * Anvilous — smithing-table repair driven by datapack recipes.
 *
 * <p>The mod adds no blocks, items or menus. Its recipes implement vanilla's
 * {@link net.minecraft.world.item.crafting.SmithingRecipe}, which puts them under
 * {@code RecipeType.SMITHING}, so the vanilla smithing table finds and runs them
 * exactly as it does {@code smithing_transform} and {@code smithing_trim}.
 */
@Mod(Anvilous.MOD_ID)
public class Anvilous {
    public static final String MOD_ID = "anvilous";

    public Anvilous(IEventBus modBus, ModContainer container) {
        AnvilousRecipes.SERIALIZERS.register(modBus);
    }
}
