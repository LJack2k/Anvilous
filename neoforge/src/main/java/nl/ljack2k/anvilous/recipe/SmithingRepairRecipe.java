package nl.ljack2k.anvilous.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repairs the item in the base slot with the material in the addition slot.
 *
 * <p>Implements {@link SmithingRecipe}, so it is registered under vanilla's
 * {@code RecipeType.SMITHING} and the vanilla smithing table runs it with no menu or block
 * of our own.
 *
 * <p>Durability is restored as a flat percentage of the item's MAXIMUM damage, per material
 * item consumed — not a percentage of what is missing, so the amount is the same whether the
 * tool is nearly broken or nearly full.
 */
public class SmithingRepairRecipe implements SmithingRecipe {

    /** Vanilla's own cap for anvil unit-repair, and our default. */
    public static final int DEFAULT_MAX_PER_CRAFT = 4;

    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final List<RepairMaterial> materials;
    private final int maxPerCraft;
    private final boolean keepEnchantments;
    private final RepairCostMode repairCost;

    public SmithingRepairRecipe(Optional<Ingredient> template,
                                Ingredient base,
                                List<RepairMaterial> materials,
                                int maxPerCraft,
                                boolean keepEnchantments,
                                RepairCostMode repairCost) {
        this.template = template;
        this.base = base;
        this.materials = List.copyOf(materials);
        this.maxPerCraft = maxPerCraft;
        this.keepEnchantments = keepEnchantments;
        this.repairCost = repairCost;
    }

    public Optional<Ingredient> template() {
        return this.template;
    }

    public Ingredient base() {
        return this.base;
    }

    public List<RepairMaterial> materials() {
        return this.materials;
    }

    public int maxPerCraft() {
        return this.maxPerCraft;
    }

    public boolean keepEnchantments() {
        return this.keepEnchantments;
    }

    public RepairCostMode repairCost() {
        return this.repairCost;
    }

    /** The first material entry that accepts this stack, if any. */
    public Optional<RepairMaterial> materialFor(ItemStack stack) {
        return this.materials.stream().filter(m -> m.test(stack)).findFirst();
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        // No template configured means the slot stays empty, so nothing is a valid template.
        return this.template.map(ingredient -> ingredient.test(stack)).orElse(false);
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
        if (this.template.isPresent()) {
            if (!this.template.get().test(input.template())) {
                return false;
            }
        } else if (!input.template().isEmpty()) {
            return false;
        }

        ItemStack tool = input.base();
        if (!this.base.test(tool) || !tool.isDamageableItem() || tool.getDamageValue() <= 0) {
            // Nothing to repair: an undamaged tool should not eat a material.
            return false;
        }

        return this.materialFor(input.addition()).isPresent();
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        RepairMaterial material = this.materialFor(input.addition()).orElse(null);
        if (material == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = input.base().copy();
        result.setCount(1);
        result.setDamageValue(Math.max(0, result.getDamageValue() - this.restoredBy(result, material, this.unitsConsumed(input))));

        if (!this.keepEnchantments) {
            result.remove(DataComponents.ENCHANTMENTS);
        }
        this.repairCost.apply(result);

        return result;
    }

    /**
     * How many material items this craft takes: what is in the slot, capped by
     * {@code max_per_craft}.
     *
     * <p>Note that vanilla's {@code SmithingMenu} removes exactly one item from the addition
     * slot when the result is taken, so consuming more than one needs the menu taught to ask
     * the recipe. Until then a craft restores the full stacked amount while paying for one.
     */
    public int unitsConsumed(SmithingRecipeInput input) {
        return Math.max(1, Math.min(input.addition().getCount(), this.maxPerCraft));
    }

    /** Durability restored by {@code units} of {@code material}, as a share of max damage. */
    private int restoredBy(ItemStack tool, RepairMaterial material, int units) {
        int perUnit = (int) Math.ceil(tool.getMaxDamage() * (material.percent() / 100.0D));
        return perUnit * units;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        // The result is the item in the base slot, so there is nothing static to show.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isIncomplete() {
        return this.base.isEmpty() || this.materials.isEmpty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AnvilousRecipes.SMITHING_REPAIR.get();
    }

    public static class Serializer implements RecipeSerializer<SmithingRepairRecipe> {

        public static final MapCodec<SmithingRepairRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.optionalFieldOf("template")
                                .forGetter(SmithingRepairRecipe::template),
                        Ingredient.CODEC_NONEMPTY.fieldOf("base")
                                .forGetter(SmithingRepairRecipe::base),
                        RepairMaterial.CODEC.listOf().fieldOf("materials")
                                .forGetter(SmithingRepairRecipe::materials),
                        ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_per_craft", DEFAULT_MAX_PER_CRAFT)
                                .forGetter(SmithingRepairRecipe::maxPerCraft),
                        Codec.BOOL.optionalFieldOf("keep_enchantments", true)
                                .forGetter(SmithingRepairRecipe::keepEnchantments),
                        RepairCostMode.CODEC.optionalFieldOf("repair_cost", RepairCostMode.RESET)
                                .forGetter(SmithingRepairRecipe::repairCost)
                ).apply(instance, SmithingRepairRecipe::new));

        /**
         * Written out by hand rather than with {@code StreamCodec.composite}, which runs out
         * of arity at this many fields.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingRepairRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public SmithingRepairRecipe decode(RegistryFriendlyByteBuf buf) {
                        Optional<Ingredient> template = buf.readBoolean()
                                ? Optional.of(Ingredient.CONTENTS_STREAM_CODEC.decode(buf))
                                : Optional.empty();
                        Ingredient base = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);

                        int count = buf.readVarInt();
                        List<RepairMaterial> materials = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            materials.add(RepairMaterial.fromNetwork(buf));
                        }

                        return new SmithingRepairRecipe(template, base, materials,
                                buf.readVarInt(), buf.readBoolean(),
                                buf.readEnum(RepairCostMode.class));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, SmithingRepairRecipe recipe) {
                        buf.writeBoolean(recipe.template().isPresent());
                        recipe.template().ifPresent(ingredient ->
                                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient));
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.base());

                        buf.writeVarInt(recipe.materials().size());
                        recipe.materials().forEach(material -> material.toNetwork(buf));

                        buf.writeVarInt(recipe.maxPerCraft());
                        buf.writeBoolean(recipe.keepEnchantments());
                        buf.writeEnum(recipe.repairCost());
                    }
                };

        @Override
        public MapCodec<SmithingRepairRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingRepairRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
