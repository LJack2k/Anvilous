package nl.ljack2k.anvilous;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers the built-in recipe sets as real datapacks rather than mod data.
 *
 * <p>Anything under the mod's own {@code data/} root is part of the always-on mod resources
 * pack: it cannot be disabled, and JSON has no way to delete a recipe, only to override one
 * by path. Shipping the defaults as packs instead makes them visible in the world-creation
 * screen and in {@code /datapack list}, and turnable off per world — and lets
 * {@link AnvilousConfig} decide whether they are offered at all.
 *
 * <p>{@link PackSource#BUILT_IN} is what makes them on by default: the server enables any
 * pack whose source {@code shouldAddAutomatically} the first time it sees it, while still
 * honouring a world's disabled list afterwards. {@code alwaysActive} stays false, or the
 * player could never turn one off. {@link Pack.Position#BOTTOM} puts them at the foot of the
 * stack, so a pack's own file at the same path wins without anybody thinking about order.
 */
public final class AnvilousPacks {
    private AnvilousPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        add(event, "defaults_repair", "Anvilous repair recipes", AnvilousConfig.REPAIR);
        add(event, "defaults_combine", "Anvilous combine recipes", AnvilousConfig.COMBINE);
        add(event, "defaults_enchant", "Anvilous enchanting recipes", AnvilousConfig.ENCHANT);
    }

    private static void add(AddPackFindersEvent event, String folder, String title, ModConfigSpec.BooleanValue flag) {
        if (!AnvilousConfig.enabled(flag)) {
            Anvilous.LOGGER.info("Built-in recipes '{}' are switched off in the config, not offering them", folder);
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(Anvilous.MOD_ID, folder),
                PackType.SERVER_DATA,
                Component.literal(title),
                PackSource.BUILT_IN,
                false,
                Pack.Position.BOTTOM);
    }
}
