package nl.ljack2k.anvilous.recipe;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * What a repair does to the item's hidden anvil-use counter
 * ({@link DataComponents#REPAIR_COST}).
 *
 * <p>The smithing table never reads that counter, so this changes nothing about the cost of
 * the repair itself — it decides what the anvil will charge for the item afterwards.
 */
public enum RepairCostMode implements StringRepresentable {
    /** Back to zero: the tool leaves the table clean for any later anvil use. */
    RESET("reset"),
    /** Left exactly as it was. */
    KEEP("keep"),
    /** Grown the way an anvil would grow it: 0, 1, 3, 7, 15, 31, ... */
    INCREASE("increase");

    public static final Codec<RepairCostMode> CODEC = StringRepresentable.fromEnum(RepairCostMode::values);

    private final String name;

    RepairCostMode(String name) {
        this.name = name;
    }

    public void apply(ItemStack stack) {
        switch (this) {
            case RESET -> stack.set(DataComponents.REPAIR_COST, 0);
            case KEEP -> {
            }
            case INCREASE -> {
                int current = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
                stack.set(DataComponents.REPAIR_COST, current * 2 + 1);
            }
        }
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
