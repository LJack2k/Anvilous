package nl.ljack2k.anvilous.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.anvilous.Anvilous;

/**
 * Server to client trigger: "open the recipe browser on this".
 *
 * <p>The reason this exists rather than a note saying "now click on JEI": a recipe category
 * is only proved to render by rendering it, and the harness drives the client over RCON
 * precisely so nobody has to take the mouse to check. {@code what} is a category id for a
 * whole category, or an item id to look that item's recipes up the way a player would.
 *
 * <p>No client refs, so a dedicated server can load it. Dev-only — registered only when
 * {@code -Danvilous.devHarness} is set.
 */
public record ShowRecipesPayload(boolean isItem, ResourceLocation what) implements CustomPacketPayload {

    public static final Type<ShowRecipesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Anvilous.MOD_ID, "show_recipes"));

    public static final StreamCodec<ByteBuf, ShowRecipesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShowRecipesPayload::isItem,
            ResourceLocation.STREAM_CODEC, ShowRecipesPayload::what,
            ShowRecipesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
