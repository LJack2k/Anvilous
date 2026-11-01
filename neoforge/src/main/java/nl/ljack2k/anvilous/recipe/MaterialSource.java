package nl.ljack2k.anvilous.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An {@code "item"} or a {@code "tag"} in a recipe's materials list, shared by repair and
 * enchanting.
 *
 * <p>Kept as an {@link Either} rather than flattened into an {@link Ingredient} so a recipe
 * round-trips back to the JSON it was written as — a tag stays a tag.
 */
public final class MaterialSource {
    private MaterialSource() {
    }

    public static final MapCodec<Either<Holder<Item>, TagKey<Item>>> MAP_CODEC = Codec.mapEither(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item"),
            TagKey.codec(Registries.ITEM).fieldOf("tag")
    );

    /** Built fresh each time, since an {@link Ingredient} resolves tags against live state. */
    public static Ingredient toIngredient(Either<Holder<Item>, TagKey<Item>> source) {
        return source.map(holder -> Ingredient.of(holder.value()), Ingredient::of);
    }

    /**
     * Written as a plain location plus a tag flag rather than through the registry stream
     * codecs: items are not a dynamic registry, so this needs no synced registry access and
     * stays readable.
     */
    public static void toNetwork(RegistryFriendlyByteBuf buf, Either<Holder<Item>, TagKey<Item>> source) {
        source.ifLeft(holder -> {
            buf.writeBoolean(false);
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(holder.value()));
        }).ifRight(tag -> {
            buf.writeBoolean(true);
            buf.writeResourceLocation(tag.location());
        });
    }

    public static Either<Holder<Item>, TagKey<Item>> fromNetwork(RegistryFriendlyByteBuf buf) {
        boolean isTag = buf.readBoolean();
        ResourceLocation id = buf.readResourceLocation();
        return isTag
                ? Either.right(TagKey.create(Registries.ITEM, id))
                : Either.left(BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id))
                        .orElseThrow(() -> new IllegalStateException("Unknown material item: " + id)));
    }
}
