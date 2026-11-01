package nl.ljack2k.anvilous.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One category class for all three, because the only things that differ are the name, the
 * icon, and the lines — and the lines are the display's business, not the category's.
 *
 * <p>Laid out to the same numbers as the JEI and EMI plugins, so a pack swapping viewers
 * gets the same picture: three input slots, an arrow, the result, and the cost underneath.
 * The price rides on the material slot as a stack count rather than in a sentence, which is
 * the one thing that makes these readable at a glance.
 */
class SmithingReiCategory<T extends SmithingReiDisplay> implements DisplayCategory<T> {

    private static final int TEMPLATE_X = 4;
    private static final int BASE_X = 28;
    private static final int ADDITION_X = 52;
    private static final int ARROW_X = 76;
    private static final int RESULT_X = 106;
    private static final int SLOT_Y = 6;
    private static final int ARROW_Y = SLOT_Y + 4;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 62;

    private static final int TEXT_X = 5;
    private static final int TEXT_Y = 29;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOUR = 0xFF404040;

    private final CategoryIdentifier<T> identifier;
    private final Component title;
    private final EntryStack<?> icon;

    SmithingReiCategory(CategoryIdentifier<T> identifier, Component title, EntryStack<?> icon) {
        this.identifier = identifier;
        this.title = title;
        this.icon = icon;
    }

    @Override
    public CategoryIdentifier<? extends T> getCategoryIdentifier() {
        return this.identifier;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public Renderer getIcon() {
        return this.icon;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(T display) {
        return WIDTH;
    }

    @Override
    public List<Widget> setupDisplay(T display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        // Left out rather than drawn empty when a recipe wants nothing there: REI draws an
        // empty slot as a filled grey square, which reads as something gone wrong.
        if (!display.template().isEmpty()) {
            widgets.add(Widgets.createSlot(new Point(bounds.x + TEMPLATE_X, bounds.y + SLOT_Y))
                    .entries(display.template()).markInput());
        }
        widgets.add(Widgets.createSlot(new Point(bounds.x + BASE_X, bounds.y + SLOT_Y))
                .entries(display.base()).markInput());
        widgets.add(Widgets.createSlot(new Point(bounds.x + ADDITION_X, bounds.y + SLOT_Y))
                .entries(display.addition()).markInput());
        widgets.add(Widgets.createArrow(new Point(bounds.x + ARROW_X, bounds.y + ARROW_Y)));
        widgets.add(Widgets.createSlot(new Point(bounds.x + RESULT_X, bounds.y + SLOT_Y))
                .entries(display.result()).markOutput());

        List<Component> lines = display.lines();
        for (int i = 0; i < lines.size(); i++) {
            widgets.add(Widgets.createLabel(
                            new Point(bounds.x + TEXT_X, bounds.y + TEXT_Y + i * LINE_HEIGHT),
                            lines.get(i))
                    .leftAligned()
                    .noShadow()
                    .color(TEXT_COLOUR));
        }

        return widgets;
    }
}
