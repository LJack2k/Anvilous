package nl.ljack2k.anvilous.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import nl.ljack2k.anvilous.Anvilous;

public final class AnvilousRecipes {
    private AnvilousRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Anvilous.MOD_ID);

    /**
     * Only a serializer is registered, never a {@code RecipeType}. The recipes live under
     * vanilla's {@code RecipeType.SMITHING} so the vanilla table runs them unmodified.
     */
    public static final DeferredHolder<RecipeSerializer<?>, SmithingRepairRecipe.Serializer> SMITHING_REPAIR =
            SERIALIZERS.register("smithing_repair", SmithingRepairRecipe.Serializer::new);
}
