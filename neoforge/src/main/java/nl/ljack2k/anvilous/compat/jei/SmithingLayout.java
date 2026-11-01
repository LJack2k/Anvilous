package nl.ljack2k.anvilous.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The shape all three categories share: the smithing table's three input slots, an arrow,
 * and the result, with room underneath for a line or two about what the craft costs.
 *
 * <p>Drawn from JEI's own slot and arrow sprites rather than a texture of our own. A texture
 * would have to be redrawn for every resource pack that restyles JEI; these follow it.
 */
final class SmithingLayout {

    static final int TEMPLATE_X = 1;
    static final int BASE_X = 25;
    static final int ADDITION_X = 49;
    static final int ARROW_X = 74;
    static final int RESULT_X = 102;
    static final int SLOT_Y = 1;
    static final int ARROW_Y = SLOT_Y + 4;

    // Wide enough for the longest cost line and tall enough for three of them. JEI clips
    // nothing, so anything that does not fit is drawn straight over the next recipe.
    static final int WIDTH = 160;
    static final int HEIGHT = 56;

    /** First line of text, clear of the slots. */
    static final int TEXT_Y = 24;
    static final int LINE_HEIGHT = 10;

    private static final int TEXT_COLOUR = 0xFF404040;

    private SmithingLayout() {
    }

    static IDrawable arrow(IGuiHelper gui) {
        return gui.getRecipeArrow();
    }

    /** Draws the cost lines under the slots, in order. */
    static void drawLines(GuiGraphics graphics, List<Component> lines) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), TEMPLATE_X, TEXT_Y + i * LINE_HEIGHT,
                    TEXT_COLOUR, false);
        }
    }
}
