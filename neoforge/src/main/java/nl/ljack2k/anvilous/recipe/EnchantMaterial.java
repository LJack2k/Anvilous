package nl.ljack2k.anvilous.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One material an enchantment may be paid in, and how many of it each LEVEL costs.
 *
 * <p>Per level rather than flat, because that is the thing packs actually want to vary:
 * Sharpness V should cost more than Sharpness I, and Mending — which has one level — should
 * be priced on its own terms. It is the same shape EnderIO's enchanter has used for years.
 */
public record EnchantMaterial(Either<Holder<Item>, TagKey<Item>> source, int perLevel) {

    public static final Codec<EnchantMaterial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MaterialSource.MAP_CODEC.forGetter(EnchantMaterial::source),
            ExtraCodecs.POSITIVE_INT.fieldOf("per_level").forGetter(EnchantMaterial::perLevel)
    ).apply(instance, EnchantMaterial::new));

    public Ingredient ingredient() {
        return MaterialSource.toIngredient(this.source);
    }

    public boolean test(ItemStack stack) {
        return this.ingredient().test(stack);
    }

    /** What this material costs for an enchantment of {@code level}. */
    public int costFor(int level) {
        return this.perLevel * Math.max(1, level);
    }

    public void toNetwork(RegistryFriendlyByteBuf buf) {
        MaterialSource.toNetwork(buf, this.source);
        buf.writeVarInt(this.perLevel);
    }

    public static EnchantMaterial fromNetwork(RegistryFriendlyByteBuf buf) {
        return new EnchantMaterial(MaterialSource.fromNetwork(buf), buf.readVarInt());
    }
}
