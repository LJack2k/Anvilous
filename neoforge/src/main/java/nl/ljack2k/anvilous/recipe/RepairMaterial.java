package nl.ljack2k.anvilous.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One repair material: an item or an item tag, plus the percentage of the tool's MAXIMUM
 * durability that a single one of them restores.
 *
 * <p>The source is kept as an {@link Either} rather than being flattened into an
 * {@link Ingredient}, so a recipe round-trips back to the JSON it was written as — a tag
 * stays a tag.
 */
public record RepairMaterial(Either<Holder<Item>, TagKey<Item>> source, int percent) {

    private static final MapCodec<Either<Holder<Item>, TagKey<Item>>> SOURCE_CODEC = Codec.mapEither(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item"),
            TagKey.codec(Registries.ITEM).fieldOf("tag")
    );

    public static final Codec<RepairMaterial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SOURCE_CODEC.forGetter(RepairMaterial::source),
            ExtraCodecs.POSITIVE_INT.fieldOf("percent").forGetter(RepairMaterial::percent)
    ).apply(instance, RepairMaterial::new));

    /** Lazily built — an {@link Ingredient} resolves tags against the live tag state. */
    public Ingredient ingredient() {
        return this.source.map(holder -> Ingredient.of(holder.value()), Ingredient::of);
    }

    public boolean test(ItemStack stack) {
        return this.ingredient().test(stack);
    }

    /**
     * Written as a plain location plus a tag flag rather than through the registry stream
     * codecs: items are not a dynamic registry, so this needs no synced registry access and
     * stays readable.
     */
    public void toNetwork(RegistryFriendlyByteBuf buf) {
        this.source.ifLeft(holder -> {
            buf.writeBoolean(false);
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(holder.value()));
        }).ifRight(tag -> {
            buf.writeBoolean(true);
            buf.writeResourceLocation(tag.location());
        });
        buf.writeVarInt(this.percent);
    }

    public static RepairMaterial fromNetwork(RegistryFriendlyByteBuf buf) {
        boolean isTag = buf.readBoolean();
        ResourceLocation id = buf.readResourceLocation();
        Either<Holder<Item>, TagKey<Item>> source = isTag
                ? Either.right(TagKey.create(Registries.ITEM, id))
                : Either.left(BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id)).orElseThrow(
                        () -> new IllegalStateException("Unknown repair material item: " + id)));
        return new RepairMaterial(source, buf.readVarInt());
    }
}
