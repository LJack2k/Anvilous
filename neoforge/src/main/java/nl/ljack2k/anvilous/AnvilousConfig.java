package nl.ljack2k.anvilous;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Whether the mod offers its own recipes at all.
 *
 * <p>The three files under {@code resources/defaults_*} are content, not machinery: the
 * recipe types, the smithing-table behaviour and the viewer plugins all work with none of
 * them loaded. A pack with its own progression wants exactly that â the engine, and none of
 * our opinion about what a repair should cost. There is no way to delete a recipe from JSON,
 * so without a switch here the only way to start empty would be to override all 169 files.
 *
 * <p>Split three ways rather than one because the three are not equally opinionated. Repair
 * and combine are vanilla's own numbers read back out of the registry; enchanting prices
 * things from {@code anvil_cost} and then picks a material per band, which is a balance
 * decision we made up. A pack can keep the first two and throw out the third.
 *
 * <p>Common, not server, config: this is a property of the installation a pack ships, not of
 * a save, so it lives in {@code config/anvilous-common.toml} where a modpack can put it once.
 * Turning one off here stops the pack being offered at all; leaving it on still lets a single
 * world drop it with {@code /datapack disable}.
 */
public final class AnvilousConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()
            .comment("Which of the built-in recipe sets Anvilous offers.",
                    "Each is a datapack, so a world can also drop one with /datapack disable,",
                    "and a pack of your own overrides any single recipe by using the same path.",
                    "Turn all three off to start from nothing and ship only your own.")
            .push("built_in_recipes");

    public static final ModConfigSpec.BooleanValue REPAIR = BUILDER
            .comment("Repair recipes for every vanilla item vanilla's own anvil accepts a material for (59).")
            .define("repair", true);

    public static final ModConfigSpec.BooleanValue COMBINE = BUILDER
            .comment("Combine recipes for every damageable vanilla item (68).")
            .define("combine", true);

    public static final ModConfigSpec.BooleanValue ENCHANT = BUILDER
            .comment("Enchanting recipes for every vanilla enchantment (42), priced from anvil_cost.")
            .define("enchant", true);

    /**
     * The recipe generators and the combine simulation, as real commands.
     *
     * <p>Off by default because they are a pack-making tool, not a feature: { dump_*}
     * writes files into the game directory, and nobody playing the pack has any use for them.
     *
     * <p>On, they are the cheapest way to cover a modpack's own content. The generators read
     * { isValidRepairItem} and { anvil_cost} out of the loaded registries, so run
     * inside a pack they emit correct recipes for its tools and enchantments, not just
     * vanilla's — which is how the sets this mod ships were made in the first place.
     */
    public static final ModConfigSpec.BooleanValue TOOL_COMMANDS = BUILDER.pop()
            .comment("Register the pack-making commands under /anvilous (operator only).",
                    "dump_repair <percent>, dump_combine and dump_enchant write a ready-to-copy",
                    "datapack folder into <game dir>/anvilous-generated, covering every item and",
                    "enchantment the running pack has loaded — modded ones included.",
                    "simulate_combine <item> <damage_a> <damage_b> [enchantment] [level] runs two",
                    "items through a real smithing menu and reports what came out, which is the",
                    "one question a recipe file cannot answer.")
            .push("tools")
            .define("commands", false);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    private AnvilousConfig() {}

    /**
     * Reads a switch, treating "not loaded yet" as on.
     *
     * <p>Pack finders are built once the configs are in, so this should never fall through in
     * practice â but reading an unloaded spec throws, and failing closed would silently ship an
     * empty game to anyone whose config did not come up.
     */
    public static boolean enabled(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }

    /**
     * Whether the pack-making commands should be registered.
     *
     * <p>Not { #enabled}: that treats an unloaded spec as on, which is right for the
     * recipe sets (shipping the defaults is the safe failure) and wrong here, where it would
     * hand every player a set of commands that write files. Commands are registered at server
     * start, long after the configs are in, so an unloaded spec here means something is wrong
     * and off is the answer.
     */
    public static boolean toolCommands() {
        return SPEC.isLoaded() && TOOL_COMMANDS.get();
    }
}
