package nl.ljack2k.anvilous.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.anvilous.Anvilous;

import java.util.List;

/** Combining two of the same worn item into one. */
public class CombineCategory implements IRecipeCategory<CombineDisplay> {

    static final RecipeType<CombineDisplay> TYPE =
            RecipeType.create(Anvilous.MOD_ID, "smithing_combine", CombineDisplay.class);

    private final IDrawable icon;
    private final IDrawable arrow;

    CombineCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(Items.SMITHING_TABLE));
        this.arrow = SmithingLayout.arrow(gui);
    }

    @Override
    public RecipeType<CombineDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilous.category.combine");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return SmithingLayout.WIDTH;
    }

    @Override
    public int getHeight() {
        return SmithingLayout.HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(CombineDisplay display) {
        return display.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CombineDisplay display, IFocusGroup focuses) {
        display.recipe().template().ifPresent(template ->
                builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.TEMPLATE_X, SmithingLayout.SLOT_Y)
                        .setStandardSlotBackground()
                        .addIngredients(template));

        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.BASE_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(display.tool());

        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.ADDITION_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(display.sacrifice());

        builder.addSlot(RecipeIngredientRole.OUTPUT, SmithingLayout.RESULT_X, SmithingLayout.SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(display.result());
    }

    @Override
    public void draw(CombineDisplay display, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        this.arrow.draw(graphics, SmithingLayout.ARROW_X, SmithingLayout.ARROW_Y);
        SmithingLayout.drawLines(graphics, List.of(
                Component.translatable("gui.anvilous.combine.durability"),
                Component.translatable("gui.anvilous.combine.bonus", display.recipe().bonusPercent()),
                Component.translatable("gui.anvilous.combine.enchantments")));
    }
}
