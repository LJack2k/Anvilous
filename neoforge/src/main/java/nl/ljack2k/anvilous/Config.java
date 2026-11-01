package nl.ljack2k.anvilous;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config — it changes what a craft costs, so it belongs to the world rather than to
 * whoever is looking at it, and NeoForge syncs it to clients.
 */
public final class Config {
    private Config() {
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Whether a repair takes only the materials it actually uses.
     *
     * <p>Defaults to true. With it false, a craft consumes everything the recipe is allowed
     * to take ({@code max_per_craft}, or the stack, whichever is smaller) even when the tool
     * fills up first — so a pickaxe one point from full, with four diamonds in the slot,
     * costs four diamonds to restore one durability. That is a real way to lose materials by
     * reflex, which is why the kinder behaviour is the default and the wasteful one is opt-in
     * for packs that want the harshness.
     */
    public static final ModConfigSpec.BooleanValue EXACT_MATERIAL_COST = BUILDER
            .comment("Consume only as many materials as the repair actually needs.",
                    "false consumes everything the recipe may take, even if the tool fills up first.")
            .define("exact_material_cost", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Reading a config value before its file is loaded throws, and recipes can be queried
     * early, so fall back to the default rather than bringing the game down.
     */
    public static boolean exactMaterialCost() {
        return !SPEC.isLoaded() || EXACT_MATERIAL_COST.get();
    }
}
