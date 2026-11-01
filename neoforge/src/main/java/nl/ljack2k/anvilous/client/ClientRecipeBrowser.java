package nl.ljack2k.anvilous.client;

import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.anvilous.Anvilous;

/**
 * Opens the recipe browser on the dev client, for the harness.
 *
 * <p>Reaches into the JEI plugin through a {@code Throwable} guard rather than a hard call:
 * this is the one place in the mod that touches a class which only exists when JEI is
 * installed, and a dev tool is not worth a crash on a client without it.
 */
public final class ClientRecipeBrowser {

    private ClientRecipeBrowser() {
    }

    public static void show(boolean isItem, ResourceLocation what) {
        try {
            if (!nl.ljack2k.anvilous.compat.jei.AnvilousJeiPlugin.show(isItem, what)) {
                Anvilous.LOGGER.info("show_recipes: JEI had nothing for {}", what);
            }
        } catch (Throwable missing) {
            Anvilous.LOGGER.info("show_recipes: no recipe browser installed ({})", missing.toString());
        }
    }
}
