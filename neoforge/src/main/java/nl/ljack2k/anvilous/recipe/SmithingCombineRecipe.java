package nl.ljack2k.anvilous.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Combines two of the same item, adding their remaining durability and merging their
 * enchantments — the anvil's third operation, and the only repair some items have.
 *
 * <p>Nine vanilla items have no material repair at all: bows, crossbows, fishing rods,
 * shears, tridents, flint and steel, brushes and the two sticks-on-a-string. Without this
 * they cannot be repaired by the mod at all, so it is what makes the smithing table a real
 * stand-in for the anvil rather than a partial one.
 *
 * <p>The durability sum is vanilla's, including its 12% bonus for combining, so a pack that
 * replaces the anvil with this does not quietly change the economics.
 */
public class SmithingCombineRecipe implements SmithingRecipe {

    /** Vanilla's bonus for combining, as a percentage of maximum durability. */
    public static final int VANILLA_BONUS_PERCENT = 12;

    private final Optional<Ingredient> template;
    private final Ingredient base;
    private final int bonusPercent;
    private final RepairCostMode repairCost;

    public SmithingCombineRecipe(Optional<Ingredient> template,
                                 Ingredient base,
                                 int bonusPercent,
                                 RepairCostMode repairCost) {
        this.template = template;
        this.base = base;
        this.bonusPercent = bonusPercent;
        this.repairCost = repairCost;
    }

    public Optional<Ingredient> template() {
        return this.template;
    }

    public Ingredient base() {
        return this.base;
    }

    public int bonusPercent() {
        return this.bonusPercent;
    }

    public RepairCostMode repairCost() {
        return this.repairCost;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return this.template.map(ingredient -> ingredient.test(stack)).orElse(false);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        // The sacrifice is the same item, which is what "combine" means in an anvil.
        return this.base.test(stack);
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return !this.build(input).isEmpty();
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return this.build(input);
    }

    /** The combined result, or empty when the craft would achieve nothing. */
    private ItemStack build(SmithingRecipeInput input) {
        if (this.template.isPresent()) {
            if (!this.template.get().test(input.template())) {
                return ItemStack.EMPTY;
            }
        } else if (!input.template().isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack tool = input.base();
        ItemStack sacrifice = input.addition();
        if (!this.base.test(tool) || !sacrifice.is(tool.getItem()) || !tool.isDamageableItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = tool.copy();
        result.setCount(1);

        // Vanilla's sum: both items' remaining durability plus a bonus share of the maximum.
        int toolLeft = tool.getMaxDamage() - tool.getDamageValue();
        int sacrificeLeft = sacrifice.getMaxDamage() - sacrifice.getDamageValue();
        int bonus = result.getMaxDamage() * this.bonusPercent / 100;
        int repaired = Math.max(0, result.getMaxDamage() - (toolLeft + sacrificeLeft + bonus));

        boolean changed = false;
        if (repaired < result.getDamageValue()) {
            result.setDamageValue(repaired);
            changed = true;
        }

        ItemEnchantments carried = sacrifice.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : carried.entrySet()) {
            // Vanilla skips the ones that do not fit rather than refusing the whole craft.
            changed |= EnchantMerge.apply(result, entry.getKey(), entry.getIntValue());
        }

        if (!changed) {
            return ItemStack.EMPTY;
        }

        this.repairCost.apply(result);
        return result;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isIncomplete() {
        return this.base.isEmpty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AnvilousRecipes.SMITHING_COMBINE.get();
    }

    public static class Serializer implements RecipeSerializer<SmithingCombineRecipe> {

        public static final MapCodec<SmithingCombineRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.optionalFieldOf("template")
                                .forGetter(SmithingCombineRecipe::template),
                        Ingredient.CODEC_NONEMPTY.fieldOf("base")
                                .forGetter(SmithingCombineRecipe::base),
                        ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("bonus_percent", VANILLA_BONUS_PERCENT)
                                .forGetter(SmithingCombineRecipe::bonusPercent),
                        RepairCostMode.CODEC.optionalFieldOf("repair_cost", RepairCostMode.RESET)
                                .forGetter(SmithingCombineRecipe::repairCost)
                ).apply(instance, SmithingCombineRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingCombineRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public SmithingCombineRecipe decode(RegistryFriendlyByteBuf buf) {
                        Optional<Ingredient> template = buf.readBoolean()
                                ? Optional.of(Ingredient.CONTENTS_STREAM_CODEC.decode(buf))
                                : Optional.empty();
                        return new SmithingCombineRecipe(template,
                                Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                                buf.readVarInt(),
                                buf.readEnum(RepairCostMode.class));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, SmithingCombineRecipe recipe) {
                        buf.writeBoolean(recipe.template().isPresent());
                        recipe.template().ifPresent(ingredient ->
                                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient));
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.base());
                        buf.writeVarInt(recipe.bonusPercent());
                        buf.writeEnum(recipe.repairCost());
                    }
                };

        @Override
        public MapCodec<SmithingCombineRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingCombineRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
