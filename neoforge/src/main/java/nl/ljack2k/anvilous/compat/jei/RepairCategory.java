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
import nl.ljack2k.anvilous.compat.view.RepairDisplay;
import nl.ljack2k.anvilous.recipe.RepairMaterial;

import java.util.ArrayList;
import java.util.List;

/** Repairing a tool with material, at the smithing table. */
public class RepairCategory implements IRecipeCategory<RepairDisplay> {

    static final RecipeType<RepairDisplay> TYPE =
            RecipeType.create(Anvilous.MOD_ID, "smithing_repair", RepairDisplay.class);

    private static final String MATERIAL_SLOT = "material";

    private final IDrawable icon;
    private final IDrawable arrow;

    RepairCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(Items.SMITHING_TABLE));
        this.arrow = SmithingLayout.arrow(gui);
    }

    @Override
    public RecipeType<RepairDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilous.category.repair");
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
    public ResourceLocation getRegistryName(RepairDisplay display) {
        return display.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RepairDisplay display, IFocusGroup focuses) {
        display.recipe().template().ifPresent(template ->
                builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.TEMPLATE_X, SmithingLayout.SLOT_Y)
                        .setStandardSlotBackground()
                        .addIngredients(template));

        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.BASE_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(display.damaged());

        // Every material the recipe accepts, each carrying the number of them one craft
        // takes - the count on the slot says the price without needing a sentence for it.
        List<ItemStack> materials = new ArrayList<>();
        for (RepairMaterial material : display.recipe().materials()) {
            for (ItemStack stack : material.ingredient().getItems()) {
                ItemStack priced = stack.copy();
                priced.setCount(Math.min(99, display.perCraft(material)));
                materials.add(priced);
            }
        }
        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.ADDITION_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .setSlotName(MATERIAL_SLOT)
                .addItemStacks(materials);

        builder.addSlot(RecipeIngredientRole.OUTPUT, SmithingLayout.RESULT_X, SmithingLayout.SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(display.repaired());
    }

    @Override
    public void draw(RepairDisplay display, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        this.arrow.draw(graphics, SmithingLayout.ARROW_X, SmithingLayout.ARROW_Y);

        // The lines follow whichever material the slot is currently cycling through, so a
        // recipe that takes iron OR diamonds tells the truth about both.
        RepairMaterial material = slots.findSlotByName(MATERIAL_SLOT)
                .flatMap(slot -> slot.getDisplayedItemStack())
                .map(display::materialFor)
                .orElse(display.recipe().materials().getFirst());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.anvilous.repair.per_item", material.percent()));
        if (display.takesSeveralCrafts(material)) {
            lines.add(Component.translatable("gui.anvilous.repair.several_crafts",
                    display.perCraft(material), display.forFullRepair(material)));
        } else {
            lines.add(Component.translatable("gui.anvilous.repair.full",
                    display.forFullRepair(material)));
        }
        if (display.recipe().keepEnchantments()) {
            lines.add(Component.translatable("gui.anvilous.keeps_enchantments"));
        }
        SmithingLayout.drawLines(graphics, lines);
    }
}
