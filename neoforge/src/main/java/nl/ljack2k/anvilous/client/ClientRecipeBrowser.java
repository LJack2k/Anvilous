package nl.ljack2k.anvilous.client;

import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.anvilous.Anvilous;

/**
 * Opens the recipe browser on the dev client, for the harness.
 *
 * <p>Reaches into the viewer plugins through a {@code Throwable} guard rather than a hard
 * call: these are the only places in the mod that touch classes which exist solely when a
 * viewer is installed, and a dev tool is not worth a crash on a client without one.
 *
 * <p>Tries JEI first and EMI second, so one harness command covers whichever viewer the run
 * was started with — and with both installed, the screenshot is of JEI, which is the one
 * drawing the browser anyway.
 */
public final class ClientRecipeBrowser {

    private ClientRecipeBrowser() {
    }

    public static void show(boolean isItem, ResourceLocation what) {
        if (tryJei(isItem, what) || tryEmi(isItem, what)) {
            return;
        }
        Anvilous.LOGGER.info("show_recipes: no recipe browser had anything for {}", what);
    }

    private static boolean tryJei(boolean isItem, ResourceLocation what) {
        try {
            return nl.ljack2k.anvilous.compat.jei.AnvilousJeiPlugin.show(isItem, what);
        } catch (Throwable missing) {
            Anvilous.LOGGER.info("show_recipes: no JEI ({})", missing.toString());
            return false;
        }
    }

    private static boolean tryEmi(boolean isItem, ResourceLocation what) {
        try {
            return nl.ljack2k.anvilous.compat.emi.AnvilousEmiPlugin.show(isItem, what);
        } catch (Throwable missing) {
            Anvilous.LOGGER.info("show_recipes: no EMI ({})", missing.toString());
            return false;
        }
    }
}
