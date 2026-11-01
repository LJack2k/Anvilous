package nl.ljack2k.anvilous.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;

import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies an enchantment from the book in the template slot to the tool in the base slot,
 * paid for in materials instead of experience.
 *
 * <p>The recipe names the ENCHANTMENT rather than matching the book, which is what makes
 * per-enchantment pricing possible at all: an {@link Ingredient} matches items and tags and
 * ignores components, so it can say "an enchanted book" but never "a Mending book". Naming
 * the enchantment sidesteps that entirely — the book is only where the level is read from.
 *
 * <p>Combining follows vanilla's anvil rules, reusing vanilla's own helpers rather than
 * reimplementing them: two equal levels make the next one up, levels cap at the
 * enchantment's maximum, incompatible enchantments refuse, and whether an item accepts an
 * enchantment is {@code ItemStack#supportsEnchantment}, so modded tools answer for themselves.
 */
public class SmithingEnchantRecipe implements SmithingRecipe, MaterialCostRecipe {

    private final ResourceLocation enchantment;
    private final Ingredient base;
    private final List<EnchantMaterial> materials;
    private final RepairCostMode repairCost;

    public SmithingEnchantRecipe(ResourceLocation enchantment,
                                 Ingredient base,
                                 List<EnchantMaterial> materials,
                                 RepairCostMode repairCost) {
        this.enchantment = enchantment;
        this.base = base;
        this.materials = List.copyOf(materials);
        this.repairCost = repairCost;
    }

    public ResourceLocation enchantment() {
        return this.enchantment;
    }

    public Ingredient base() {
        return this.base;
    }

    public List<EnchantMaterial> materials() {
        return this.materials;
    }

    public RepairCostMode repairCost() {
        return this.repairCost;
    }

    /**
     * The book's entry for this recipe's enchantment, if it has one.
     *
     * <p>Read straight off the component and compared by id, so no registry access is needed
     * — which matters because {@link #unitsConsumed} is handed nothing but the input.
     */
    private Optional<Object2IntMap.Entry<Holder<Enchantment>>> bookEntry(ItemStack template) {
        ItemEnchantments stored = template.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null) {
            return Optional.empty();
        }
        return stored.entrySet().stream()
                .filter(entry -> entry.getKey().unwrapKey()
                        .map(key -> key.location().equals(this.enchantment))
                        .orElse(false))
                .findFirst();
    }

    private Optional<EnchantMaterial> materialFor(ItemStack stack) {
        return this.materials.stream().filter(material -> material.test(stack)).findFirst();
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return this.bookEntry(stack).isPresent();
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return this.materialFor(stack).isPresent();
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return !this.build(input).isEmpty() && this.affordable(input);
    }

    /** Whether the addition slot actually holds enough to pay for this craft. */
    private boolean affordable(SmithingRecipeInput input) {
        var entry = this.bookEntry(input.template());
        var material = this.materialFor(input.addition());
        if (entry.isEmpty() || material.isEmpty()) {
            return false;
        }
        return input.addition().getCount() >= material.get().costFor(entry.get().getIntValue());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return this.build(input);
    }

    /**
     * The enchanted result, or empty when this craft is not allowed.
     *
     * <p>Shared by {@link #matches} and {@link #assemble} so the menu can never offer a result
     * it would then refuse to produce.
     */
    private ItemStack build(SmithingRecipeInput input) {
        var entry = this.bookEntry(input.template()).orElse(null);
        if (entry == null || !this.base.test(input.base()) || this.materialFor(input.addition()).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack tool = input.base();
        Holder<Enchantment> holder = entry.getKey();
        if (!tool.supportsEnchantment(holder)) {
            return ItemStack.EMPTY;
        }

        ItemEnchantments existing = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> present : existing.keySet()) {
            if (!present.equals(holder) && !Enchantment.areCompatible(holder, present)) {
                return ItemStack.EMPTY;
            }
        }

        int bookLevel = entry.getIntValue();
        int current = existing.getLevel(holder);
        // Vanilla's anvil rule: two equal levels make the next one up, otherwise the higher
        // of the two wins.
        int merged = Math.min(current == bookLevel ? bookLevel + 1 : Math.max(current, bookLevel),
                holder.value().getMaxLevel());
        if (merged <= current) {
            // Nothing would change, so do not charge for it.
            return ItemStack.EMPTY;
        }

        ItemStack result = tool.copy();
        result.setCount(1);

        // Set the component directly rather than through EnchantmentHelper.updateEnchantments:
        // that helper returns without doing anything when the component is absent, which would
        // be a silent no-op on any item that does not carry an empty one already.
        ItemEnchantments.Mutable merging = new ItemEnchantments.Mutable(existing);
        merging.set(holder, merged);
        result.set(DataComponents.ENCHANTMENTS, merging.toImmutable());

        this.repairCost.apply(result);
        return result;
    }

    @Override
    public int unitsConsumed(SmithingRecipeInput input) {
        var entry = this.bookEntry(input.template());
        var material = this.materialFor(input.addition());
        if (entry.isEmpty() || material.isEmpty()) {
            return 1;
        }
        // A declared price, not "what is needed": enchanting has nothing to fill up, so the
        // cost is whatever the recipe says the level is worth.
        return Math.max(1, material.get().costFor(entry.get().getIntValue()));
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isIncomplete() {
        return this.base.isEmpty() || this.materials.isEmpty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AnvilousRecipes.SMITHING_ENCHANT.get();
    }

    public static class Serializer implements RecipeSerializer<SmithingEnchantRecipe> {

        public static final MapCodec<SmithingEnchantRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("enchantment")
                                .forGetter(SmithingEnchantRecipe::enchantment),
                        Ingredient.CODEC_NONEMPTY.fieldOf("base")
                                .forGetter(SmithingEnchantRecipe::base),
                        EnchantMaterial.CODEC.listOf().fieldOf("materials")
                                .forGetter(SmithingEnchantRecipe::materials),
                        RepairCostMode.CODEC.optionalFieldOf("repair_cost", RepairCostMode.RESET)
                                .forGetter(SmithingEnchantRecipe::repairCost)
                ).apply(instance, SmithingEnchantRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingEnchantRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public SmithingEnchantRecipe decode(RegistryFriendlyByteBuf buf) {
                        ResourceLocation enchantment = buf.readResourceLocation();
                        Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);

                        int count = buf.readVarInt();
                        List<EnchantMaterial> materials = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            materials.add(EnchantMaterial.fromNetwork(buf));
                        }

                        return new SmithingEnchantRecipe(enchantment, base, materials,
                                buf.readEnum(RepairCostMode.class));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, SmithingEnchantRecipe recipe) {
                        buf.writeResourceLocation(recipe.enchantment());
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.base());

                        buf.writeVarInt(recipe.materials().size());
                        recipe.materials().forEach(material -> material.toNetwork(buf));

                        buf.writeEnum(recipe.repairCost());
                    }
                };

        @Override
        public MapCodec<SmithingEnchantRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingEnchantRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
