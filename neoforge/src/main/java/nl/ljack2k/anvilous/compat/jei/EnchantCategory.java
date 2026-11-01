package nl.ljack2k.anvilous.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
import net.minecraft.world.item.enchantment.Enchantment;
import nl.ljack2k.anvilous.Anvilous;
import nl.ljack2k.anvilous.compat.view.EnchantDisplay;
import nl.ljack2k.anvilous.recipe.EnchantMaterial;

import java.util.ArrayList;
import java.util.List;

/** Applying an enchantment from a book, paid for in materials instead of experience. */
public class EnchantCategory implements IRecipeCategory<EnchantDisplay> {

    static final RecipeType<EnchantDisplay> TYPE =
            RecipeType.create(Anvilous.MOD_ID, "smithing_enchant", EnchantDisplay.class);

    private static final String MATERIAL_SLOT = "material";

    private final IDrawable icon;
    private final IDrawable arrow;

    EnchantCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(Items.ENCHANTED_BOOK));
        this.arrow = SmithingLayout.arrow(gui);
    }

    @Override
    public RecipeType<EnchantDisplay> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilous.category.enchant");
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
    public ResourceLocation getRegistryName(EnchantDisplay display) {
        return display.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnchantDisplay display, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.TEMPLATE_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(display.book());

        IRecipeSlotBuilder base = builder
                .addSlot(RecipeIngredientRole.INPUT, SmithingLayout.BASE_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .addItemStacks(display.tools());

        List<ItemStack> materials = new ArrayList<>();
        for (EnchantMaterial material : display.recipe().materials()) {
            for (ItemStack stack : material.ingredient().getItems()) {
                ItemStack priced = stack.copy();
                priced.setCount(Math.min(99, display.cost(material)));
                materials.add(priced);
            }
        }
        builder.addSlot(RecipeIngredientRole.INPUT, SmithingLayout.ADDITION_X, SmithingLayout.SLOT_Y)
                .setStandardSlotBackground()
                .setSlotName(MATERIAL_SLOT)
                .addItemStacks(materials);

        IRecipeSlotBuilder result = builder
                .addSlot(RecipeIngredientRole.OUTPUT, SmithingLayout.RESULT_X, SmithingLayout.SLOT_Y)
                .setOutputSlotBackground()
                .addItemStacks(display.enchanted());

        // The two lists are the same tools in the same order, so linking them keeps the
        // result showing the item actually being enchanted as the slot cycles.
        builder.createFocusLink(base, result);
    }

    @Override
    public void draw(EnchantDisplay display, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        this.arrow.draw(graphics, SmithingLayout.ARROW_X, SmithingLayout.ARROW_Y);

        EnchantMaterial material = slots.findSlotByName(MATERIAL_SLOT)
                .flatMap(slot -> slot.getDisplayedItemStack())
                .map(display::materialFor)
                .orElse(display.recipe().materials().getFirst());

        int cost = display.cost(material);
        SmithingLayout.drawLines(graphics, List.of(
                // Stripped of its own style: vanilla renders enchantment names in grey,
                // which on JEI's grey panel is almost not there.
                Component.literal(Enchantment.getFullname(display.enchantment(), display.level()).getString()),
                cost == 1
                        ? Component.translatable("gui.anvilous.enchant.cost_one")
                        : Component.translatable("gui.anvilous.enchant.cost", cost, material.perLevel()),
                Component.translatable("gui.anvilous.enchant.no_experience")));
    }
}
